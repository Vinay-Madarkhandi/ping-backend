# Deployment Architecture

## Components

| Component | Role | Notes |
|---|---|---|
| **ping app instance(s)** | Stateless JVM (Spring Boot). Runs the API, the polling scheduler, check workers, the email-outbox worker, and the retention job. | Horizontally scalable; no sticky state. |
| **PostgreSQL** | System of record: monitors, status, incidents, logs, pause windows, daily stats, email outbox, scheduler lock. Also the **work queue** (claim via `FOR UPDATE SKIP LOCKED`) and the **clock** (`DatabaseClock`). | Single primary for writes. |
| **SMTP relay** | Outbound alert email. | Credentials via `MAIL_USERNAME`/`MAIL_PASSWORD`. |
| **Prometheus** | Scrapes `/actuator/prometheus`. | Plus Grafana for dashboards/alerts. |
| **Monitored targets** | External HTTP endpoints. | Reached through the SSRF-validating pooled HC5 client. |

## Multi-instance model

All instances are equal and safe to run concurrently:

- **Check scheduling** — each instance polls and claims a disjoint batch of due monitors with
  `SELECT … FOR UPDATE SKIP LOCKED`, leasing each forward by `max(interval, timeout) + margin`.
  Throughput scales ~linearly with instance count until Postgres write IOPS / claim-index
  contention become the limit (see Known Limitations / scale path).
- **Email outbox** — workers claim rows with SKIP LOCKED; disjoint by construction.
- **Retention/rollup** — cluster-singleton via a DB **leader lock** (`scheduler_lock`, DB-clock
  conditional UPDATE, auto-expiring TTL). Only one instance runs it per schedule.
- **Time** — all scheduling/lease/incident timestamps come from the **database clock**, so instance
  wall-clock skew is irrelevant.

```
            ┌─────────── app instance (xN) ───────────┐
   HTTP ───▶│ API  Scheduler→claim(SKIP LOCKED)        │──▶ targets (HC5 pool, SSRF resolver)
            │      fast/slow worker pools              │
            │      EmailOutboxWorker (SKIP LOCKED)     │──▶ SMTP
            │      RetentionJob (leader lock)          │
            └──────────────────┬──────────────────────┘
                               ▼
                          PostgreSQL  ◀── Prometheus scrape /actuator/prometheus
```

## Configuration (env-driven)

Secrets and environment specifics are env vars (never committed):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `JWT_SECRET`.
Engine tuning lives under `monitor.*` (`scheduler`, `http`, `alert`, `email`, `retention`, `ssrf`,
`uptime`, `executor.fast`/`executor.slow`) — see `application.properties`. Set
`monitor.scheduler.enabled=false` for API-only / passive nodes.

## Schema / migrations

Flyway (`ddl-auto=validate`), V1–V12. Every schema change is a forward migration; no Hibernate
auto-DDL in any environment. Migrations are written to run on both PostgreSQL (prod) and H2 (unit
tests); Postgres-only behaviour (SKIP LOCKED) is covered by a Testcontainers integration test.

## Capacity knobs

- `monitor.scheduler.poll-rate-ms`, `monitor.scheduler.batch-size` — claim cadence/size.
- `monitor.executor.fast|slow.*` — worker bulkhead pools (slow = high-timeout monitors).
- `monitor.http.max-total`, `max-per-route` — HC5 connection pool (per-route caps fan-out per host).
- `spring.task.scheduling.pool.size` — scheduling threads (poll / email / retention isolation).

## Network & security

- `/actuator/health`, `/actuator/info`, `/actuator/prometheus` are unauthenticated — **restrict to
  the internal/monitoring network** at the ingress; everything else requires JWT auth.
- Outbound egress is constrained by the SSRF guard (create-time) + the validating DNS resolver
  (connect-time) — loopback/private/link-local/metadata destinations are refused.
- Rotate the previously-committed Gmail app password; supply real SMTP creds via env.

## Scale-out path (beyond a single primary)

Tens of thousands of monitors fit a single primary. Beyond that: batch log inserts + move logs to a
partitioned table / TSDB → shard the claim by `hash(monitor_id)` → move scheduling off the DB to a
**Redis ZSET due-queue** (swap `MonitorClaimService` only) → Kafka + in-memory timer wheels for
hundreds of thousands+.
