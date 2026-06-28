-- When true, the monitor's owner is over their monthly check quota; the scheduler skips it
-- (see MonitorRepository.claimDueMonitors). Cleared automatically at month rollover by the
-- quota enforcement job.
ALTER TABLE monitor
    ADD COLUMN quota_blocked BOOLEAN NOT NULL DEFAULT false;
