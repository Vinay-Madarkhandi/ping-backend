package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class MonitorMapper {

    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;

    public Monitor toEntity(CreateMonitorRequestDto monitorRequestDto){
        if (monitorRequestDto.getIntervalMilliseconds() <= 0) {
            throw new IllegalArgumentException("intervalMilliseconds must be greater than 0");
        }

        if (monitorRequestDto.getTimeoutMilliseconds() <= 0) {
            throw new IllegalArgumentException("timeoutMilliseconds must be greater than 0");
        }

        return Monitor.builder()
                .name(monitorRequestDto.getName())
                .url(monitorRequestDto.getUrl())
                .intervalMilliseconds(monitorRequestDto.getIntervalMilliseconds())
                .timeoutMilliseconds(monitorRequestDto.getTimeoutMilliseconds())
                .isActive(true)
                .monitorMethod(parseMethod(monitorRequestDto.getMonitorMethod()))
                .expectedStatusCode(monitorRequestDto.getExpectedStatusCode())
                .keyword(monitorRequestDto.getKeyword())
                .followRedirects(monitorRequestDto.getFollowRedirects() == null || monitorRequestDto.getFollowRedirects())
                .customHeaders(monitorRequestDto.getCustomHeaders())
                .tags(normalizeTags(monitorRequestDto.getTags()))
                .nextCheckAt(Instant.now())
                .build();
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
                .build();
    }
}
