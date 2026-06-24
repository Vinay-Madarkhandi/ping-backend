package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    /** The single open incident for a monitor, if any (enforced unique by open_for_monitor). */
    Optional<Incident> findByOpenForMonitor(UUID monitorId);

    Page<Incident> findByMonitor_IdOrderByStartedAtDesc(UUID monitorId, Pageable pageable);

    /** Incidents overlapping [start, end): started before the window ends and not resolved before it starts. */
    @Query("""
            select i from Incident i
            where i.monitor.id = :monitorId
              and i.startedAt < :end
              and (i.resolvedAt is null or i.resolvedAt > :start)
            """)
    List<Incident> findOverlapping(@Param("monitorId") UUID monitorId,
                                   @Param("start") Instant start,
                                   @Param("end") Instant end);
}
