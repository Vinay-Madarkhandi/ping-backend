package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.service.MaintenanceWindowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Applies and reverts maintenance windows as their start/end times arrive. Two independent claims
 * per tick — starts and ends — since a window transitions through both states at different times
 * and each is claimed with {@code FOR UPDATE SKIP LOCKED} (see {@link MaintenanceWindowService}),
 * so this is safe to run on every instance without a leader lock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceWindowWorker {

    private static final int BATCH_SIZE = 50;

    private final MaintenanceWindowService maintenanceWindowService;

    @Scheduled(fixedDelayString = "${monitor.maintenance.worker-rate-ms:30000}")
    public void tick() {
        try {
            int started = maintenanceWindowService.applyDueStarts(BATCH_SIZE);
            int ended = maintenanceWindowService.revertDueEnds(BATCH_SIZE);
            if (started > 0 || ended > 0) {
                log.info("Maintenance windows: {} started, {} ended", started, ended);
            }
        } catch (Exception e) {
            log.error("Maintenance window worker tick failed; will retry next tick", e);
        }
    }
}
