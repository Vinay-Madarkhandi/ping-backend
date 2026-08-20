package com.heartbeat.ping.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class JwtServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JwtService jwtService;

    @Test
    void tokenExpiryUsesSeconds() {
        String token = jwtService.createToken("user@example.com");

        Date expiration = jwtService.extractExpiration(token);
        long lifetimeMillis = expiration.getTime() - System.currentTimeMillis();

        assertTrue(lifetimeMillis > Duration.ofHours(9).toMillis());
        assertTrue(jwtService.isValid(token));
    }
}
