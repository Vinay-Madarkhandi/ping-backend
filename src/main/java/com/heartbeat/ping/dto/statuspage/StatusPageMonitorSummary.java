package com.heartbeat.ping.dto.statuspage;

import java.util.UUID;

/** Just enough for the owner's editor to show which monitors are on a page. */
public record StatusPageMonitorSummary(UUID id, String name) {
}
