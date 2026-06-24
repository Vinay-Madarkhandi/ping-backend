-- Alert-engine state machine fields on the per-monitor status row.
-- One statement per column for PostgreSQL (prod) and H2 (tests) compatibility.
ALTER TABLE monitor_status ADD COLUMN current_state VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE monitor_status ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0;
ALTER TABLE monitor_status ADD COLUMN down_since TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE monitor_status ADD COLUMN last_alert_sent_at TIMESTAMP WITHOUT TIME ZONE;
