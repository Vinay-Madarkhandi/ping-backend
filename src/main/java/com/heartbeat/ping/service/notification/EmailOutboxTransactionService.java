package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.modles.EmailOutbox;
import com.heartbeat.ping.modles.EmailStatus;
import com.heartbeat.ping.repository.EmailOutboxRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional boundaries for the outbox worker. Claiming leases rows forward (so a crash mid-send
 * just retries after the lease) and commits before the SMTP send, so no row lock or DB connection
 * is held during network I/O. Finalize runs in its own short transaction.
 */
@Service
@RequiredArgsConstructor
public class EmailOutboxTransactionService {

    private final EmailOutboxRepository outboxRepository;
    private final EmailProperties emailProps;
    private final DatabaseClock clock;

    /** Claims due PENDING emails (SKIP LOCKED), leases each forward by its backoff, returns snapshots. */
    @Transactional
    public List<SendableEmail> claimSendable(int batchSize) {
        Instant now = clock.now();
        List<EmailOutbox> rows = outboxRepository.claimSendable(
                EmailStatus.PENDING, now, PageRequest.of(0, batchSize));

        return rows.stream()
                .map(email -> {
                    email.setNextAttemptAt(now.plus(backoff(email.getAttempts())));
                    return new SendableEmail(email.getId(), email.getRecipient(), email.getSubject(), email.getBody());
                })
                .toList();
    }

    @Transactional
    public void markSent(UUID id) {
        outboxRepository.findById(id).ifPresent(email -> {
            email.setStatus(EmailStatus.SENT);
            email.setSentAt(clock.now());
        });
    }

    /** Records a failed attempt; moves to FAILED (dead-letter) once max attempts are exhausted. */
    @Transactional
    public EmailStatus markFailure(UUID id, String error) {
        EmailOutbox email = outboxRepository.findById(id).orElse(null);
        if (email == null) {
            return EmailStatus.FAILED;
        }
        email.setAttempts(email.getAttempts() + 1);
        email.setLastError(error);
        if (email.getAttempts() >= email.getMaxAttempts()) {
            email.setStatus(EmailStatus.FAILED);
        }
        return email.getStatus();
    }

    /** Exponential backoff from the base delay: base * 2^attempts. */
    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts, 16); // cap the shift to avoid overflow
        return emailProps.getBackoff().multipliedBy(factor);
    }
}
