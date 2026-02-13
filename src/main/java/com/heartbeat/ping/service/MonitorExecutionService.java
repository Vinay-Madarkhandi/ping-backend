package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class MonitorExecutionService {

    private final MonitorRepository monitorRepository;
    private final MonitorLogsRepository monitorLogsRepository;
    private final MonitorStatusRepository monitorStatusRepository;
    private final RestTemplate restTemplate;

    public MonitorExecutionService(MonitorRepository monitorRepository, MonitorLogsRepository monitorLogsRepository, MonitorStatusRepository monitorStatusRepository, RestTemplate restTemplate){
        this.monitorRepository = monitorRepository;
        this.monitorLogsRepository = monitorLogsRepository;
        this.monitorStatusRepository = monitorStatusRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void execute(Monitor monitor){
        long start = System.currentTimeMillis();

        int statusCode = 0;
        boolean isUp = false;
        String errorMessage = null;

        try {
            ResponseEntity<String> response;
            MonitorMethod method =
                    monitor.getMonitorMethod() != null
                            ? monitor.getMonitorMethod()
                            : MonitorMethod.GET;

            if (method== MonitorMethod.GET) {
                response = restTemplate.getForEntity(monitor.getUrl(), String.class);
            } else if (method == MonitorMethod.POST) {
                response = restTemplate.postForEntity(monitor.getUrl(), null, String.class);
            } else {
                throw new IllegalStateException("Unsupported monitor method: " + method);
            }

            statusCode = response.getStatusCode().value();
            isUp = statusCode >= 200 && statusCode < 300;

        } catch (Exception ex) {
            log.warn("Monitor {} failed: {}", monitor.getId(), ex.getMessage());
            isUp = false;
            errorMessage = ex.getMessage();
        }

        long responseTime = System.currentTimeMillis() - start;

        MonitorLogs logEntry = MonitorLogs.builder()
                .monitor(monitor)
                .statusCode(statusCode)
                .responseTimeInMilli((int) responseTime)
                .isUp(isUp)
                .errorMessage(errorMessage)
                .checkedAt(LocalDateTime.now())
                .build();

        monitorLogsRepository.save(logEntry);

        MonitorStatus status = monitorStatusRepository
                .findById(monitor.getId())
                .orElseGet(() -> {
                    MonitorStatus s = new MonitorStatus();
                    s.setMonitor(monitor); // THIS sets the ID via @MapsId
                    s.setTotalChecks(0);
                    s.setTotalUp(0);
                    s.setTotalDown(0);
                    s.setUptimePercentage(100.0);
                    return s;
                });


        status.setTotalChecks(status.getTotalChecks() + 1);

        if (isUp) {
            status.setTotalUp(status.getTotalUp() + 1);
        } else {
            status.setTotalDown(status.getTotalDown() + 1);
            status.setLastDowntimeAt(LocalDateTime.now());
        }

        double uptime =
                (status.getTotalUp() * 100.0) / status.getTotalChecks();

        status.setUptimePercentage(uptime);
        status.setUpdatedAt(LocalDateTime.now());

        monitor.setNextCheckAt(
                LocalDateTime.now().plus(
                        monitor.getIntervalMilliseconds(),
                        ChronoUnit.MILLIS
                )
        );



        monitor.setNextCheckAt(
                LocalDateTime.now().plus(
                        monitor.getIntervalMilliseconds(),
                        ChronoUnit.MILLIS
                )
        );
        monitorStatusRepository.save(status);
        monitorRepository.save(monitor);

    }


}
