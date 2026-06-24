package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.Monitor;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitorRepository extends JpaRepository<Monitor, UUID> {

    boolean existsByIdAndUser_Id(UUID monitorId, UUID userId);

    List<Monitor> findByUser_Id(UUID userId);

    /** Active (non-archived) monitors for a user. */
    List<Monitor> findByUser_IdAndDeletedAtIsNull(UUID userId);

    Optional<Monitor> findByIdAndUser_Id(UUID monitorId, UUID userId);

    /**
     * Atomically claims a batch of due monitors using {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     * The Hibernate lock-timeout value {@code -2} maps to {@code SKIP LOCKED}, so concurrent
     * pollers (including other instances) never pick the same monitor. Dueness uses the database
     * clock ({@code current_timestamp}) to be immune to cross-node clock skew. Archived
     * ({@code deletedAt != null}) and paused monitors are excluded. Must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from Monitor m
            where m.isActive = true and m.paused = false and m.deletedAt is null
              and m.nextCheckAt <= current_timestamp
            order by m.nextCheckAt asc
            """)
    List<Monitor> claimDueMonitors(Pageable pageable);
}
