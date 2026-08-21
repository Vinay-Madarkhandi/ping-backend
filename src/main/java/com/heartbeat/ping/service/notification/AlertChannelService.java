package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.dto.alertchannel.AlertChannelRequest;
import com.heartbeat.ping.dto.alertchannel.AlertChannelResponse;
import com.heartbeat.ping.modles.AlertChannel;
import com.heartbeat.ping.modles.AlertChannelType;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.AlertChannelRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.ResourceNotFoundException;
import com.heartbeat.ping.service.security.UrlSafetyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-scoped CRUD for alert channels, plus wiring which channels a monitor notifies. Channel
 * target URLs go through the same SSRF guard as monitor URLs — they are just as much an outbound
 * request to a user-supplied address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertChannelService {

    private static final int MAX_CHANNELS_PER_USER = 10;

    private final AlertChannelRepository channelRepository;
    private final MonitorRepository monitorRepository;
    private final UserRepository userRepository;
    private final UrlSafetyValidator urlSafetyValidator;
    private final WebhookDeliveryService webhookDeliveryService;
    private final WebhookPayloadBuilder webhookPayloadBuilder;

    @Transactional
    public AlertChannelResponse create(UUID userId, AlertChannelRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (channelRepository.findByUser_IdOrderByCreatedAtDesc(userId).size() >= MAX_CHANNELS_PER_USER) {
            throw new IllegalArgumentException("A user can have at most " + MAX_CHANNELS_PER_USER + " alert channels");
        }

        AlertChannelType type = parseType(request.getType());
        String targetUrl = request.getTargetUrl().trim();
        urlSafetyValidator.validate(targetUrl);

        AlertChannel channel = AlertChannel.builder()
                .user(user)
                .type(type)
                .name(request.getName().trim())
                .targetUrl(targetUrl)
                .active(true)
                .build();

        return toResponse(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<AlertChannelResponse> list(UUID userId) {
        return channelRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID channelId, UUID userId) {
        AlertChannel channel = channelRepository.findByIdAndUser_Id(channelId, userId)
                .orElseThrow(() -> new AccessDeniedException("Alert channel not found for this user"));
        channelRepository.delete(channel);
    }

    @Transactional(readOnly = true)
    public List<UUID> getMonitorChannelIds(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));
        return monitor.getAlertChannels().stream().map(AlertChannel::getId).toList();
    }

    /** Replaces the full set of channels a monitor notifies. Every id must belong to the caller. */
    @Transactional
    public void setMonitorChannels(UUID monitorId, UUID userId, List<UUID> channelIds) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        if (channelIds == null || channelIds.isEmpty()) {
            monitor.setAlertChannels(new LinkedHashSet<>());
            return;
        }

        List<AlertChannel> owned = channelRepository.findByIdInAndUser_Id(channelIds, userId);
        if (owned.size() != new LinkedHashSet<>(channelIds).size()) {
            throw new AccessDeniedException("One or more alert channels are not owned by this user");
        }

        Set<AlertChannel> channels = new LinkedHashSet<>(owned);
        monitor.setAlertChannels(channels);
    }

    /** Sends a synchronous test message. Returns false (with a message) rather than throwing on delivery failure. */
    @Transactional(readOnly = true)
    public AlertChannelTestResult test(UUID channelId, UUID userId) {
        AlertChannel channel = channelRepository.findByIdAndUser_Id(channelId, userId)
                .orElseThrow(() -> new AccessDeniedException("Alert channel not found for this user"));

        String payload = webhookPayloadBuilder.buildTestPayload(channel.getType(), channel.getName());
        try {
            webhookDeliveryService.deliver(channel.getTargetUrl(), payload);
            return new AlertChannelTestResult(true, "Test alert delivered.");
        } catch (Exception ex) {
            log.info("Alert channel {} test send failed: {}", channelId, ex.getMessage());
            return new AlertChannelTestResult(false, "Delivery failed: " + ex.getMessage());
        }
    }

    public record AlertChannelTestResult(boolean success, String message) {
    }

    private AlertChannelType parseType(String type) {
        try {
            return AlertChannelType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type must be one of WEBHOOK, SLACK, DISCORD");
        }
    }

    private AlertChannelResponse toResponse(AlertChannel channel) {
        return AlertChannelResponse.builder()
                .id(channel.getId())
                .type(channel.getType())
                .name(channel.getName())
                .targetUrl(channel.getTargetUrl())
                .active(channel.isActive())
                .createdAt(channel.getCreatedAt())
                .build();
    }
}
