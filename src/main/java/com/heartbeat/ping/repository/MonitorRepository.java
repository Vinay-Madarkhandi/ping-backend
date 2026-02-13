package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public interface MonitorRepository extends JpaRepository<Monitor, UUID> {

    boolean existsByIdAndUser_Id(UUID monitorId, UUID userId);

    @Query("select m from Monitor m where m.isActive=true and m.nextCheckAt<=:now")
    List<Monitor> findDueMonitors(@Param("now")LocalDateTime now);
}
