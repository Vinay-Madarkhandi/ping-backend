# Ping

Production-oriented uptime monitoring backend built with Spring Boot, PostgreSQL, Flyway, Spring
Security, Micrometer, and Docker Compose.

Ping lets users register HTTP monitors, run periodic health checks, detect outages with debounce
thresholds, send email alerts through a durable outbox, calculate duration-based uptime, and expose
operational metrics for Prometheus.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [API](#api)
- [Observability](#observability)
- [Operations](#operations)
- [Testing](#testing)
- [Production checklist](#production-checklist)
- [Known limitations](#known-limitations)
- [Project docs](#project-docs)

## Features

- User signup and signin with JWT stored in an HttpOnly cookie.
- HTTP monitor creation with interval, timeout, method, expected status, keyword matching, redirects,
  and custom headers.
- Multi-instance-safe scheduler using PostgreSQL row locking and `FOR UPDATE SKIP LOCKED`.
- Fast and slow worker pools to isolate high-timeout monitors from normal checks.
- Three probe outcomes: `UP`, `DOWN`, and `INCONCLUSIVE`.
- Alert state machine with `UNKNOWN`, `UP`, `SUSPECT`, and `DOWN`.
- Durable email outbox with retries and exponential backoff.
- Incident tracking with one open incident per monitor.
- Pause, resume, archive, logs, incidents, status, and uptime APIs.
- Duration-based uptime calculation that excludes paused time and data gaps.
- Raw log retention with daily rollups.
- SSRF protection at monitor creation and at DNS resolution/connect time.
- Prometheus metrics through Spring Actuator, with provisioned Grafana dashboards and alert rules.
- Correlated, human-readable logs (per-request `X-Request-Id` trace id) shipped to Loki for search.
- Docker Compose stack for PostgreSQL, the app, and an optional full observability stack
  (Prometheus + Grafana + Loki + Alloy + Alertmanager).

## Architecture

The application is a stateless Spring Boot service backed by PostgreSQL. PostgreSQL is used as the
system of record, the scheduler coordination layer, and the database clock.

```text
HTTP clients
    |
    v
Spring Boot app instances
    |-- REST API
    |-- monitor scheduler
    |-- fast / slow check executors
    |-- email outbox worker
    |-- retention and rollup job
    |
    +--> monitored HTTP targets
    +--> SMTP relay
    v
PostgreSQL

Prometheus scrapes: /actuator/prometheus
```

Important runtime properties:

- App instances are horizontally scalable.
- Schedulers on multiple instances claim disjoint work with `SKIP LOCKED`.
- Long checks are leased by `max(interval, timeout) + margin` to avoid duplicate execution during
  normal operation.
- HTTP probes run outside database transactions.
- Alert transitions are serialized on the `monitor_status` row.
- Retention runs as a cluster singleton through a database leader lock.
- Time-sensitive writes use the database clock to avoid instance clock skew.

For the full design rationale, read [architecture.md](architecture.md).

## Tech Stack

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Web | Spring Web MVC |
| Security | Spring Security, JWT cookie auth |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| Migrations | Flyway |
| HTTP checks | Apache HttpClient 5 with connection pooling |
| Metrics | Micrometer, Spring Boot Actuator, Prometheus registry |
| Email | Spring Mail, durable outbox table |
| Tests | JUnit, Spring Boot Test, H2, Testcontainers PostgreSQL |
| Packaging | Gradle, Docker |

## Quick Start

### Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle wrapper from this repository (`./gradlew`)

### Run with Docker Compose

Copy the environment template and fill in real values:

```bash
cp .env.example .env
```

Start PostgreSQL and the app:

```bash
docker compose up --build -d
```

Check service status:

```bash
docker compose ps
```

Verify the app:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

The API is available at:

```text
http://localhost:8080
```

### Run with the observability stack

A full monitoring stack — **Prometheus + Grafana + Loki + Alloy + Alertmanager** — ships as an
optional Compose profile:

```bash
docker compose --profile observability up --build -d
```

| Service | URL | Purpose |
|---------|-----|---------|
| Grafana | http://localhost:3000 | Dashboards + log search (login `admin` / `${GRAFANA_ADMIN_PASSWORD:-admin}`) |
| Prometheus | http://localhost:9090 | Metrics + alert-rule evaluation |
| Alertmanager | http://localhost:9093 | Alert routing |
| Loki | http://localhost:3100 | Log store (queried via Grafana) |

Grafana auto-loads the **"Ping — Overview"** dashboard (uptime, check rates/latency, scheduler lag,
pool saturation, alerts/email outbox, JVM, DB pool, and a live application-log panel). Prometheus
scrapes `http://app:8080/actuator/prometheus` and fires the rules in `docker/prometheus/alerts.yml`;
Alloy tails container logs into Loki. See [docs/observability.md](docs/observability.md) for the full
tour and the alert → action table.

### Run Locally Without Docker App Container

Start a PostgreSQL database, then provide datasource settings:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/ping
export DB_USERNAME=pinguser
export DB_PASSWORD=change-me
export JWT_SECRET=$(openssl rand -base64 48)
export MAIL_USERNAME=your-address@gmail.com
export MAIL_PASSWORD=your-gmail-app-password

./gradlew bootRun
```

If you only need PostgreSQL from Compose, use:

```bash
docker compose up -d postgres
```

The Compose file publishes PostgreSQL on host port `5433` to avoid clashing with a local database:

```bash
export DB_URL=jdbc:postgresql://localhost:5433/ping
```

## Configuration

Runtime configuration is supplied through environment variables and `application.properties`.

### Required Environment Variables

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for PostgreSQL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password or Gmail app password |
| `JWT_SECRET` | Long random JWT signing secret |

For Docker Compose, these values come from `.env`.

### Important Application Properties

| Property | Default | Purpose |
|---|---:|---|
| `monitor.scheduler.enabled` | `true` | Enable scheduler, email worker, and retention job |
| `monitor.scheduler.poll-rate-ms` | `5000` | How often due monitors are claimed |
| `monitor.scheduler.batch-size` | `100` | Max monitors claimed per poll |
| `monitor.scheduler.lease-margin` | `5s` | Safety margin added to monitor leases |
| `monitor.scheduler.slow-threshold-ms` | `5000` | Timeout threshold for the slow executor |
| `monitor.executor.fast.*` | see config | Fast check worker pool sizing |
| `monitor.executor.slow.*` | see config | Slow check worker pool sizing |
| `monitor.http.max-total` | `200` | Total outbound HTTP connection pool limit |
| `monitor.http.max-per-route` | `20` | Per-host connection pool limit |
| `monitor.http.connect-timeout` | `5s` | Outbound connect timeout |
| `monitor.http.response-timeout` | `10s` | Default read timeout |
| `monitor.alert.failure-threshold` | `3` | Consecutive failures before DOWN |
| `monitor.alert.recovery-threshold` | `1` | Consecutive successes before recovery |
| `monitor.alert.cooldown` | `15m` | DOWN alert cooldown |
| `monitor.email.max-attempts` | `5` | Email retry attempts before dead-letter |
| `monitor.email.backoff` | `1m` | Base email retry backoff |
| `monitor.retention.days` | `30` | Raw monitor log retention |
| `monitor.retention.cron` | `0 30 3 * * *` | Daily retention job schedule |
| `monitor.ssrf.enabled` | `true` | Enable SSRF validation |
| `monitor.ssrf.allow-private` | `false` | Allow private/loopback monitor targets |
| `monitor.uptime.default-window` | `24h` | Default uptime API window |
| `monitor.uptime.gap-multiplier` | `3` | Missing-data gap threshold multiplier |
| `monitor.uptime.min-gap-threshold` | `30s` | Minimum gap threshold |

Use `monitor.scheduler.enabled=false` for passive API-only nodes.

## API

Base URL:

```text
http://localhost:8080
```

Authentication uses a `JwtToken` HttpOnly cookie returned by signin. For command-line examples, use a
cookie jar:

```bash
COOKIE_JAR=/tmp/ping-cookies.txt
```

### Health

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

### Signup

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/signup/user \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "Vinay",
    "email": "vinay@example.com",
    "password": "change-me"
  }'
```

### Signin

```bash
curl -i -c "$COOKIE_JAR" -X POST http://localhost:8080/api/v1/auth/signin/user \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "vinay@example.com",
    "password": "change-me"
  }'
```

### Validate Session

```bash
curl -b "$COOKIE_JAR" http://localhost:8080/api/v1/auth/validate
```

### Create Monitor

```bash
curl -i -b "$COOKIE_JAR" -X POST http://localhost:8080/api/v1/monitors \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Example",
    "url": "https://example.com",
    "intervalMilliseconds": 30000,
    "timeoutMilliseconds": 5000,
    "monitorMethod": "GET",
    "expectedStatusCode": 200,
    "keyword": "Example Domain",
    "followRedirects": true,
    "customHeaders": {
      "User-Agent": "Ping-Monitor"
    }
  }'
```

### List Monitors

```bash
curl -b "$COOKIE_JAR" http://localhost:8080/api/v1/monitors
```

### Update Active Flag

```bash
curl -i -b "$COOKIE_JAR" -X PATCH http://localhost:8080/api/v1/monitors/{monitorId}/toggle \
  -H 'Content-Type: application/json' \
  -d '{"active": false}'
```

### Pause and Resume

```bash
curl -i -b "$COOKIE_JAR" -X POST http://localhost:8080/api/v1/monitors/{monitorId}/pause
curl -i -b "$COOKIE_JAR" -X POST http://localhost:8080/api/v1/monitors/{monitorId}/resume
```

### Logs

```bash
curl -b "$COOKIE_JAR" \
  'http://localhost:8080/api/v1/monitors/{monitorId}/logs?page=0&size=20'
```

### Status

```bash
curl -b "$COOKIE_JAR" \
  http://localhost:8080/api/v1/monitors/{monitorId}/status
```

### Incidents

```bash
curl -b "$COOKIE_JAR" \
  'http://localhost:8080/api/v1/monitors/{monitorId}/incidents?page=0&size=20'
```

### Uptime

```bash
curl -b "$COOKIE_JAR" \
  'http://localhost:8080/api/v1/monitors/{monitorId}/uptime?window=24h'
```

Accepted window examples:

```text
30m
24h
7d
```

### Archive Monitor

```bash
curl -i -b "$COOKIE_JAR" -X DELETE \
  http://localhost:8080/api/v1/monitors/{monitorId}
```

Archive is a soft delete. History is retained.

## Observability

The app ships a complete, ready-to-run observability stack (see
[docs/observability.md](docs/observability.md) for the full guide):

- **Metrics** — Micrometer → Prometheus, visualized in a provisioned **Grafana** dashboard.
- **Logs** — correlated, human-readable logs (per-request `X-Request-Id` trace id), shipped by
  **Alloy** into **Loki** and searchable from the same Grafana.
- **Alerts** — Prometheus rules (`docker/prometheus/alerts.yml`) routed through **Alertmanager**.

Launch it all with `docker compose --profile observability up --build` (URLs in
[Run with the observability stack](#run-with-the-observability-stack)).

Actuator endpoints:

```text
GET  /actuator/health          # liveness/readiness (public)
GET  /actuator/health/liveness
GET  /actuator/health/readiness
GET  /actuator/info            # build/app info (public)
GET  /actuator/prometheus      # metrics scrape (public)
GET  /actuator/metrics         # metric browser (authenticated)
GET  /actuator/loggers         # view/change log levels at runtime (authenticated)
POST /actuator/loggers/com.heartbeat.ping   # e.g. {"configuredLevel":"DEBUG"} — no restart needed
```

Prometheus metrics include:

| Metric | Meaning |
|---|---|
| `monitor_check_latency` | Probe latency timer tagged by result |
| `monitor_checks_total` | Count of `up`, `down`, and `inconclusive` checks |
| `monitor_scheduler_lag_ms` | How late due checks are being claimed |
| `monitor_checks_rejected` | Checks dropped because a worker pool was saturated |
| `monitor_alerts` | DOWN and recovery alert transitions |
| `monitor_email_outbox` | Email send outcomes |
| Hikari metrics | Database pool health |
| JVM metrics | Memory, GC, threads |

Useful Prometheus queries:

```promql
up
rate(monitor_checks_total[5m])
rate(monitor_checks_total{result="inconclusive"}[5m])
rate(monitor_checks_rejected[5m])
monitor_scheduler_lag_ms_sum / monitor_scheduler_lag_ms_count
jvm_memory_used_bytes
hikaricp_connections_active
```

For dashboard and alert guidance, see [docs/runbook.md](docs/runbook.md).

## Operations

### Common Docker Commands

```bash
docker compose up -d
docker compose up --build -d
docker compose --profile observability up -d
docker compose ps
docker logs -f ping-app
docker logs -f ping-postgres
docker logs -f ping-prometheus
docker compose restart app
docker compose down
```

Remove containers and database volume:

```bash
docker compose down -v
```

### Database Migrations

Flyway owns schema changes. Hibernate is configured with:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Production rules:

- Add a new Flyway migration for every schema change.
- Do not edit migrations that have already been applied.
- Prefer backward-compatible migrations for rolling deploys.
- Rollbacks should use forward compensating migrations, not schema rewind.

### Deployment

1. Provide production environment variables.
2. Start one app instance first so Flyway can apply pending migrations.
3. Confirm `/actuator/health` is `UP`.
4. Scale additional app instances.
5. Restrict actuator endpoints at the ingress or network layer.

See [docs/deployment-architecture.md](docs/deployment-architecture.md) for the multi-instance model
and scale path.

### Alert Email Flow

Alert emails are enqueued in `email_outbox` and sent asynchronously by `EmailOutboxWorker`.

Properties:

- Enqueue is idempotent through a unique dedupe key.
- Delivery is at-least-once.
- Failed sends are retried with backoff.
- Rows become `FAILED` after `monitor.email.max-attempts`.

Operators should monitor:

```promql
rate(monitor_email_outbox{outcome="failed"}[5m])
```

## Testing

Run the test suite:

```bash
./gradlew test
```

Build the application jar:

```bash
./gradlew bootJar
```

Test strategy:

- Pure unit tests for uptime calculation, alert transitions, JWT, and mappers.
- H2-backed Spring tests for fast context and schema validation.
- Testcontainers PostgreSQL coverage for Postgres-specific claim behavior.

## Production Checklist

Before exposing this service outside local development:

- Rotate any previously committed or shared SMTP credentials.
- Set a strong `JWT_SECRET`.
- Use real database credentials and restrict database access.
- Set secure mail credentials through environment variables only.
- Put the app behind TLS.
- Auth cookie hardening is config-driven now: `cookie.secure` defaults to `true` and `cookie.same-site`
  defaults to `Lax`. Only override `COOKIE_SECURE=false` for local HTTP development (docker-compose
  already does this for you); never in a real deployment.
- Restrict `/actuator/health`, `/actuator/info`, and `/actuator/prometheus` to internal networks.
- Keep `monitor.ssrf.allow-private=false` unless running an isolated test environment.
- Configure Prometheus alerting for scheduler lag, rejected checks, inconclusive checks, failed email,
  Hikari saturation, JVM memory, and DB health.
- Decide retention/archive policy for long-term incidents, email outbox rows, and historical logs.
- Run a load test for your expected monitor count and interval mix.

## Known Limitations

Current accepted trade-offs:

- Email delivery is at-least-once, so duplicate alert emails are possible after a crash between SMTP
  success and marking the row sent.
- `INCONCLUSIVE` checks write log rows and do not open incidents, which can slightly inflate uptime
  during sustained infrastructure-side failures.
- Duplicate outbound probes are bounded but not impossible if a JVM pauses longer than the lease.
- Under overload, checks can be rejected and effectively delayed until the lease expires.
- Sent and failed email outbox rows currently accumulate.
- There is no built-in dead-letter alert beyond the Prometheus metric.
- Long-term partitioning is not implemented for incidents, email outbox, or daily stats.
- The write path is limited by a single PostgreSQL primary at large scale.
- Actuator endpoints are unauthenticated and must be network-restricted.

See [docs/known-limitations.md](docs/known-limitations.md) for details.

## Project Docs

- [architecture.md](architecture.md): detailed system design and invariants.
- [docs/deployment-architecture.md](docs/deployment-architecture.md): production topology and scale path.
- [docs/observability.md](docs/observability.md): metrics, logs, dashboards, and alerting stack.
- [docs/runbook.md](docs/runbook.md): operational response guide.
- [docs/uptime.md](docs/uptime.md): uptime formula and edge cases.
- [docs/load-testing-plan.md](docs/load-testing-plan.md): capacity test plan.
- [docs/known-limitations.md](docs/known-limitations.md): accepted trade-offs and deferred work.

## License

No license file is currently included in this repository. Add one before publishing or distributing
the project.
