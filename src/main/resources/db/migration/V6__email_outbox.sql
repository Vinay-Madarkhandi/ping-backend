-- Durable email outbox. Rows are self-contained (recipient/subject/body denormalized) so the
-- send worker never joins monitor/user and cannot participate in cross-table lock cycles.
CREATE TABLE email_outbox
(
    id              UUID                        NOT NULL,
    monitor_id      UUID,
    recipient       VARCHAR(320)                NOT NULL,
    subject         VARCHAR(255)                NOT NULL,
    body            TEXT                        NOT NULL,
    status          VARCHAR(16)                 NOT NULL,
    attempts        INTEGER                     NOT NULL,
    max_attempts    INTEGER                     NOT NULL,
    next_attempt_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_error      TEXT,
    -- e.g. "<incidentId>:DOWN" / ":RECOVERY"; UNIQUE makes alert enqueue idempotent.
    dedupe_key      VARCHAR(255)                NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    sent_at         TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_email_outbox PRIMARY KEY (id),
    CONSTRAINT uk_email_outbox_dedupe UNIQUE (dedupe_key)
);

-- Supports the sendable claim (status = 'PENDING' AND next_attempt_at <= now()).
CREATE INDEX idx_email_outbox_sendable ON email_outbox (status, next_attempt_at);
