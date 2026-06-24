-- Outage incidents. One row per confirmed DOWN period; resolved on recovery.
CREATE TABLE incident
(
    id               UUID                        NOT NULL,
    monitor_id       UUID                        NOT NULL,
    started_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resolved_at      TIMESTAMP WITHOUT TIME ZONE,
    duration_seconds BIGINT,
    failure_reason   TEXT,
    status           VARCHAR(16)                 NOT NULL,
    -- = monitor_id while OPEN, NULL once resolved. A plain UNIQUE on a nullable column
    -- enforces "at most one OPEN incident per monitor" portably (multiple NULLs allowed).
    open_for_monitor UUID,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_incident PRIMARY KEY (id),
    CONSTRAINT uk_incident_open_for_monitor UNIQUE (open_for_monitor)
);

CREATE INDEX idx_incident_monitor_started ON incident (monitor_id, started_at);

ALTER TABLE incident
    ADD CONSTRAINT fk_incident_on_monitor FOREIGN KEY (monitor_id) REFERENCES monitor (id);
