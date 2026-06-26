# Operational Runbook

## Deploy

1. Provide env: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `JWT_SECRET`.
2. Start one instance first — Flyway applies pending migrations (V1–V12). Confirm
   `/actuator/health` is UP, then scale out. (Migrations must be backward-compatible across a
   rolling deploy; expand-then-contract for breaking changes.)
3. Passive / API-only nodes: set `monitor.scheduler.enabled=false`.

## Health & dashboards

- Liveness/readiness: `GET /actuator/health` (groups at `/actuator/health/{liveness,readiness}`).
- Metrics: `GET /actuator/prometheus`.
- Dashboards/alerts: run `docker compose --profile observability up` and open Grafana at
  http://localhost:3000 — the provisioned **"Ping — Overview"** dashboard covers scheduler lag,
  `monitor_checks_total{result}` (incl. `inconclusive`), `monitor_checks_rejected{pool}`, check
  latency p95/p99, `monitor_alerts`, `monitor_email_outbox{outcome}`, Hikari pool and JVM/GC, plus a
  live log panel. Prometheus alert rules are in `docker/prometheus/alerts.yml`. Full guide:
  [observability.md](observability.md).

## Alerts → response

**Scheduler lag rising (`monitor_scheduler_lag_ms` p95 > poll interval)**
- Check `monitor_checks_rejected` (pool saturated) and Hikari pending. → scale instances, raise
  `monitor.executor.*` pool sizes / `monitor.http.max-total`, or lower fan-out. If DB-bound, see scale path.

**`monitor_checks_rejected{pool}` sustained > 0**
- That pool is under-capacity. Raise `monitor.executor.<pool>.max-pool-size`/`queue-capacity` or add
  instances. Slow-pool rejections specifically → many high-timeout monitors; consider per-class limits.

**`inconclusive` rate climbing**
- Our infra, not targets: HC5 pool exhaustion (raise `max-total`/`max-per-route`), DNS issues, or SSRF
  blocks (a monitor now resolves to a private IP — investigate config / possible rebinding). These do
  NOT page customers by design; investigate proactively.

**`monitor_email_outbox{outcome="failed"}` > 0 (dead-letters)**
- Alert emails are not being delivered. Check SMTP creds/connectivity. Inspect `email_outbox` rows
  with `status='FAILED'` (`last_error`). Fix SMTP, then requeue (set `status='PENDING'`,
  `attempts=0`, `next_attempt_at=now()`). The `EmailDeadLettering` Prometheus alert fires when this
  happens (`docker/prometheus/alerts.yml`); wire an Alertmanager receiver so it actually pages.

**`email_outbox` PENDING backlog growing**
- Worker not draining: confirm `monitor.scheduler.enabled=true` on at least one node; check the
  scheduling pool isn't starved; check SMTP latency.

**DB failover / outage**
- Polling and writes pause; the scheduler logs errors and auto-resumes when the DB returns. No manual
  action required for correctness. Confirm recovery via lag returning to baseline.

**Retention didn't run / ran on multiple nodes**
- It's leader-locked (`scheduler_lock` row `name='retention'`). To inspect:
  `SELECT * FROM scheduler_lock`. A stuck lock auto-expires after its TTL (30m). To force-release:
  `UPDATE scheduler_lock SET locked_until = now() WHERE name='retention'`.

**Uptime endpoint slow for huge windows**
- Gap detection scans raw logs in-window. Prefer windows within retention; very large windows lean on
  daily rollups for the purged portion (already wired). If still slow, this is the known read-scaling
  limitation — consider TSDB.

## Routine operations

- **Pause/resume a monitor**: `POST /api/v1/monitors/{id}/pause` / `/resume` (excludes paused time
  from uptime).
- **Archive a monitor**: `DELETE /api/v1/monitors/{id}` (soft delete; history retained; open incident
  resolved).
- **Rotate secrets**: update env + restart; rotate the leaked Gmail app password immediately.
- **Verify migrations**: `flyway_schema_history` table; never edit applied migrations — add new ones.

## Scaling actions

- Add instances (linear until the DB ceiling).
- Tune `poll-rate-ms` / `batch-size` for cadence vs DB load.
- Past the single-primary ceiling, follow the scale-out path in `deployment-architecture.md`.

## Rollback

- App: redeploy the previous image (instances are stateless).
- DB: migrations are forward-only — never auto-rollback a Flyway migration; write a compensating
  forward migration. Keep migrations additive to allow rolling back app code without schema rollback.
