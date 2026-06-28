-- Subscription plans define per-tier usage limits. Reference data, seeded here and editable via SQL.
CREATE TABLE plan
(
    id                   UUID        NOT NULL,
    name                 VARCHAR(50) NOT NULL,
    max_monitors         INTEGER     NOT NULL,
    min_interval_ms      INTEGER     NOT NULL,
    max_timeout_ms       INTEGER     NOT NULL,
    monthly_check_quota  BIGINT      NOT NULL,
    retention_days       INTEGER     NOT NULL,
    alert_cooldown_seconds INTEGER   NOT NULL,
    max_alerts_per_day   INTEGER     NOT NULL,
    CONSTRAINT pk_plan PRIMARY KEY (id),
    CONSTRAINT uk_plan_name UNIQUE (name)
);

-- Fixed UUIDs so the seed is deterministic and portable across Postgres and H2 (test mode).
INSERT INTO plan (id, name, max_monitors, min_interval_ms, max_timeout_ms, monthly_check_quota,
                  retention_days, alert_cooldown_seconds, max_alerts_per_day)
VALUES ('00000000-0000-0000-0000-000000000001', 'FREE', 5, 300000, 5000, 50000, 7, 3600, 50),
       ('00000000-0000-0000-0000-000000000002', 'PRO', 50, 60000, 10000, 1000000, 90, 900, 500);

-- Every user belongs to exactly one plan; existing users are backfilled to FREE.
ALTER TABLE users
    ADD COLUMN plan_id UUID;

UPDATE users
SET plan_id = (SELECT id FROM plan WHERE name = 'FREE')
WHERE plan_id IS NULL;

ALTER TABLE users
    ALTER COLUMN plan_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_on_plan FOREIGN KEY (plan_id) REFERENCES plan (id);
