package com.heartbeat.ping.service.alert;

import com.heartbeat.ping.config.properties.AlertProperties;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.modles.UserAlertUsage;
import com.heartbeat.ping.repository.UserAlertUsageRepository;
import com.heartbeat.ping.service.PlanService;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.incident.IncidentService;
import com.heartbeat.ping.service.metrics.MetricsService;
import com.heartbeat.ping.service.notification.AlertType;
import com.heartbeat.ping.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Explicit state machine: {@code UNKNOWN/UP/SUSPECT/DOWN}. A monitor is declared DOWN only after
 * {@code failureThreshold} consecutive failures (SUSPECT in between) and RECOVERED after
 * {@code recoveryThreshold} consecutive successes. Incident open/resolve and alert enqueue happen
 * here, inside the caller's locked transaction, so they are atomic with the state change.
 *
 * <p>DOWN alerts are rate-limited by {@code cooldown}; RECOVERY alerts always send.
 * Operates on the managed status row (loaded FOR UPDATE by the caller) and never touches paused
 * monitors (the caller skips them).
 */
@Service
@RequiredArgsConstructor
public class DefaultAlertEngine implements AlertEngine {

    private final AlertProperties alertProps;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final PlanService planService;
    private final UserAlertUsageRepository userAlertUsageRepository;
    private final MetricsService metricsService;

    @Override
    public void process(Monitor monitor, MonitorStatus status, CheckResult result, Instant now) {
        if (result.inconclusive()) {
            // Our infrastructure failed to complete the probe — no information about the target.
            // Do not advance/reset counters, change state, open incidents or alert.
            return;
        }

        status.setTotalChecks(status.getTotalChecks() + 1);

        if (result.up()) {
            status.setTotalUp(status.getTotalUp() + 1);
            onSuccess(monitor, status, now);
        } else {
            status.setTotalDown(status.getTotalDown() + 1);
            status.setLastDowntimeAt(toLocal(now));
            onFailure(monitor, status, result, now);
        }

        status.setUptimePercentage(ratio(status)); // Phase 3 replaces with duration-based uptime
        status.setUpdatedAt(toLocal(now));
    }

    private void onFailure(Monitor monitor, MonitorStatus status, CheckResult result, Instant now) {
        status.setConsecutiveSuccesses(0);
        status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);

        if (status.getCurrentState() == MonitorState.DOWN) {
            return; // already down — no new incident or alert
        }

        if (status.getConsecutiveFailures() >= alertProps.getFailureThreshold()) {
            status.setCurrentState(MonitorState.DOWN);
            status.setDownSince(toLocal(now));
            Incident incident = incidentService.open(monitor, reasonFor(result), now);
            sendDownAlertIfNotCoolingDown(monitor, status, incident, now);
        } else {
            status.setCurrentState(MonitorState.SUSPECT);
        }
    }

    private void onSuccess(Monitor monitor, MonitorStatus status, Instant now) {
        status.setConsecutiveFailures(0);

        if (status.getCurrentState() != MonitorState.DOWN) {
            status.setCurrentState(MonitorState.UP);
            status.setConsecutiveSuccesses(0);
            return;
        }

        status.setConsecutiveSuccesses(status.getConsecutiveSuccesses() + 1);
        if (status.getConsecutiveSuccesses() >= alertProps.getRecoveryThreshold()) {
            status.setCurrentState(MonitorState.UP);
            status.setDownSince(null);
            status.setConsecutiveSuccesses(0);
            Optional<Incident> resolved = incidentService.resolve(monitor.getId(), now);
            resolved.ifPresent(incident -> {
                notificationService.enqueue(monitor, incident, AlertType.RECOVERY, now);
                status.setLastAlertSentAt(toLocal(now));
            });
        }
        // else: remain DOWN until the recovery threshold is met
    }

    private void sendDownAlertIfNotCoolingDown(Monitor monitor, MonitorStatus status, Incident incident, Instant now) {
        Plan plan = planFor(monitor);
        Duration cooldown = plan != null ? Duration.ofSeconds(plan.getAlertCooldownSeconds()) : alertProps.getCooldown();

        LocalDateTime lastAlert = status.getLastAlertSentAt();
        boolean coolingDown = lastAlert != null
                && Duration.between(lastAlert.toInstant(ZoneOffset.UTC), now).compareTo(cooldown) < 0;
        if (coolingDown) {
            metricsService.recordAlertSuppressed("cooldown");
            return; // suppress repeated DOWN alerts within the cooldown window
        }

        UUID userId = userIdOf(monitor);
        LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (plan != null && userId != null && dailyAlertsUsed(userId, day) >= plan.getMaxAlertsPerDay()) {
            metricsService.recordAlertSuppressed("daily_cap");
            return; // user has hit their daily alert cap
        }

        notificationService.enqueue(monitor, incident, AlertType.DOWN, now);
        if (userId != null) {
            userAlertUsageRepository.incrementAlerts(userId, day);
        }
        status.setLastAlertSentAt(toLocal(now));
    }

    /** Resolves the owning user's plan from the cache. Returns null if the user/plan is unset. */
    private Plan planFor(Monitor monitor) {
        User user = monitor.getUser();
        if (user == null || user.getPlan() == null) {
            return null;
        }
        return planService.getById(user.getPlan().getId());
    }

    private UUID userIdOf(Monitor monitor) {
        return monitor.getUser() != null ? monitor.getUser().getId() : null;
    }

    private int dailyAlertsUsed(UUID userId, LocalDate day) {
        return userAlertUsageRepository.findByUserIdAndDay(userId, day)
                .map(UserAlertUsage::getAlertsSent)
                .orElse(0);
    }

    private String reasonFor(CheckResult result) {
        if (result.errorMessage() != null) {
            return result.errorMessage();
        }
        return "HTTP " + result.statusCode();
    }

    private double ratio(MonitorStatus status) {
        return status.getTotalChecks() == 0
                ? 100.0
                : (status.getTotalUp() * 100.0) / status.getTotalChecks();
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
