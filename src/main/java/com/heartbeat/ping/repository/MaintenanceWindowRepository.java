package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.MaintenanceWindow;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {

    /** All windows for a monitor, most recently scheduled first — history kept, not deleted. */
    List<MaintenanceWindow> findByMonitor_IdOrderByStartsAtDesc(UUID monitorId);

    Optional<MaintenanceWindow> findByIdAndMonitor_Id(UUID id, UUID monitorId);

    /** Upcoming or currently-active windows count, for the per-monitor cap. */
    long countByMonitor_IdAndEndsAtAfter(UUID monitorId, Instant now);

    /**
     * Claims windows whose start time has arrived but haven't been applied yet, with
     * {@code FOR UPDATE SKIP LOCKED} so concurrent worker instances never double-apply one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select w from MaintenanceWindow w
            where w.appliedAt is null and w.startsAt <= :now
            order by w.startsAt asc
            """)
    List<MaintenanceWindow> claimDueStarts(@Param("now") Instant now, Pageable pageable);

    /**
     * Claims applied windows whose end time has arrived but haven't been reverted yet.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select w from MaintenanceWindow w
            where w.appliedAt is not null and w.revertedAt is null and w.endsAt <= :now
            order by w.endsAt asc
            """)
    List<MaintenanceWindow> claimDueEnds(@Param("now") Instant now, Pageable pageable);
}
