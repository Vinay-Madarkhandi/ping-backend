package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonitorStatusRepository extends JpaRepository<MonitorStatus, UUID> {
    Optional<MonitorStatus> findByMonitorId(UUID uuid);
}
