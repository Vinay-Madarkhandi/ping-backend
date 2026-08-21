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
import com.heartbeat.ping.service.notification.AlertChannelService.AlertChannelTestResult;
import com.heartbeat.ping.service.security.UrlSafetyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertChannelServiceTest {

    @Mock
    private AlertChannelRepository channelRepository;

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UrlSafetyValidator urlSafetyValidator;

    @Mock
    private WebhookDeliveryService webhookDeliveryService;

    @Mock
    private WebhookPayloadBuilder webhookPayloadBuilder;

    private AlertChannelService service() {
        return new AlertChannelService(
                channelRepository, monitorRepository, userRepository, urlSafetyValidator,
                webhookDeliveryService, webhookPayloadBuilder);
    }

    private final UUID userId = UUID.randomUUID();

    @Test
    void createValidatesTargetUrlAndPersists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(channelRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(channelRepository.save(any())).thenAnswer(inv -> {
            AlertChannel channel = inv.getArgument(0);
            channel.setId(UUID.randomUUID());
            return channel;
        });

        AlertChannelRequest request = AlertChannelRequest.builder()
                .type("SLACK")
                .name("Team alerts")
                .targetUrl("https://hooks.slack.com/services/x")
                .build();

        AlertChannelResponse response = service().create(userId, request);

        verify(urlSafetyValidator).validate("https://hooks.slack.com/services/x");
        assertEquals(AlertChannelType.SLACK, response.getType());
        assertEquals("Team alerts", response.getName());
    }

    @Test
    void createRejectsUnknownType() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(channelRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        AlertChannelRequest request = AlertChannelRequest.builder()
                .type("CARRIER_PIGEON")
                .name("x")
                .targetUrl("https://example.com")
                .build();

        assertThrows(IllegalArgumentException.class, () -> service().create(userId, request));
    }

    @Test
    void createRejectsWhenAtChannelCap() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(channelRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.nCopies(10, AlertChannel.builder().build()));

        AlertChannelRequest request = AlertChannelRequest.builder()
                .type("WEBHOOK")
                .name("x")
                .targetUrl("https://example.com")
                .build();

        assertThrows(IllegalArgumentException.class, () -> service().create(userId, request));
    }

    @Test
    void deleteThrowsWhenNotOwnedByCaller() {
        UUID channelId = UUID.randomUUID();
        when(channelRepository.findByIdAndUser_Id(channelId, userId)).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service().delete(channelId, userId));
    }

    @Test
    void setMonitorChannelsRejectsChannelsNotOwnedByCaller() {
        UUID monitorId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Monitor monitor = Monitor.builder().build();
        monitor.setId(monitorId);

        when(monitorRepository.findByIdAndUser_Id(monitorId, userId)).thenReturn(Optional.of(monitor));
        when(channelRepository.findByIdInAndUser_Id(List.of(channelId), userId)).thenReturn(List.of());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service().setMonitorChannels(monitorId, userId, List.of(channelId)));
    }

    @Test
    void testSendReportsFailureInsteadOfThrowing() throws IOException {
        UUID channelId = UUID.randomUUID();
        AlertChannel channel = AlertChannel.builder()
                .type(AlertChannelType.WEBHOOK)
                .name("x")
                .targetUrl("https://example.com/hook")
                .active(true)
                .build();
        channel.setId(channelId);

        when(channelRepository.findByIdAndUser_Id(channelId, userId)).thenReturn(Optional.of(channel));
        when(webhookPayloadBuilder.buildTestPayload(eq(AlertChannelType.WEBHOOK), anyString())).thenReturn("{}");
        doThrow(new IOException("connection refused")).when(webhookDeliveryService).deliver(anyString(), anyString());

        AlertChannelTestResult result = service().test(channelId, userId);

        assertFalse(result.success());
    }
}
