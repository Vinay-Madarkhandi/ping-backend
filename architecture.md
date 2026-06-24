# Architecture & Design Decisions — Ping Uptime Monitor

This document explains **every significant architectural decision**, the **trade-off** behind it, and
the **logic** of each subsystem, top to bottom. It is the "why" companion to the code; operational and
formula details live in `docs/` (`deployment-architecture.md`, `runbook.md`, `load-testing-plan.md`,
`uptime.md`, `known-limitations.md`).

---

## 0. Guiding principles

- **Correctness under concurrency and failure over raw speed.** The system is multi-instance from day
  one; every hot path is designed to be safe when N instances run it simultaneously and when nodes
  crash mid-operation.
- **SOLID + small classes + Lombok.** One concern per class, interfaces for swappable collaborators
  (DIP), per-concern config (ISP). No god-classes, no hand-written boilerplate.
- **The database is the coordination substrate** (work queue, lock, and clock) until scale forces it
  out. This keeps the system simple and operationally boring at the cost of an eventual ceiling
  (see §18).
- **Be honest about what isn't measured.** Uptime never claims time we didn't observe; infra failures
  never masquerade as customer outages.

---

## 1. Technology choices

| Choice | Why | Trade-off / alternative |
|---|---|---|
| Spring Boot 4 / Java 21 | Mature DI, transactions, scheduling, security, actuator; virtual-thread-ready runtime. | Heavier than a micro-framework; startup cost. |
| PostgreSQL + JPA/Hibernate | Strong transactions, `SELECT … FOR UPDATE SKIP LOCKED` (the backbone of the scheduler), rich SQL for time math. | ORM hides SQL cost; we drop to native/JPQL where it matters. |
| Flyway, `ddl-auto=validate` | Explicit, reviewable, ordered schema evolution; Hibernate never mutates schema. | Every change is a migration (more ceremony) — intentional. |
| Apache HttpClient 5 (pooled) | Blocking pooled client that fits a thread-pool scheduler; per-request timeout/redirect/headers and a custom DNS resolver for SSRF. | Manual pool tuning vs a reactive client (rejected — see §7). |
| Micrometer + Prometheus | Standard metrics with dimensional tags. | Pull model; needs a scrape target. |
| Lombok | Removes accessor/constructor boilerplate; `@RequiredArgsConstructor` for constructor injection. | Annotation-processing magic; mitigated with a project `lombok.config`. |

---

## 2. Layering & package conventions

```
controller        REST endpoints (thin; auth + delegation)
service/<feature> business logic, one package per concern:
  check        HTTP probe (HealthCheckService + CheckSpec/CheckResult)
  execution    per-check orchestration + its transactions
  alert        the state machine (AlertEngine)
  incident     incident lifecycle
  notification email building + durable outbox
  uptime       duration-based uptime (pure calculator + service)
  retention    rollup + purge
  security     SSRF (guard, IP policy, validating resolver)
  metrics      Micrometer facade
  time         DatabaseClock
  lock         distributed leader lock
scheduler         polling, claim, dispatch, background workers/jobs
repository        Spring Data JPA
modles            JPA entities + enums + converters (existing spelling kept)
config            beans + per-concern @ConfigurationProperties
```

**Decisions & trade-offs**
- **Interface + impl for swappable collaborators** (`HealthCheckService`/`HttpHealthCheckService`,
  `AlertEngine`/`DefaultAlertEngine`, `IncidentService`, `NotificationService`, `MetricsService`,
  `UrlSafetyValidator`/`SsrfGuard`, `DatabaseClock`). *Why:* DIP — they're mockable in unit tests and
  replaceable (e.g. swap the claim impl for Redis later). *Trade-off:* more files/indirection; not
  applied to plain application services (`MonitorService`, `AuthService`) where there's no second impl.
- **Package-by-feature inside `service/`** instead of one flat package. *Why:* cohesion makes each
  subsystem's surface obvious. *Trade-off:* more packages.
- **`lombok.config` copies `@Qualifier` onto generated constructors.** *Why:* lets
  `@RequiredArgsConstructor` disambiguate the two executor beans without hand-writing a constructor.
  *Trade-off:* a non-obvious config file devs must know about.

---

## 3. Domain model & persistence

### 3.1 Entities
`Monitor`, `MonitorStatus` (1:1 via `@MapsId`), `MonitorLogs`, `Incident`, `EmailOutbox`,
`MonitorPauseWindow`, `MonitorDailyStats`, `User`; enums `MonitorMethod`, `MonitorState`,
`ProbeOutcome`, `IncidentStatus`, `EmailStatus`.

### 3.2 Time: `Instant` for scheduling, `LocalDateTime` for audit
- Scheduling/lease/incident/uptime times are **`Instant`** stored as plain `TIMESTAMP` via
  `@JdbcTypeCode(SqlTypes.TIMESTAMP)` + `hibernate.jdbc.time_zone=UTC`.
  *Why:* timezone-independent comparisons across instances; plain TIMESTAMP keeps Hibernate `validate`
  happy against the existing columns (no `timestamptz` migration).
  *Trade-off:* `@JdbcTypeCode` is explicit/verbose; mixing `Instant` and `LocalDateTime` is a
  maintenance footgun (audit fields like `createdAt`, `MonitorStatus` timestamps stay `LocalDateTime`)
  — documented, and it surfaces as the timestamp-serialization gotcha for the frontend.

### 3.3 Custom-header storage: `AttributeConverter` (JSON)
`Monitor.customHeaders` is a `Map<String,String>` persisted as a JSON `TEXT` column via
`JsonStringMapConverter`. *Why:* keeps the model strongly typed while the schema stays a simple column.
*Trade-off:* not queryable as structured data (fine — we never query by header); silent
serialize/deserialize fallback to empty on malformed JSON.

### 3.4 Soft delete
`Monitor.deletedAt` marks archival; the claim and listing filter `deletedAt IS NULL`; "delete" resolves
any open incident and keeps history. *Why:* incident/outage history is the product's most valuable data;
`ON DELETE CASCADE` would destroy it. *Trade-off:* every query must remember the filter; a true
hard-purge (GDPR) is a separate path.

### 3.5 Optimistic version
`Monitor.@Version` guards against lost updates if two runners ever touch the same monitor. *Why:* a
last-resort safety net behind SKIP LOCKED + lease. *Trade-off:* a losing transaction throws and rolls
back (handled), but its outbound probe already happened.

### 3.6 Flyway + H2/Postgres duality
Migrations V1–V12 are written to run on **both** Postgres (prod) and H2 (unit tests): one `ALTER … ADD
COLUMN` per statement, no partial indexes (the "at most one open" invariant uses a **nullable unique
column** instead — see §12), and we avoid reserved words (`day` → `stat_day`). Postgres-only behaviour
(SKIP LOCKED, `current_timestamp` arithmetic) is never exercised by H2 because the scheduler is disabled
in unit tests and validated by Testcontainers (§17). *Trade-off:* most tests run on H2, so a class of
Postgres-specific bugs can only be caught by the (single) Testcontainers test — a known testing gap.

---

## 4. Configuration: per-concern `@ConfigurationProperties`

Each concern is its own bound class — `SchedulerProperties`, `HttpClientProperties`, `AlertProperties`,
`EmailProperties`, `RetentionProperties`, `SsrfProperties`, `UptimeProperties`, plus a reusable
`WorkerPoolProperties` bound twice (`monitor.executor.fast` / `.slow`).

**Decision & trade-off:** an earlier single class with six nested static groups was rejected as a
god-class (and it forced every collaborator to depend on the whole blob). Splitting per concern honors
**ISP** — `SsrfGuard` depends only on `SsrfProperties`. *Trade-off:* more small classes and a longer
`@EnableConfigurationProperties` list.

Secrets (`DB_*`, `MAIL_*`, `JWT_SECRET`) are env placeholders, not committed values.

---

## 5. The scheduler & claim mechanism (the heart)

### 5.1 Polling vs push
A `@Scheduled` poller claims due monitors from the DB. *Why:* simplest correct model, naturally
multi-instance via SKIP LOCKED, no extra infra. *Trade-off:* the DB is the work queue → it becomes the
bottleneck at scale (§18); push/queue is the migration path.

### 5.2 Claiming: `FOR UPDATE SKIP LOCKED`
`MonitorRepository.claimDueMonitors` uses `@Lock(PESSIMISTIC_WRITE)` + the Hibernate lock-timeout hint
`-2` (= `SKIP LOCKED`), filtering `active && !paused && deletedAt IS NULL && nextCheckAt <=
current_timestamp` ordered by `nextCheckAt`. *Why:* multiple pollers/instances claim **disjoint**
batches with no double-execution and no waiting (skipped rows aren't blocked on). Ordering by due-time
gives **global FIFO fairness**. *Trade-off:* Postgres-only; the `next_check_at` index is a contention
hot spot at high claim rates.

### 5.3 Leasing: overload `next_check_at` (not a `locked_until` column)
After claiming, `MonitorClaimService` pushes `nextCheckAt` forward by `max(interval, timeout) +
leaseMargin`. While a check runs, the monitor isn't due, so it won't be re-claimed; the runner
overwrites `nextCheckAt` with the true `now + interval` on completion; if the runner crashes, the lease
just expires and the monitor retries.
- *Why this lease length:* it must exceed the longest possible execution (a check can take up to its
  timeout), or a slow check could be re-claimed and double-executed.
- **Decision: reuse `next_check_at` rather than add `locked_until`.** *Why:* one column, one index, one
  predicate; both designs auto-expire on crash, so `locked_until` adds no recovery benefit. *Trade-off:*
  `next_check_at` transiently means "leased until," not "next due," so anything reading it mid-flight
  sees a future time; and there's no queryable "currently executing" signal. `locked_until` is the
  upgrade when we need in-flight observability or lease renewal.

### 5.4 Clock: `DatabaseClock`
Dueness uses JPQL `current_timestamp`; lease/reschedule/incident timestamps use `DatabaseClock.now()`
(`select current_timestamp`). *Why:* a single shared clock eliminates cross-node wall-clock skew that
would otherwise make a fast-clocked instance claim early and mis-time leases. *Trade-off:* one extra DB
round-trip per check (pure overhead at scale; the fix is inlining `now()` into the lease SQL). The
resolver defensively normalizes the vendor return type (Postgres `OffsetDateTime` / H2 `Timestamp`) to
`Instant`.

### 5.5 Capacity-aware claiming (adaptive backpressure)
The poller claims `min(batchSize, freeSlots(fast) + freeSlots(slow))` and skips claiming entirely when
pools are full. *Why:* under sustained overload, claiming work we can't run wastes lease `UPDATE`s and
stretches monitors' effective intervals; sizing the claim to free capacity avoids that. *Trade-off:*
`freeSlots` is a heuristic (queue remaining + idle threads); estimation races are caught by AbortPolicy.

### 5.6 Bulkhead pools (fast vs slow)
Two executors (`CheckExecutorConfig`): monitors with effective `timeout >= slowThresholdMs` run in the
**slow** pool, others in the **fast** pool. *Why:* a flood of slow/hanging monitors can't starve the
latency-sensitive majority. *Trade-off:* two pools to size; a monitor is classified by configured
timeout (a proxy for max thread occupancy), not actual latency.

### 5.7 Rejection policy: `AbortPolicy` (not `CallerRuns`)
A saturated pool throws `RejectedExecutionException`; `MonitorScheduler` catches it, increments
`monitor.checks.rejected{pool}`, and drops the check (re-claimed after its lease). *Why:* `CallerRuns`
would run the check **on the poller thread**, blocking claiming for up to a full timeout — the worst
behaviour for a scheduler. *Trade-off:* dropped checks wait a lease window before retry, silently
stretching effective intervals under overload (visible only via the metric).

### 5.8 Dedicated scheduling pool
`spring.task.scheduling.pool.size > 1` so the poller, email-outbox worker, and retention job don't
starve each other on Spring's default single scheduling thread. *Trade-off:* a few idle threads.

### 5.9 Per-tick resilience
`poll()` wraps its body in try/catch so a DB failover can't kill the `@Scheduled` series; the next tick
retries and auto-resumes when the DB returns. *Trade-off:* no backoff (a tight failure loop logs each
poll interval).

---

## 6. HTTP client

### 6.1 Pooled HC5, one shared client
`HttpClientConfig` builds one `CloseableHttpClient` on a `PoolingHttpClientConnectionManager`
(`maxTotal`, `maxPerRoute`, connect/socket timeouts). *Why:* connection reuse; bounded concurrency.
*Trade-off:* `maxPerRoute` (per scheme+host+port) can throttle many monitors on one domain — a known
fairness limit.

### 6.2 Per-request config via `HttpClientContext`
Per-monitor timeout and redirect policy are set per request through `HttpClientContext` + `RequestConfig`
on the shared client. *Why:* one pooled client serves every monitor while still honoring per-monitor
settings — the idiomatic HC5 way. **Decision: use HC5 directly, not `RestClient`.** *Why:* per-monitor
redirect policy, per-request timeout, and reading the body for keyword matching are cleaner on the raw
client; it's still a fully pooled blocking client (the requirement allowed "WebClient **or** pooled
client"). *Trade-off:* deviates from the originally-approved "RestClient wrapper" — noted at the time.

### 6.3 Reactive (WebClient) was rejected
*Why:* the scheduler is blocking (JPA, thread pools); a reactive client would be `.block()`ed on worker
threads anyway and drag the whole reactive stack alongside Web MVC. *Trade-off:* we forgo non-blocking
I/O efficiency — acceptable given bounded worker pools and the bulkhead.

---

## 7. Health check & probe outcomes

### 7.1 `CheckSpec` / `CheckResult` value objects
`HealthCheckService.check(CheckSpec)` takes an immutable snapshot, not the JPA entity. *Why:* decouples
the probe from persistence (DIP, testable) and lets the probe run with no entity attached. *Trade-off:* a
mapping step (`CheckSpec.from(monitor)`) inside the read transaction.

### 7.2 Success evaluation
Up iff status matches (`expectedStatusCode` if set, else 2xx/3xx) **and** (no keyword, or body contains
keyword). The body is only read when a keyword is configured (otherwise the entity is consumed to free
the connection). *Trade-off:* keyword checks read the full body into memory.

### 7.3 Three outcomes: `UP` / `DOWN` / `INCONCLUSIVE`
**Decision:** classify our-infrastructure failures separately from target failures.
- `INCONCLUSIVE` ← `ConnectionRequestTimeoutException` (pool exhausted), `SsrfBlockedHostException`,
  `InterruptedException` — walked through the exception cause chain.
- `DOWN` ← everything else (bad status, keyword miss, connect/read timeout, refused, TLS, DNS).
*Why:* a saturated connection pool is **our** fault and must never open an incident or page a customer.
*Trade-off:* `INCONCLUSIVE` checks still write a log row (so they aren't treated as a data gap), which
slightly inflates uptime as "monitored-up"; and a genuinely misconfigured monitor that always blocks
SSRF stays inconclusive forever (never alerts) — a deliberate "don't page on our refusal" choice.

---

## 8. Execution pipeline & transaction boundaries

### 8.1 Probe runs **outside** any transaction
`MonitorCheckRunner` (no `@Transactional`): `loadSpec` (short read tx) → **probe (no tx)** →
`recordResult` (short write tx). *Why:* the original design held a JDBC connection for the whole HTTP
round-trip (up to the timeout) — under load or DB stress this exhausts the connection pool. Splitting it
means the network call holds no DB connection. *Trade-off:* the monitor is read twice (spec, then managed
entity in the write tx) — a small extra query for a large correctness win.

### 8.2 Lock ordering & linearizability
`recordResult` locks the `monitor_status` row **`FOR UPDATE` first** (before the log insert's FK touch),
then writes the log, runs the alert engine, reschedules, saves. The **global lock hierarchy** is
`monitor_status → monitor → incident → email_outbox`; claim only takes `monitor` via SKIP LOCKED (never
waits); the outbox worker locks only `email_outbox` (rows are self-contained). *Why:* a single,
acyclic ordering makes deadlocks impossible by construction, and the `monitor_status` write lock is the
**linearization point** for the state machine — concurrent results for one monitor serialize there, so
transitions apply in a total order. *Trade-off:* `monitor_status` is a hot per-monitor row written on
every check (contributes to write amplification at scale).

---

## 9. Alert engine — the state machine

### 9.1 States & transitions
`UNKNOWN/UP/SUSPECT/DOWN` (`DefaultAlertEngine`). A failure increments `consecutiveFailures` →
`SUSPECT`; at `failureThreshold` (default 3) → `DOWN` (open incident, enqueue alert). A success resets
failures; while `DOWN`, after `recoveryThreshold` (default 1) consecutive successes → `UP` (resolve
incident, enqueue recovery). *Why:* the threshold debounces transient blips (a single 5xx never pages);
`SUSPECT` makes the sub-threshold window first-class and dashboard-visible; the recovery threshold damps
flapping. *Trade-off:* an outage isn't reported until `threshold × interval` has elapsed (alerting
latency vs noise — the classic monitoring trade-off).

### 9.2 Alerts fire only on transitions
DOWN/RECOVERY alerts are emitted on state **edges**, never per failed check. *Why:* no alert storms.
Combined with the `monitor_status` lock (§8.2), a second concurrent failure that arrives when already
`DOWN` is a no-op — no duplicate incident or alert.

### 9.3 Cooldown
DOWN alerts are suppressed within `cooldown` (default 15m) via `lastAlertSentAt`; **RECOVERY always
sends**. *Why:* prevent re-paging on a flapping monitor while never withholding good news. *Trade-off:* a
monitor that flaps faster than the cooldown can still emit paired DOWN/RECOVERY notices.

### 9.4 PAUSED & INCONCLUSIVE are not health states
Paused monitors are skipped by the caller (log recorded, FSM untouched) and shown as a **derived**
`displayState=PAUSED` — `paused` stays a `Monitor` flag, not an FSM state, to avoid two sources of truth.
`INCONCLUSIVE` results return early from the engine (no counter/state/incident/alert). *Why:* operational
and infrastructure conditions must not corrupt the health machine. *Trade-off:* the API exposes both
`currentState` (health) and `displayState` (what to show) — consumers must use the right one.

---

## 10. Incidents

### 10.1 One open incident per monitor — via a nullable unique column
`Incident.openForMonitor` = `monitor_id` while OPEN, `NULL` when resolved, with a plain `UNIQUE`
constraint. *Why:* a partial unique index (`WHERE status='OPEN'`) would be Postgres-only and break H2
tests; a nullable unique column is portable (multiple NULLs allowed) and enforces "at most one open" in
the DB even if logic erred. *Trade-off:* a slightly non-obvious column whose meaning is "open-marker."

### 10.2 Atomicity & duration
`open`/`resolve` run **inside the alert engine's locked transaction**, so the incident and the state
transition commit atomically. `durationSeconds` is computed at resolve from DB-clock timestamps. *Why:*
no orphan incidents, accurate durations on one clock. *Trade-off:* incidents are never purged (retained
for history) → unbounded growth at scale (partitioning is future work).

---

## 11. Notifications & the durable email outbox

### 11.1 Outbox pattern
`NotificationService.enqueue` writes a self-contained `EmailOutbox` row (recipient/subject/body
denormalized); a scheduled `EmailOutboxWorker` drains it. *Why:* decouples alerting from SMTP latency/
failures, survives restarts, and (because rows are self-contained) the worker never joins
monitor/incident → it can't participate in a lock cycle. *Trade-off:* an extra table and a worker.

### 11.2 Enqueue idempotency
`dedupe_key = <incidentId>:<type>` with a `UNIQUE` constraint; a duplicate enqueue (concurrent
transition / replay) collides and is a no-op. *Why:* exactly-once **enqueue** per logical alert.

### 11.3 Send: lease + SKIP LOCKED + outside-tx
The worker claims due `PENDING` rows with SKIP LOCKED, **leases each forward** (`nextAttemptAt`) and
commits, then sends **outside** the transaction, then marks `SENT` / reschedules with exponential
backoff / `FAILED` (dead-letter) after `maxAttempts`. *Why:* no two workers send the same row; a crash
mid-send just retries after the lease; no DB connection is held during SMTP. *Trade-off:* delivery is
**at-least-once** — a crash between SMTP success and `markSent` resends (no provider idempotency token
over SMTP). And a permanently-failing row dead-letters **silently** (no operator alert yet).

---

## 12. Uptime — duration-based

### 12.1 Duration, not check-ratio
Uptime = % of **monitored time** the monitor was up: `monitored = window − paused − gap`,
`uptime% = (monitored − down) / monitored × 100`. *Why:* a check-success ratio is biased by check
frequency and ignores how long an outage lasted; duration is what an SLA actually means. *Trade-off:*
needs incident, pause, and coverage data, not just a counter.

### 12.2 Pure interval algebra + precedence
`UptimeCalculator` (no DB, fully unit-tested) uses `Intervals` (clip/merge/subtract). Window time is
partitioned by **precedence Paused > Gap > Down > Up**, so overlaps never double-count. *Why:* a pure,
deterministic core is trivially testable across all edge cases; explicit precedence resolves ambiguous
overlaps (e.g. an outage during a scheduler gap counts as gap/unknown, not down). *Trade-off:* the
precedence choice (gap overrides an open incident) is a judgment call — documented in `docs/uptime.md`.

### 12.3 The five edge cases
DOWN from incidents (open → window end); paused excluded; archived caps the window end at `deletedAt`;
window start clamped to `createdAt`; coverage gaps (`> gapMultiplier × interval`, plus leading/trailing
edges) excluded; `null` uptime when no monitored time. (Full table in `docs/uptime.md`.)

### 12.4 Beyond raw-log retention: rollups + recent logs
Incidents and pause windows are never purged, so DOWN/paused cover any window. Only **gap detection**
needs raw logs (kept ~30d), so the window is split at the retention cutoff: recent part uses per-check
timestamps; older part uses `monitor_daily_stats` (a day with no rollup row = a full-day gap). *Why:*
correct uptime for 90d windows without keeping 90d of raw logs. *Trade-off:* older-day gap detection is
day-granular (intra-day gaps in purged history aren't reconstructed); gap detection scans in-window
check timestamps (O(checks) — slow for huge live windows).

---

## 13. Retention & rollups

`RetentionService` (daily) rolls the **previous day** into `monitor_daily_stats` (counts, avg latency,
downtime from incidents) then **purges** `monitor_logs` older than `retention.days`. Rollup is
idempotent (`existsByMonitorIdAndDay`). *Why:* bound hot-store size while preserving aggregate history.
*Trade-off:* raw logs are **hard-deleted** at the horizon (cold-storage archival is the better product
choice — deferred); only `monitor_logs` is purged (other tables grow).

### 13.1 Singleton across instances: a DB leader lock
`RetentionJob` runs only if it wins the `retention` row in `scheduler_lock` (a DB-clock conditional
`UPDATE` with an auto-expiring TTL). *Why:* with N instances, N retention jobs would race on the
`(monitor_id, stat_day)` unique constraint and duplicate the purge. **Decision: a hand-rolled DB lock,
not ShedLock.** *Why:* Spring Boot 4 is bleeding-edge and ShedLock compatibility is uncertain; a tiny
conditional-UPDATE lock is dependency-free, portable, DB-clock-based, and crash-safe (TTL expiry).
*Trade-off:* reinvents a small slice of ShedLock; no lock-extension/heartbeat (TTL is sized to exceed
the job).

---

## 14. SSRF protection (two layers)

### 14.1 Create-time guard
`SsrfGuard` validates the URL on monitor creation: scheme must be http(s); host must not be a blocked
name; resolved IPs must not be loopback/private/link-local/multicast/IPv6-ULA/metadata
(`IpAddressPolicy`). *Why:* fail fast on obviously unsafe configs.

### 14.2 Connection-time validating resolver (closes DNS rebinding)
`SsrfValidatingDnsResolver` (wired into the HC5 connection manager) performs the **single** resolution
the client connects to and rejects disallowed IPs. *Why:* validating then letting the client re-resolve
is a TOCTOU window — an attacker can answer "safe" to the validator and "private" to the socket. One
resolution = the validated IP is the connected IP. *Trade-off:* a blocked resolution surfaces as
`INCONCLUSIVE` (we refused), so a rebinding/misconfigured target never alerts — intentional. The
create-time guard's separate resolution remains a fail-fast convenience, not the authority.

`IpAddressPolicy` is shared by both layers (DRY, single rule set).

---

## 15. Observability

Micrometer + `/actuator/prometheus`. Metrics: `monitor.check.latency{result}` (timer),
`monitor.checks.total{result}` (up/down/inconclusive), `monitor.scheduler.lag.ms` (summary),
`monitor.checks.rejected{pool}`, `monitor.alerts{type}`, `monitor.email.outbox{outcome}`. *Why:* the
lag/rejected/inconclusive trio makes the scheduler's health observable (is it keeping up? shedding load?
is it our infra or the targets?). *Trade-off:* pull-based; actuator endpoints are `permitAll` and must
be network-restricted at ingress.

---

## 16. Security / auth

Stateless JWT in an **HttpOnly `JwtToken` cookie**. `JwtAuthenticationFilters` reads the cookie,
validates, and sets the security context; `/auth/validate` returns 200/401 for the frontend's session
check. *Why:* cookie keeps the token out of JS (XSS-resistant); stateless scales horizontally with no
session store. *Trade-off:* no server-side logout/refresh (logout is client cookie clear; token valid
until expiry); cookie is `Secure=false`/no `SameSite` today (must harden for prod).

---

## 17. Testing strategy

- **Pure unit tests** for logic with no I/O: `UptimeCalculator` (all five edge cases), `DefaultAlertEngine`
  (threshold/recovery/no-duplicate/inconclusive), mappers, JWT.
- **H2 (`@SpringBootTest`)** for context/schema validation; the scheduler is disabled
  (`monitor.scheduler.enabled=false`) and SSRF allows private hosts in tests.
- **Testcontainers Postgres** for the Postgres-only path (real `FOR UPDATE SKIP LOCKED` claim + lease and
  the full Flyway set).
*Why:* fast feedback for pure logic; real-DB confidence where it matters. *Trade-off:* most DB-touching
tests run on H2, so vendor-specific bugs rely on the (currently single) Testcontainers test — more of
the suite should move to Postgres in CI.

---

## 18. Scale ceiling & migration path (summary)

Bottleneck = Postgres write IOPS (two hot-row writes + log insert + clock round-trip per check) and
`next_check_at` index contention; order-of-magnitude **tens of thousands of monitors** on a single
primary. Path: batch log inserts + logs to a TSDB/partitioned table → shard the claim by
`hash(monitor_id)` → move scheduling to a **Redis ZSET due-queue** (swap `MonitorClaimService` only) →
Kafka + in-memory timer wheels for hundreds of thousands+. Full detail in
`docs/deployment-architecture.md` and `docs/known-limitations.md`.

---

## 19. Consolidated invariants (don't break these)

1. Lease ≥ `max(interval, timeout) + margin`; never lease by interval alone.
2. The HTTP probe runs outside any transaction.
3. `recordResult` locks `monitor_status` **first**; global order `monitor_status → monitor → incident →
   email_outbox`; claim and outbox use SKIP LOCKED and never wait.
4. Alerts fire only on state transitions; `INCONCLUSIVE` and `paused` never drive the FSM.
5. At most one OPEN incident per monitor (nullable-unique `open_for_monitor`); one open pause window
   per monitor likewise.
6. Email enqueue is idempotent (`dedupe_key` unique); delivery is at-least-once.
7. All scheduling/lease/incident/uptime time is the **DB clock** (skew-free); audit times are UTC
   `LocalDateTime`.
8. Uptime never counts unmonitored time (paused/gap excluded); `null` means "no data," not 100%.
9. Cluster-singleton jobs (retention) run under the leader lock.
10. Soft delete preserves history; the claim filters `deletedAt IS NULL`.
```
