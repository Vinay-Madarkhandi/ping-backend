package com.heartbeat.ping.service.check;

/** Performs the actual HTTP probe for a monitor and reports whether it is up. */
public interface HealthCheckService {

    CheckResult check(CheckSpec spec);
}
