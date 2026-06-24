package com.heartbeat.ping.modles;

/**
 * Outcome of a single probe.
 *
 * <ul>
 *   <li>{@code UP} — target responded as expected.</li>
 *   <li>{@code DOWN} — target-attributable failure (bad status, keyword miss, connect/read timeout,
 *       connection refused, TLS error, DNS failure). Counts toward the failure threshold.</li>
 *   <li>{@code INCONCLUSIVE} — our own infrastructure could not complete the probe (HTTP pool
 *       exhausted, thread interrupted, SSRF-blocked resolution). Never counted as a failure, so it
 *       can never open an incident or fire an alert.</li>
 * </ul>
 */
public enum ProbeOutcome {
    UP,
    DOWN,
    INCONCLUSIVE
}
