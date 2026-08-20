-- Heartbeat (cron/job) monitors: an external job pings Ping instead of Ping probing a URL.
-- url is only meaningful for HTTP monitors, so it drops its NOT NULL constraint.
ALTER TABLE monitor ALTER COLUMN url DROP NOT NULL;

ALTER TABLE monitor ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'HTTP';
ALTER TABLE monitor ADD COLUMN heartbeat_token VARCHAR(64);
ALTER TABLE monitor ADD COLUMN grace_period_milliseconds INTEGER NOT NULL DEFAULT 0;

-- Partial unique index (not a table constraint) so multiple HTTP monitors with a null token coexist.
CREATE UNIQUE INDEX idx_monitor_heartbeat_token ON monitor (heartbeat_token) WHERE heartbeat_token IS NOT NULL;
