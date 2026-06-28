package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Enforces plan limits at monitor-creation time (Layer 1): monitor count, minimum check interval,
 * and maximum timeout. Throws {@link PlanLimitExceededException} (HTTP 403) on violation.
 */
@Service
@RequiredArgsConstructor
public class UsageLimitService {

    private final MonitorRepository monitorRepository;
    private final PlanService planService;

    public void validateNewMonitor(User user, int intervalMs, int timeoutMs) {
        Plan plan = planFor(user);

        long currentMonitors = monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId());
        if (currentMonitors >= plan.getMaxMonitors()) {
            throw new PlanLimitExceededException(
                    "Monitor limit reached for the " + plan.getName() + " plan (max " + plan.getMaxMonitors() + ")");
        }

        if (intervalMs < plan.getMinIntervalMs()) {
            throw new PlanLimitExceededException(
                    "Check interval below the " + plan.getName() + " plan minimum of " + plan.getMinIntervalMs() + " ms");
        }

        if (timeoutMs > plan.getMaxTimeoutMs()) {
            throw new PlanLimitExceededException(
                    "Timeout exceeds the " + plan.getName() + " plan maximum of " + plan.getMaxTimeoutMs() + " ms");
        }
    }

    private Plan planFor(User user) {
        UUID planId = user.getPlan() != null ? user.getPlan().getId() : null;
        Plan plan = planId != null ? planService.getById(planId) : planService.getByName("FREE");
        if (plan == null) {
            throw new IllegalStateException("No plan configured for user " + user.getId());
        }
        return plan;
    }
}
