# Known Limitations

Status of the issues raised in design review. **Fixed** items shipped; **Open** items are accepted
trade-offs or deferred work.

## Fixed (production-critical)

- **Infra failures no longer page customers.** Probes now return `UP` / `DOWN` / `INCONCLUSIVE`.
  HC5 pool exhaustion, thread interruption, and SSRF-blocked resolution are `INCONCLUSIVE` and never
  advance the failure counter, open an incident, or send an alert. Recorded on the log row + the
  `monitor_checks_total{result="inconclusive"}` metric.
- **Retention is a cluster singleton.** A DB leader lock (`scheduler_lock`, DB-clock conditional
  UPDATE, auto-expiring TTL) ensures only one instance runs rollup+purge per schedule.
- **Uptime works beyond raw-log retention.** Incidents and pause windows are never purged; gap
  detection splits at the retention cutoff and uses daily rollups for the purged portion.
- **DNS rebinding closed.** A validating DNS resolver in the HC5 connection manager performs the
  single resolution the client connects to, rejecting disallowed IPs — the validated IP is the
  connected IP (no TOCTOU re-resolution).

## Open — accepted trade-offs

- **Email delivery is at-least-once.** If SMTP succeeds but the JVM dies before `markSent`, the
  send-lease expires and the email is re-sent. Enqueue is idempotent (`dedupe_key` UNIQUE), but
  there is no provider-side idempotency token. Duplicate alert emails are possible but rare.
- **INCONCLUSIVE time counts as monitored-up in uptime.** The uptime calc excludes gaps (no log) and
  paused time, but an inconclusive check still writes a log row, so its instant isn't treated as a
  gap. Sustained inconclusive periods slightly inflate uptime. (Down is unaffected — inconclusive
  never opens an incident.)
- **Duplicate probes are bounded, not eliminated.** A GC/STW pause longer than a monitor's lease can
  let a second runner execute concurrently. The DB stays consistent (status `FOR UPDATE` serialises
  the FSM; `@Version` guards the monitor row; the loser's tx rolls back), but a second outbound HTTP
  request may be sent.
- **Overload silently stretches effective intervals.** Under sustained saturation, capacity-aware
  claiming + Abort policy shed load; dropped monitors are re-claimed only after their lease, so a
  30s monitor may effectively check less often. Visible via `monitor_checks_rejected`, but there is
  no explicit per-monitor degradation signal.

## Open — deferred operational work

- **No `email_outbox` retention/cleanup.** SENT and FAILED rows accumulate indefinitely; needs a
  purge/archive policy.
- **No dead-letter alerting.** A permanently-failing alert email lands in `FAILED` silently — an
  operator must watch `monitor_email_outbox{outcome="failed"}`. A customer's outage notice could be
  lost without notice.
- **No long-term partitioning.** Only `monitor_logs` is purged. `incident`, `email_outbox`,
  `monitor_daily_stats` grow unbounded; needs time-partitioning at scale.
- **Raw logs are hard-deleted at retention, not archived.** For audit/forensics, export to cold
  object storage (S3/Parquet) before purge rather than deleting.

## Open — scale & testing

- **Single-primary write ceiling (~tens of thousands of monitors).** Two hot-row writes per check
  (`monitor` + `monitor_status`) plus log inserts and the `DatabaseClock` round-trip cap throughput.
  Scale path: batch log inserts → logs to TSDB/partitioned table → shard the claim → Redis ZSET
  due-queue → Kafka + in-memory timer wheels.
- **Uptime gap detection scans in-window logs.** O(checks) per request; large windows are slow and
  lean on rollups. A TSDB-backed read path is the long-term fix.
- **Test/prod schema divergence.** Most tests run on H2; Postgres-only semantics are covered by one
  Testcontainers test. More of the suite should run on Postgres in CI to catch vendor-specific bugs.
- **Per-route HTTP fairness.** `max-per-route` (default 20) bounds concurrency per host; many
  monitors on one domain contend. Configurable, but no per-host overrides or dynamic sizing.
- **Actuator endpoints are unauthenticated** (`health`/`info`/`prometheus`) — must be
  network-restricted at ingress; they leak operational metrics if publicly exposed.
