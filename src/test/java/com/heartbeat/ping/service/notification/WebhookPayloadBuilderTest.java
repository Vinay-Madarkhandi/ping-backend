package com.heartbeat.ping.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heartbeat.ping.modles.AlertChannelType;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.IncidentStatus;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookPayloadBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookPayloadBuilder builder = new WebhookPayloadBuilder(objectMapper);

    private Monitor monitor;
    private Incident incident;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("owner@example.com").build();
        monitor = Monitor.builder().name("api.example.com").url("https://api.example.com").user(user).build();
        monitor.setId(UUID.randomUUID());

        incident = Incident.builder()
                .startedAt(Instant.now())
                .failureReason("connect timeout")
                .status(IncidentStatus.OPEN)
                .build();
        incident.setId(UUID.randomUUID());
    }

    @Test
    void genericWebhookPayloadIncludesEventAndMonitor() throws Exception {
        String json = builder.build(AlertChannelType.WEBHOOK, monitor, incident, AlertType.DOWN);

        JsonNode node = objectMapper.readTree(json);
        assertEquals("DOWN", node.get("event").asText());
        assertEquals("api.example.com", node.get("monitor").get("name").asText());
        assertEquals("connect timeout", node.get("reason").asText());
    }

    @Test
    void slackPayloadUsesTextField() throws Exception {
        String json = builder.build(AlertChannelType.SLACK, monitor, incident, AlertType.DOWN);

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.has("text"));
        assertTrue(node.get("text").asText().contains("api.example.com"));
        assertTrue(node.get("text").asText().contains("DOWN"));
    }

    @Test
    void discordPayloadUsesContentField() throws Exception {
        String json = builder.build(AlertChannelType.DISCORD, monitor, incident, AlertType.RECOVERY);

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.has("content"));
        assertTrue(node.get("content").asText().contains("RECOVERED"));
    }

    @Test
    void testPayloadDoesNotRequireAnIncident() throws Exception {
        String json = builder.buildTestPayload(AlertChannelType.WEBHOOK, "My channel");

        JsonNode node = objectMapper.readTree(json);
        assertEquals("TEST", node.get("event").asText());
        assertEquals("My channel", node.get("channel").asText());
    }
}
