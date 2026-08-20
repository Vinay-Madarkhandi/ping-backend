package com.heartbeat.ping.dto.statuspage;

import java.util.List;
import java.util.UUID;

/** Owner-facing view (authenticated) — used to list and edit a user's own status pages. */
public record StatusPageResponse(
        UUID id,
        String slug,
        String title,
        String description,
        List<StatusPageMonitorSummary> monitors,
        String logoUrl,
        /** Whether a password is currently set. The hash itself is never returned. */
        boolean passwordProtected
) {
}
