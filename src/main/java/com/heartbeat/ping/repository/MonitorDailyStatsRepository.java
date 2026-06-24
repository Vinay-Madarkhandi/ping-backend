package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MonitorDailyStatsRepository extends JpaRepository<MonitorDailyStats, UUID> {

    boolean existsByMonitorIdAndDay(UUID monitorId, LocalDate day);

    /** Days in [from, to) that have a rollup row — used to detect coverage gaps in purged history. */
    @Query("select s.day from MonitorDailyStats s where s.monitorId = :monitorId and s.day >= :from and s.day < :to")
    List<LocalDate> findStatDays(@Param("monitorId") UUID monitorId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
