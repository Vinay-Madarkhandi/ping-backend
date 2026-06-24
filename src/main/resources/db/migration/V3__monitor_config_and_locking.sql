-- Per-monitor check configuration plus optimistic-lock column for safe concurrent execution.
-- One statement per column so the script runs on both PostgreSQL (prod) and H2 (tests).
ALTER TABLE monitor ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE monitor ADD COLUMN paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitor ADD COLUMN expected_status_code INTEGER;
ALTER TABLE monitor ADD COLUMN keyword VARCHAR(255);
ALTER TABLE monitor ADD COLUMN follow_redirects BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE monitor ADD COLUMN custom_headers TEXT;

-- Supports the due-monitor claim query (is_active = true AND paused = false AND next_check_at <= now).
CREATE INDEX idx_monitor_due ON monitor (is_active, paused, next_check_at);
