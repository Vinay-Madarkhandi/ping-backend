package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.service.execution.HeartbeatUrlBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorMapperTest {

    private final MonitorMapper mapper = new MonitorMapper(new HeartbeatUrlBuilder());

    @Test
    void parsesMethodCaseInsensitively() {
        CreateMonitorRequestDto request = request("post", 60_000, 5_000);

        Monitor monitor = mapper.toEntity(request);

        assertEquals(MonitorMethod.POST, monitor.getMonitorMethod());
    }

    @Test
    void defaultsBlankMethodToGet() {
        CreateMonitorRequestDto request = request(" ", 60_000, 5_000);

        Monitor monitor = mapper.toEntity(request);

        assertEquals(MonitorMethod.GET, monitor.getMonitorMethod());
    }

    @Test
    void rejectsInvalidIntervalAndMethod() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request("GET", 0, 5_000)));
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request("PUT", 60_000, 5_000)));
    }

    @Test
    void normalizesTagsByTrimmingDedupingAndDroppingBlanks() {
        Set<String> raw = new LinkedHashSet<>(Set.of(" prod ", "PROD", "api", ""));

        Set<String> normalized = mapper.normalizeTags(raw);

        assertEquals(2, normalized.size());
        assertTrue(normalized.contains("prod") || normalized.contains("PROD"));
        assertTrue(normalized.contains("api"));
    }

    @Test
    void returnsEmptySetForNullTags() {
        assertTrue(mapper.normalizeTags(null).isEmpty());
    }

    @Test
    void rejectsTooManyTags() {
        Set<String> raw = new LinkedHashSet<>();
        for (int i = 0; i < 11; i++) {
            raw.add("tag" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> mapper.normalizeTags(raw));
    }

    @Test
    void rejectsOverlongTag() {
        Set<String> raw = Set.of("a".repeat(31));

        assertThrows(IllegalArgumentException.class, () -> mapper.normalizeTags(raw));
    }

    @Test
    void httpMonitorRejectsBlankUrl() {
        CreateMonitorRequestDto request = CreateMonitorRequestDto.builder()
                .name("api")
                .intervalMilliseconds(60_000)
                .timeoutMilliseconds(5_000)
                .build();

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    @Test
    void heartbeatMonitorNeedsNoUrlAndGetsAToken() {
        CreateMonitorRequestDto request = CreateMonitorRequestDto.builder()
                .name("nightly-backup")
                .kind("HEARTBEAT")
                .intervalMilliseconds(3_600_000)
                .timeoutMilliseconds(5_000)
                .gracePeriodMilliseconds(60_000)
                .build();

        Monitor monitor = mapper.toEntity(request);

        assertEquals(MonitorKind.HEARTBEAT, monitor.getKind());
        assertNull(monitor.getUrl());
        assertNotNull(monitor.getHeartbeatToken());
        assertEquals(60_000, monitor.getGracePeriodMilliseconds());
        assertFalse(monitor.getHeartbeatToken().isBlank());
    }

    @Test
    void rejectsUnknownKind() {
        CreateMonitorRequestDto request = request("GET", 60_000, 5_000);
        request.setKind("CARRIER_PIGEON");

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    @Test
    void rejectsNegativeGracePeriod() {
        CreateMonitorRequestDto request = CreateMonitorRequestDto.builder()
                .name("job")
                .kind("HEARTBEAT")
                .intervalMilliseconds(60_000)
                .timeoutMilliseconds(5_000)
                .gracePeriodMilliseconds(-1)
                .build();

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    private CreateMonitorRequestDto request(String method, int intervalMilliseconds, int timeoutMilliseconds) {
        return CreateMonitorRequestDto.builder()
                .name("api")
                .url("https://example.com")
                .monitorMethod(method)
                .intervalMilliseconds(intervalMilliseconds)
                .timeoutMilliseconds(timeoutMilliseconds)
                .build();
    }
}
