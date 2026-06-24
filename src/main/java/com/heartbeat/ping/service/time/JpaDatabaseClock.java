package com.heartbeat.ping.service.time;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Reads {@code current_timestamp} from the database. The JDBC return type varies by vendor
 * (PostgreSQL timestamptz -> OffsetDateTime, H2 -> Timestamp), so the result is normalized
 * defensively to an {@link Instant}.
 */
@Component
public class JpaDatabaseClock implements DatabaseClock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Instant now() {
        Object value = entityManager.createNativeQuery("select current_timestamp").getSingleResult();
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime odt -> odt.toInstant();
            case Timestamp ts -> ts.toInstant();
            case LocalDateTime ldt -> ldt.toInstant(ZoneOffset.UTC);
            case null -> throw new IllegalStateException("Database returned null current_timestamp");
            default -> throw new IllegalStateException("Unexpected current_timestamp type: " + value.getClass());
        };
    }
}
