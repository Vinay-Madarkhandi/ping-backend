package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Enforces plan limits on monitor configuration (Layer 1): monitor count, minimum check interval,
 * and maximum timeout. Throws {@link PlanLimitExceededException} (HTTP 403) on violation.
 *
 * <p>Creation and editing share the settings checks but differ on the count check — editing an
 * existing monitor must not fail merely because the user is already at their limit — so the two are
 * separate methods.
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

        validateSettings(plan, intervalMs, timeoutMs);
    }

    /**
     * Re-checks interval/timeout against the plan without the monitor-count check. Used when editing
     * or restoring a monitor, and important on its own: a user downgraded from PRO to FREE must not
     * be able to keep a sub-minimum interval by editing an existing monitor.
     */
    public void validateMonitorSettings(User user, int intervalMs, int timeoutMs) {
        validateSettings(planFor(user), intervalMs, timeoutMs);
    }

    /**
     * Checks that restoring an archived monitor would not push the user over their plan's limit.
     * Archived monitors do not count toward the limit, so restoring one is effectively a creation.
     */
    public void validateRestore(User user) {
        Plan plan = planFor(user);
        long currentMonitors = monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId());
        if (currentMonitors >= plan.getMaxMonitors()) {
            throw new PlanLimitExceededException(
                    "Restoring would exceed the " + plan.getName() + " plan limit of "
                            + plan.getMaxMonitors() + " monitors");
        }
    }

    private void validateSettings(Plan plan, int intervalMs, int timeoutMs) {
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
