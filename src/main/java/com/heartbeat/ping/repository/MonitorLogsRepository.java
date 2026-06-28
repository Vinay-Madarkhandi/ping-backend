package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    Optional<MonitorLogs> findTopByMonitor_IdOrderByCheckedAtDesc(UUID monitorId);

    Page<MonitorLogs> findByMonitorIdOrderByCheckedAtDesc(
            UUID monitorId,
            Pageable pageable
    );

    /** Ordered check times in a window, for detecting coverage gaps (scheduler outages / missing data). */
    @Query("""
            select l.checkedAt from MonitorLogs l
            where l.monitor.id = :monitorId and l.checkedAt between :start and :end
            order by l.checkedAt asc
            """)
    List<LocalDateTime> findCheckTimes(@Param("monitorId") UUID monitorId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /** Bulk-deletes raw logs older than the cutoff (retention purge). */
    @Modifying
    @Query("delete from MonitorLogs l where l.checkedAt < :cutoff")
    int deleteByCheckedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Bulk-deletes raw logs older than the cutoff for monitors owned by users on the given plan. */
    @Modifying
    @Query("""
            delete from MonitorLogs l
            where l.checkedAt < :cutoff
              and l.monitor.id in (select m.id from Monitor m where m.user.plan.id = :planId)
            """)
    int deleteByCheckedAtBeforeForPlan(@Param("cutoff") LocalDateTime cutoff, @Param("planId") UUID planId);

    /** Distinct monitor ids that have any log at or after the cutoff (for daily rollup). */
    @Query("select distinct l.monitor.id from MonitorLogs l where l.checkedAt >= :from and l.checkedAt < :to")
    List<UUID> findMonitorIdsWithLogsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
