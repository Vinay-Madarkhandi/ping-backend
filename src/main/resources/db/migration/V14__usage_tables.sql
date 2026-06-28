-- Per-user monthly check consumption, used to enforce the plan's monthly_check_quota.
-- One row per (user, calendar month); incremented atomically as checks are recorded.
CREATE TABLE user_usage
(
    id              UUID   NOT NULL,
    user_id         UUID   NOT NULL,
    period_month    DATE   NOT NULL,
    checks_consumed BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_usage PRIMARY KEY (id),
    CONSTRAINT uk_user_usage_period UNIQUE (user_id, period_month)
);

ALTER TABLE user_usage
    ADD CONSTRAINT fk_user_usage_on_user FOREIGN KEY (user_id) REFERENCES users (id);

-- Per-user daily alert count, used to enforce the plan's max_alerts_per_day cap.
CREATE TABLE user_alert_usage
(
    id          UUID    NOT NULL,
    user_id     UUID    NOT NULL,
    usage_day   DATE    NOT NULL,
    alerts_sent INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_alert_usage PRIMARY KEY (id),
    CONSTRAINT uk_user_alert_usage_day UNIQUE (user_id, usage_day)
);

ALTER TABLE user_alert_usage
    ADD CONSTRAINT fk_user_alert_usage_on_user FOREIGN KEY (user_id) REFERENCES users (id);

-- Leader-lock row for the cluster-singleton quota enforcement job (see DistributedLock).
INSERT INTO scheduler_lock (name, locked_until, locked_by)
VALUES ('quota', TIMESTAMP '1970-01-01 00:00:00', 'none');
