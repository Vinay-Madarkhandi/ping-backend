package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitorLogsRepository extends JpaRepository<MonitorLogs, UUID> {
    List<MonitorLogs> getMonitorLogsByIdIn(Collection<UUID> uuids);

    List<MonitorLogs> findByMonitorIdAndCheckedAtBetween(
            UUID monitorId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<MonitorLogs> findTopByMonitor_IdOrderByCheckedAt(UUID monitorId);

    Page<MonitorLogs> findByMonitorIdOrderByCheckedAtDesc(
            UUID monitorId,
            Pageable pageable
    );

}
