package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.MonitorExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class MonitorScheduler {
    private final MonitorRepository monitorRepository;
    private final MonitorExecutionService executionService;

    public MonitorScheduler(MonitorRepository monitorRepository,
                            MonitorExecutionService executionService) {
        this.monitorRepository = monitorRepository;
        this.executionService = executionService;
    }

    @Scheduled(fixedRate = 10_000) // every 10 seconds
    public void run() {
        log.info("Scheduler tick at {}", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();

        List<Monitor> dueMonitors = monitorRepository.findDueMonitors(now);

        if (dueMonitors.isEmpty()) {
            return;
        }

        log.info("Executing {} monitors", dueMonitors.size());

        for (Monitor monitor : dueMonitors) {
            try {
                executionService.execute(monitor);
            } catch (Exception e) {
                log.error("Monitor execution failed for {}", monitor.getId(), e);
            }
        }
    }
}
