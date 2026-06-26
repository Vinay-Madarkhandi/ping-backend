# Observability

Production-grade observability for `ping` spans three pillars — **metrics**, **logs**, and
**alerts** — all runnable locally as one Compose profile and structured to map cleanly onto a real
Kubernetes/managed deployment.

```
                         ┌─────────────────────────────────────────────┐
   ping app  ── /actuator/prometheus ──►  Prometheus ──► Alertmanager   │
      │ stdout logs                          │  rules        │ routes    │
      ▼                                      ▼               ▼           │
    Alloy  ───────────────────────────►   Grafana  ◄── dashboards + UI  │
      │ (tails container logs)                ▲                          │
      └────────────────►  Loki  ──────────────┘  (log search/panels)     │
                         └─────────────────────────────────────────────┘
```

## Run it

```bash
cp .env.example .env          # set GRAFANA_ADMIN_PASSWORD / LOG_LEVEL if you like
docker compose --profile observability up --build -d
```

| Service | URL | Notes |
|---------|-----|-------|
| **Grafana** | http://localhost:3000 | login `admin` / `${GRAFANA_ADMIN_PASSWORD:-admin}`; opens on the Ping dashboard |
| **Prometheus** | http://localhost:9090 | `/targets` (scrape health), `/alerts` (rule state) |
| **Alertmanager** | http://localhost:9093 | grouped/firing alerts |
| **Loki** | http://localhost:3100 | log store; query via Grafana (no UI of its own) |

Without the profile, `docker compose up` runs just Postgres + the app — the app still exposes
`/actuator/prometheus` and logs to stdout, so nothing about the app depends on the stack being up.

## 1. Metrics

Micrometer exports to Prometheus at `/actuator/prometheus`. Every series carries an
`application="ping"` tag (added in `WebLoggingConfig`). Histogram buckets are enabled for HTTP and
check latency so Grafana computes true server-side p95/p99.

Domain metrics come from `MicrometerMetricsService`:

| Micrometer name | Prometheus name | Meaning |
|---|---|---|
| `monitor.checks.total` | `monitor_checks_total{result}` | up / down / inconclusive check counts |
| `monitor.check.latency` | `monitor_check_latency_seconds*` | probe latency timer (histogram) |
| `monitor.scheduler.lag.ms` | `monitor_scheduler_lag_ms_{sum,count}` | how late due checks were claimed |
| `monitor.checks.rejected` | `monitor_checks_rejected_total{pool}` | checks dropped on pool saturation |
| `monitor.alerts` | `monitor_alerts_total{type}` | DOWN / recovery transitions |
| `monitor.email.outbox` | `monitor_email_outbox_total{outcome}` | sent / retry / failed emails |

Plus the Spring Boot defaults: `http_server_requests_seconds*`, `hikaricp_connections_*`,
`jvm_memory_*`, `jvm_gc_pause_seconds_*`, thread/CPU.

### Dashboard — "Ping — Overview"

Auto-provisioned from `docker/grafana/dashboards/ping-overview.json` (datasources from
`docker/grafana/provisioning/`). Panels:

- **Top row (stat):** App up/down · checks/sec · HTTP req/sec · scheduler lag (avg ms, color-graded).
- **Checks:** outcome rate by result (stacked) · check latency p95/p99.
- **Capacity:** checks rejected by pool · alerts fired & email-outbox outcomes (failed in red).
- **HTTP:** request rate by status · latency p95 by URI.
- **Runtime:** Hikari pool (active/idle/pending) · JVM heap used-vs-max · GC pause rate.
- **Logs:** live tail of the app container, sourced from Loki.

The dashboard is editable in the UI; to persist a change, export JSON and overwrite the file.

## 2. Logs

Logging is configured in `src/main/resources/logback-spring.xml`:

- Human-readable, colorized console output in all environments, wrapped in a non-blocking
  `AsyncAppender` (the poller / HTTP pools / email worker never block on logging).
- Every line carries a `[traceId]` correlation id. `CorrelationIdFilter` (registered first in the
  chain via `WebLoggingConfig`) reads or generates an `X-Request-Id` per request, puts it in the MDC,
  echoes it on the response, and logs one `METHOD uri -> status (ms)` line per request.
- Framework noise (Hibernate SQL, HC5, etc.) is quieted so the signal stays readable.

**Shipping:** Grafana Alloy (`docker/alloy/config.alloy`) discovers containers via the Docker socket
and pushes their stdout/stderr to Loki, labelled by `compose_service` and `container`. In Grafana,
filter the app with `{compose_service="app"}`; correlate with a request via
`{compose_service="app"} |= "<traceId>"`.

**Changing log level** — no restart needed (env default is `LOG_LEVEL`, `INFO`):

```bash
curl -u <user> -X POST http://localhost:8080/actuator/loggers/com.heartbeat.ping \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

## 3. Alerts

Rules live in `docker/prometheus/alerts.yml`; Prometheus evaluates them and pushes to Alertmanager
(`docker/alertmanager/alertmanager.yml`). By default Alertmanager uses a no-op receiver (alerts are
still visible in its UI and in Grafana) — wire Slack/email/PagerDuty by editing the commented
`receivers` block.

| Alert | Fires when | First response |
|---|---|---|
| `PingAppDown` (critical) | scrape of `up{job="ping"}` fails for 1m | check app container/logs, DB connectivity |
| `SchedulerLagHigh` | avg lag > 10s for 5m | scale instances / raise `monitor.executor.*` (runbook) |
| `CheckPoolRejecting` | any pool drops checks over 5m | raise that pool's size/queue or add instances |
| `InconclusiveRateHigh` | >10% inconclusive for 10m | our infra: HC5 pool, DNS, SSRF blocks |
| `EmailDeadLettering` (critical) | any email → FAILED in 15m | check SMTP; inspect/requeue `email_outbox` |
| `HikariPoolSaturated` | connections pending for 5m | DB/pool bottleneck — see scale path |
| `JvmHeapHigh` | heap > 90% for 10m | GC thrash / OOM risk — investigate or scale |

Per-alert remediation detail lives in [runbook.md](runbook.md).

## Verifying the stack

1. `http://localhost:9090/targets` → `ping` target **UP**; `/alerts` lists all rules as inactive.
2. `http://localhost:3000` → "Ping — Overview" populated; create a monitor / let checks run and watch
   the check panels move.
3. Logs panel streams app logs; `{compose_service="app"} |= "ERROR"` returns nothing on a healthy run.
4. Fault-injection: `docker compose stop app` → within ~1–2m `PingAppDown` shows firing in
   Prometheus `/alerts` and in Alertmanager.

## Production notes / deltas from this local stack

- Loki/Alloy here use the Docker socket and filesystem storage — fine for local/single-host. In
  k8s, ship logs with the Alloy/Promtail DaemonSet and back Loki with object storage.
- Alertmanager has no real receiver wired by default — add one before relying on paging.
- No distributed tracing backend (Tempo/Zipkin) yet; the correlation id is in-process. Adding OTLP
  export is the natural next step.
- Keep `/actuator/health`, `/actuator/info`, `/actuator/prometheus` public (for probes/scraping) and
  everything else authenticated, as configured in `SpringSecurity`; network-restrict the actuator
  port in production regardless.
