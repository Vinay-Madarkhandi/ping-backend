package com.heartbeat.ping.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heartbeat.ping.modles.AlertChannelType;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the outbound JSON body for a webhook delivery, formatted per destination type. */
@Component
@RequiredArgsConstructor
public class WebhookPayloadBuilder {

    private final ObjectMapper objectMapper;

    @SneakyThrows
    public String build(AlertChannelType type, Monitor monitor, Incident incident, AlertType alertType) {
        return switch (type) {
            case WEBHOOK -> objectMapper.writeValueAsString(genericPayload(monitor, incident, alertType));
            case SLACK -> objectMapper.writeValueAsString(Map.of("text", messageFor(monitor, incident, alertType, false)));
            case DISCORD -> objectMapper.writeValueAsString(Map.of("content", messageFor(monitor, incident, alertType, true)));
        };
    }

    /** Sample payload for the "test" button in settings — channel-level, not tied to any monitor. */
    @SneakyThrows
    public String buildTestPayload(AlertChannelType type, String channelName) {
        return switch (type) {
            case WEBHOOK -> objectMapper.writeValueAsString(Map.of(
                    "event", "TEST",
                    "channel", channelName,
                    "message", "This is a test alert from Ping."
            ));
            case SLACK -> objectMapper.writeValueAsString(
                    Map.of("text", ":white_check_mark: Test alert from Ping — \"" + channelName + "\" is wired up correctly."));
            case DISCORD -> objectMapper.writeValueAsString(
                    Map.of("content", "✅ Test alert from Ping — \"" + channelName + "\" is wired up correctly."));
        };
    }

    private Map<String, Object> genericPayload(Monitor monitor, Incident incident, AlertType alertType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", alertType.name());
        payload.put("monitor", Map.of("id", monitor.getId().toString(), "name", monitor.getName(), "url", monitor.getUrl()));
        payload.put("incidentId", incident.getId().toString());
        payload.put("startedAt", incident.getStartedAt().toString());
        if (alertType == AlertType.RECOVERY) {
            payload.put("resolvedAt", incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : null);
            payload.put("durationSeconds", incident.getDurationSeconds());
        } else {
            payload.put("reason", incident.getFailureReason());
        }
        return payload;
    }

    private String messageFor(Monitor monitor, Incident incident, AlertType alertType, boolean discordMarkdown) {
        String bold = discordMarkdown ? "**" : "*";
        if (alertType == AlertType.DOWN) {
            String reason = incident.getFailureReason() != null ? incident.getFailureReason() : "unknown";
            return "🔴 " + bold + monitor.getName() + bold + " is DOWN\n" + monitor.getUrl() + "\nReason: " + reason;
        }
        return "✅ " + bold + monitor.getName() + bold + " has RECOVERED\n" + monitor.getUrl();
    }
}
