package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorPauseWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitorPauseWindowRepository extends JpaRepository<MonitorPauseWindow, UUID> {

    Optional<MonitorPauseWindow> findByOpenForMonitor(UUID monitorId);

    /** Pause windows overlapping [start, end): paused before the window ends and not resumed before it starts. */
    @Query("""
            select w from MonitorPauseWindow w
            where w.monitor.id = :monitorId
              and w.pausedAt < :end
              and (w.resumedAt is null or w.resumedAt > :start)
            """)
    List<MonitorPauseWindow> findOverlapping(@Param("monitorId") UUID monitorId,
                                             @Param("start") Instant start,
                                             @Param("end") Instant end);
}
