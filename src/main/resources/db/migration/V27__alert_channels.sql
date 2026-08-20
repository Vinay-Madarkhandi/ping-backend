-- Generic webhook/Slack/Discord alert channels, additive to the existing email alerts.
CREATE TABLE alert_channel (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(16) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    target_url  VARCHAR(2048) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_channel_user ON alert_channel(user_id);

-- Which channels a monitor notifies, in addition to the owner's email.
CREATE TABLE monitor_alert_channel (
    monitor_id UUID NOT NULL REFERENCES monitor(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES alert_channel(id) ON DELETE CASCADE,
    PRIMARY KEY (monitor_id, channel_id)
);

CREATE INDEX idx_monitor_alert_channel_channel ON monitor_alert_channel(channel_id);

-- Durable outbox for webhook deliveries, mirroring email_outbox's shape and guarantees.
CREATE TABLE webhook_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monitor_id      UUID,
    channel_id      UUID,
    target_url      VARCHAR(2048) NOT NULL,
    channel_type    VARCHAR(16) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error      TEXT,
    dedupe_key      VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL,
    sent_at         TIMESTAMP
);

CREATE INDEX idx_webhook_outbox_sendable ON webhook_outbox(status, next_attempt_at);
