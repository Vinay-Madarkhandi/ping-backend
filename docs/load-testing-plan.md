# Load-Testing Plan

## Goal

Find the **knee** of the polling architecture on a given Postgres + instance count: the monitor
count / check rate at which scheduler lag grows unbounded or checks get dropped. Validate the
bulkhead, backpressure, and failure-classification behaviour under stress.

## Key metrics (from `/actuator/prometheus`)

| Metric | Meaning | Health signal |
|---|---|---|
| `monitor_scheduler_lag_ms` (summary) | how late claimed checks were | rising p95 → falling behind |
| `monitor_check_latency` (timer, by `result`) | probe latency | tail growth → slow targets / pool waits |
| `monitor_checks_total{result}` | up/down/**inconclusive** counts | inconclusive rising → our infra, not targets |
| `monitor_checks_rejected{pool}` | dropped (pool saturated) | any sustained > 0 → under-capacity |
| `monitor_alerts{type}`, `monitor_email_outbox{outcome}` | alert/email flow | retry/failed climbing → SMTP problems |
| Hikari `*_pending`, `*_active`; DB tx/s, lock waits | DB pressure | the real ceiling |
| JVM GC pause, worker pool active/queue | host pressure | long pauses → duplicate-probe risk |

## Test harness

- **Seed**: script to create N monitors (mix of intervals 10s/30s/60s; mix of fast and slow timeouts
  to exercise both bulkhead pools) pointing at controllable stub targets.
- **Targets**: a stub server (e.g. nginx/Go) with tunable latency and status, plus a "blackhole"
  endpoint to force connect/read timeouts, and one host hosting *many* monitors to test per-route caps.
- **API load**: k6/Gatling against create/list/uptime/incidents endpoints.
- **Env**: `monitor.ssrf.allow-private=true` only in the isolated test env (targets are internal).

## Scenarios

1. **Steady-state ramp** — increase N at fixed intervals until `scheduler_lag_ms` p95 exceeds one
   poll interval and stays there. That N is the knee for this config. Record checks/s at the knee.
2. **Burst** — make a large cohort due simultaneously; confirm capacity-aware claiming + Abort policy
   shed load (rejected counter rises) without the poller blocking, and lag recovers.
3. **Slow-target flood** — many high-timeout monitors; confirm the **slow** pool saturates while the
   **fast** pool (and its monitors' lag) stays healthy — i.e. bulkhead isolation holds.
4. **Pool-exhaustion / infra failure** — many monitors on one host (exceed `max-per-route`); confirm
   the resulting failures register as **`inconclusive`**, NOT `down`, and **no incidents/alerts fire**.
5. **Target outage** — flip a cohort to 5xx/timeout; confirm DOWN after exactly 3 consecutive
   failures, one incident + one alert per monitor, recovery on restore, no alert storm.
6. **DB failover** — restart/failover Postgres mid-load; confirm polling pauses then auto-resumes,
   no data corruption, monitors re-claimed (no permanent stuck/zombie).
7. **Multi-instance** — scale 1→3 instances; confirm ~linear throughput, no double-execution
   (`monitor_checks_total` not inflated), exactly one retention run (leader lock).
8. **Uptime read load** — hit `/{id}/uptime` for large windows (30d/90d) while seeding history;
   measure latency and confirm rollups serve the purged portion.

## Pass/fail thresholds (tune per SLA)

- At target capacity: `scheduler_lag_ms` p95 < poll interval; `rejected` ≈ 0.
- Scenario 4: `incidents_opened` from infra failures == 0.
- Scenario 5: exactly 1 alert per DOWN edge; recovery alert delivered.
- Scenario 7: no duplicate logs per check; exactly 1 retention execution per schedule.

## Method

Start at 1 instance, find the knee (Scenario 1), then add instances to confirm scaling and locate the
DB ceiling. Capture checks/s, DB tx/s, Hikari saturation, and lag at each step to produce a
capacity table (monitors-per-instance per interval) for sizing.
