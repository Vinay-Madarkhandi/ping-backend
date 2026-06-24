-- Consecutive successes counter for the configurable recovery threshold (DOWN -> UP).
ALTER TABLE monitor_status ADD COLUMN consecutive_successes INTEGER NOT NULL DEFAULT 0;
