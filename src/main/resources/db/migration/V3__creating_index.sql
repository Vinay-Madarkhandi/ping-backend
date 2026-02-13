CREATE INDEX idx_monitor_due
    ON monitor (is_active, next_check_at);
