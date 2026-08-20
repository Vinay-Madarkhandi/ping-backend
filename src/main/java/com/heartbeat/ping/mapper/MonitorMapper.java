package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.service.execution.HeartbeatUrlBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MonitorMapper {

    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;

    private final HeartbeatUrlBuilder heartbeatUrlBuilder;

    public Monitor toEntity(CreateMonitorRequestDto monitorRequestDto){
        if (monitorRequestDto.getIntervalMilliseconds() <= 0) {
            throw new IllegalArgumentException("intervalMilliseconds must be greater than 0");
        }

        if (monitorRequestDto.getTimeoutMilliseconds() <= 0) {
            throw new IllegalArgumentException("timeoutMilliseconds must be greater than 0");
        }

        MonitorKind kind = parseKind(monitorRequestDto.getKind());
        boolean heartbeat = kind == MonitorKind.HEARTBEAT;

        if (!heartbeat && (monitorRequestDto.getUrl() == null || monitorRequestDto.getUrl().isBlank())) {
            throw new IllegalArgumentException("url is required for HTTP monitors");
        }

        int gracePeriodMs = monitorRequestDto.getGracePeriodMilliseconds() != null
                ? monitorRequestDto.getGracePeriodMilliseconds() : 0;
        if (gracePeriodMs < 0) {
            throw new IllegalArgumentException("gracePeriodMilliseconds must not be negative");
        }

        return Monitor.builder()
                .name(monitorRequestDto.getName())
                .url(heartbeat ? null : monitorRequestDto.getUrl())
                .intervalMilliseconds(monitorRequestDto.getIntervalMilliseconds())
                .timeoutMilliseconds(monitorRequestDto.getTimeoutMilliseconds())
                .isActive(true)
                .monitorMethod(heartbeat ? null : parseMethod(monitorRequestDto.getMonitorMethod()))
                .kind(kind)
                .heartbeatToken(heartbeat ? newHeartbeatToken() : null)
                .gracePeriodMilliseconds(heartbeat ? gracePeriodMs : 0)
                .expectedStatusCode(heartbeat ? null : monitorRequestDto.getExpectedStatusCode())
                .keyword(heartbeat ? null : monitorRequestDto.getKeyword())
                .followRedirects(monitorRequestDto.getFollowRedirects() == null || monitorRequestDto.getFollowRedirects())
                .customHeaders(heartbeat ? null : monitorRequestDto.getCustomHeaders())
                .tags(normalizeTags(monitorRequestDto.getTags()))
                .nextCheckAt(Instant.now())
                .build();
    }

    /** Parses the monitor kind, defaulting to HTTP. Public so edits could reuse it if kind ever becomes editable. */
    public MonitorKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return MonitorKind.HTTP;
        }
        try {
            return MonitorKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("kind must be HTTP or HEARTBEAT");
        }
    }

    private String newHeartbeatToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String heartbeatUrlFor(Monitor monitor) {
        return monitor.getKind() == MonitorKind.HEARTBEAT && monitor.getHeartbeatToken() != null
                ? heartbeatUrlBuilder.urlFor(monitor.getHeartbeatToken())
                : null;
    }

    /**
     * Trims, drops blanks, dedupes case-insensitively (keeping first-seen casing) and caps the tag
     * set so a monitor can't accumulate unbounded or absurdly long labels.
     */
    public Set<String> normalizeTags(Set<String> rawTags) {
        if (rawTags == null) {
            return new LinkedHashSet<>();
        }

        Set<String> seen = new java.util.HashSet<>();
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (raw == null) continue;
            String tag = raw.trim();
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException("Tags must be at most " + MAX_TAG_LENGTH + " characters");
            }
            if (seen.add(tag.toLowerCase(Locale.ROOT))) {
                normalized.add(tag);
            }
        }

        if (normalized.size() > MAX_TAGS) {
            throw new IllegalArgumentException("A monitor can have at most " + MAX_TAGS + " tags");
        }

        return normalized;
    }

    /** Parses the HTTP method, defaulting to GET. Public so edits reuse identical parsing to creation. */
    public MonitorMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return MonitorMethod.GET;
        }

        try {
            return MonitorMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("monitorMethod must be GET or POST");
        }
    }

    public CreateMonitorResponseDto toResponse(Monitor monitor){
        return CreateMonitorResponseDto.builder()
                .id(monitor.getId().toString())
                .name(monitor.getName())
                .url(monitor.getUrl())
                .isActive(monitor.isActive())
                .createdAt(monitor.getCreatedAt())
                .kind(monitor.getKind().name())
                .heartbeatUrl(heartbeatUrlFor(monitor))
                .build();
    }
}
