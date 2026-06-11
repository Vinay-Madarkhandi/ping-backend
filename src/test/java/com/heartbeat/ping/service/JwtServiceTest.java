package com.heartbeat.ping.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JwtServiceTest {

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
