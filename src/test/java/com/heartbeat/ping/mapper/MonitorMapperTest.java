package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonitorMapperTest {

    private final MonitorMapper mapper = new MonitorMapper();

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
