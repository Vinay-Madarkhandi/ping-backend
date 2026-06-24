# Uptime calculation

Uptime is **duration-based**, not check-count based. We measure the fraction of *monitored time*
that a monitor was up — never `successfulChecks / totalChecks` (which is biased by check frequency
and ignores how long an outage actually lasted).

Implemented by `UptimeCalculator` (pure, fully unit-tested) and fed by `UptimeService`.
Endpoint: `GET /api/v1/monitors/{id}/uptime?window=24h` (`window` accepts `30m`, `24h`, `7d`, …).

## Formula

For a window `W = [windowStart, windowEnd)`:

```
monitored = |W| - paused - gap
uptime%   = monitored <= 0 ? null : (monitored - down) / monitored * 100
```

Window time is partitioned into disjoint categories by **precedence** (highest wins), so overlaps
are never double-counted:

| Precedence | Category | Effect |
|-----------|----------|--------|
| 1 | **Paused** | excluded from the denominator (intentional, "not monitored") |
| 2 | **Gap** (missing data, outside paused) | excluded from the denominator |
| 3 | **Down** (incident, outside paused/gap) | counts against uptime |
| 4 | **Up** | the remaining monitored time |

Concretely: `paused' = merge(paused)`, `gap' = gap − paused'`, `down' = down − paused' − gap'`.

## Edge cases

1. **DOWN intervals** — taken from `incident` rows overlapping the window: `[started_at, resolved_at)`.
   An **open** incident (no `resolved_at`) is counted up to `windowEnd`.

2. **Paused intervals** — taken from `monitor_pause_window` rows: `[paused_at, resumed_at)`, open
   windows extended to `windowEnd`. Paused time is **subtracted from the denominator**, so a paused
   monitor neither gains nor loses uptime. (Health state is frozen; the API shows a derived
   `PAUSED` display state.)

3. **Archived monitors** — `windowEnd` is capped at `deleted_at` (no monitoring after archival).
   History is preserved (soft delete), so uptime up to the archival point remains queryable.
   `windowStart` is also clamped to the monitor's `created_at` (no uptime before it existed).

4. **Missing data / scheduler outage** — a gap between consecutive checks longer than
   `gapMultiplier × interval` (floored by `minGapThreshold`), plus the leading edge
   (`windowStart → first check`) and trailing edge (`last check → windowEnd`), is treated as a
   **coverage gap** and excluded from the denominator. We never claim uptime we did not measure.
   By precedence, a gap also overrides an open incident: if our scheduler was down we do not assert
   the target stayed down either — that time is simply unmonitored.

5. **No monitored time** — if the whole window was paused / had no coverage, `monitored = 0` and
   `uptimePercentage` is returned as **`null`** (not `100`), so consumers can distinguish "perfect"
   from "unknown".

## Clock source

All interval boundaries come from the **database clock** (`DatabaseClock` → `select current_timestamp`),
not per-JVM wall time: lease times, incident `started_at`/`resolved_at`, pause `paused_at`/`resumed_at`,
and `windowEnd` are all DB-anchored. This eliminates cross-node clock skew in a multi-instance
deployment, so duration math is consistent regardless of which instance recorded an event.

## Cost / scaling note

Gap detection scans the window's check timestamps. For large windows this is the optimization point:
`monitor_daily_stats` (populated by the retention rollup before raw logs are purged) holds per-day
`down`/`total` aggregates and is the intended source for long historical windows once raw logs age out.
