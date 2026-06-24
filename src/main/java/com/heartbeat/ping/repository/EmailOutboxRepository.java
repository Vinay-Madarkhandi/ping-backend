package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.EmailOutbox;
import com.heartbeat.ping.modles.EmailStatus;
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
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    /**
     * Claims a batch of sendable emails (PENDING and due) with {@code FOR UPDATE SKIP LOCKED}
     * ({@code -2} lock hint), so concurrent outbox workers never grab the same row. The worker
     * leases each row forward before sending, so a crash mid-send simply retries later.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from EmailOutbox e
            where e.status = :status and e.nextAttemptAt <= :now
            order by e.nextAttemptAt asc
            """)
    List<EmailOutbox> claimSendable(@Param("status") EmailStatus status,
                                    @Param("now") Instant now,
                                    Pageable pageable);
}
