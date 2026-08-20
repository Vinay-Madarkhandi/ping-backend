package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.dto.alerts.AlertResponse;
import com.heartbeat.ping.repository.EmailOutboxRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read model over the durable outbox, exposing whether alerts were actually delivered.
 *
 * <p>Delivery state was previously invisible to users and only semi-visible to operators (a Prometheus
 * counter), so a customer could miss an outage notice without anyone knowing. Reading the outbox
 * directly means no extra bookkeeping: it already records recipient, attempts, status and last error.
 */
@Service
@RequiredArgsConstructor
public class AlertHistoryService {

    private final EmailOutboxRepository outboxRepository;
    private final MonitorRepository monitorRepository;

    @Transactional(readOnly = true)
    public Page<AlertResponse> forMonitor(UUID monitorId, UUID userId, Pageable pageable) {
        if (!monitorRepository.existsByIdAndUser_Id(monitorId, userId)) {
            throw new AccessDeniedException("Monitor not found for this user");
        }
        return outboxRepository.findByMonitorIdOrderByCreatedAtDesc(monitorId, pageable)
                .map(AlertResponse::from);
    }

    /**
     * Every alert across the account. Scoped by the caller's monitor ids — including archived ones, so
     * history for an archived monitor does not vanish.
     */
    @Transactional(readOnly = true)
    public Page<AlertResponse> forUser(UUID userId, Pageable pageable) {
        List<UUID> monitorIds = monitorRepository.findIdsByUserId(userId);
        if (monitorIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return outboxRepository.findByMonitorIdInOrderByCreatedAtDesc(monitorIds, pageable)
                .map(AlertResponse::from);
    }
}
