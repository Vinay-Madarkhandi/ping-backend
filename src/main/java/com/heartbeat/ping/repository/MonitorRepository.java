package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.Monitor;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /** Number of active (non-archived) monitors a user owns — enforces the plan's max-monitors limit. */
    long countByUser_IdAndDeletedAtIsNull(UUID userId);

    /**
     * Atomically claims a batch of due monitors using {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     * The Hibernate lock-timeout value {@code -2} maps to {@code SKIP LOCKED}, so concurrent
     * pollers (including other instances) never pick the same monitor. Dueness uses the database
     * clock ({@code current_timestamp}) to be immune to cross-node clock skew. Archived
     * ({@code deletedAt != null}), paused and quota-blocked monitors are excluded. Must run inside
     * a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from Monitor m
            where m.isActive = true and m.paused = false and m.deletedAt is null
              and m.quotaBlocked = false
              and m.nextCheckAt <= current_timestamp
            order by m.nextCheckAt asc
            """)
    List<Monitor> claimDueMonitors(Pageable pageable);

    /** Quota-blocks all monitors owned by the given (over-quota) users. */
    @Modifying(clearAutomatically = true)
    @Query("update Monitor m set m.quotaBlocked = true where m.quotaBlocked = false and m.user.id in :userIds")
    int blockForUsers(@Param("userIds") Collection<UUID> userIds);

    /** Clears quota-block on monitors whose owners are no longer over quota. */
    @Modifying(clearAutomatically = true)
    @Query("update Monitor m set m.quotaBlocked = false where m.quotaBlocked = true and m.user.id not in :userIds")
    int unblockExceptUsers(@Param("userIds") Collection<UUID> userIds);

    /** Clears quota-block on every monitor (used when no user is over quota). */
    @Modifying(clearAutomatically = true)
    @Query("update Monitor m set m.quotaBlocked = false where m.quotaBlocked = true")
    int unblockAll();

    /** Clears quota-block for a single user's monitors (e.g. immediately after a plan upgrade). */
    @Modifying
    @Query("update Monitor m set m.quotaBlocked = false where m.quotaBlocked = true and m.user.id = :userId")
    int clearQuotaBlockForUser(@Param("userId") UUID userId);

    long countByQuotaBlockedTrue();
}
