package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.WebhookOutbox;
import com.heartbeat.ping.modles.WebhookStatus;
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
public interface WebhookOutboxRepository extends JpaRepository<WebhookOutbox, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    /**
     * Claims a batch of sendable webhooks (PENDING and due) with {@code FOR UPDATE SKIP LOCKED},
     * mirroring {@code EmailOutboxRepository.claimSendable} so concurrent workers never collide.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select w from WebhookOutbox w
            where w.status = :status and w.nextAttemptAt <= :now
            order by w.nextAttemptAt asc
            """)
    List<WebhookOutbox> claimSendable(@Param("status") WebhookStatus status,
                                      @Param("now") Instant now,
                                      Pageable pageable);
}
