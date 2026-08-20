package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MonitorStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitorStatusRepository extends JpaRepository<MonitorStatus, UUID> {

    /**
     * Loads the status row with a pessimistic write lock ({@code SELECT ... FOR UPDATE}).
     * This is the linearization point for the alert state machine: concurrent result
     * processing for the same monitor serializes here, so transitions apply in a total order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitorStatus s where s.monitorId = :monitorId")
    Optional<MonitorStatus> findByIdForUpdate(@Param("monitorId") UUID monitorId);

    /**
     * Active monitors whose TLS certificate expires within [now, threshold). Join-fetches the
     * monitor and its owner so the SSL-expiry job can build one alert email per row without N+1s.
     */
    @Query("""
            select s from MonitorStatus s
            join fetch s.monitor m
            join fetch m.user u
            where s.sslCertExpiresAt is not null
              and s.sslCertExpiresAt >= :now
              and s.sslCertExpiresAt < :threshold
              and m.deletedAt is null
              and m.paused = false
            """)
    List<MonitorStatus> findSslExpiringBetween(
            @Param("now") LocalDateTime now, @Param("threshold") LocalDateTime threshold);
}
