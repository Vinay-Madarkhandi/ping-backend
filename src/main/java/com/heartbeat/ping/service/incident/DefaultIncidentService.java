package com.heartbeat.ping.service.incident;

import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.IncidentStatus;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultIncidentService implements IncidentService {

    private final IncidentRepository incidentRepository;

    @Override
    public Incident open(Monitor monitor, String failureReason, Instant startedAt) {
        Incident incident = Incident.builder()
                .monitor(monitor)
                .startedAt(startedAt)
                .status(IncidentStatus.OPEN)
                .openForMonitor(monitor.getId()) // UNIQUE -> at most one open per monitor
                .failureReason(failureReason)
                .build();
        return incidentRepository.save(incident);
    }

    @Override
    public Optional<Incident> resolve(UUID monitorId, Instant resolvedAt) {
        return incidentRepository.findByOpenForMonitor(monitorId).map(incident -> {
            incident.setResolvedAt(resolvedAt);
            incident.setStatus(IncidentStatus.RESOLVED);
            incident.setDurationSeconds(Duration.between(incident.getStartedAt(), resolvedAt).getSeconds());
            incident.setOpenForMonitor(null); // frees the unique slot for a future incident
            return incident;
        });
    }

    @Override
    public Page<Incident> history(UUID monitorId, Pageable pageable) {
        return incidentRepository.findByMonitor_IdOrderByStartedAtDesc(monitorId, pageable);
    }
}
