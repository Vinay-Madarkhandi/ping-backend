package com.heartbeat.ping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.heartbeat.ping.repository.RevokedTokenRepository;

/**
 * Unit-level check for {@link JwtService}'s startup secret validation — kept separate from
 * {@code JwtServiceTest} (a {@code @SpringBootTest} needing Testcontainers) so it runs anywhere.
 */
@ExtendWith(MockitoExtension.class)
class JwtSecretValidationTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private JwtService serviceWithSecret(String secret) throws Exception {
        JwtService service = new JwtService(revokedTokenRepository);
        Field field = JwtService.class.getDeclaredField("SECRET_KEY");
        field.setAccessible(true);
        field.set(service, secret);
        return service;
    }

    private void validate(JwtService service) throws Exception {
        java.lang.reflect.Method method = JwtService.class.getDeclaredMethod("validateSecret");
        method.setAccessible(true);
        try {
            method.invoke(service);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    @Test
    void rejectsAMissingSecret() throws Exception {
        JwtService service = serviceWithSecret(null);
        assertThrows(IllegalStateException.class, () -> validate(service));
    }

    @Test
    void rejectsABlankSecret() throws Exception {
        JwtService service = serviceWithSecret("   ");
        assertThrows(IllegalStateException.class, () -> validate(service));
    }

    @Test
    void rejectsASecretShorterThan32Bytes() throws Exception {
        JwtService service = serviceWithSecret("too-short");
        assertThrows(IllegalStateException.class, () -> validate(service));
    }

    @Test
    void acceptsASecretOfAtLeast32Bytes() throws Exception {
        JwtService service = serviceWithSecret("this-secret-is-at-least-32-bytes-long");
        assertDoesNotThrow(() -> validate(service));
    }
}
