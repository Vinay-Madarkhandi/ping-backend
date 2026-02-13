ALTER TABLE monitor
    ADD next_check_at datetime NULL;

ALTER TABLE monitor
    MODIFY next_check_at datetime NOT NULL;


ALTER TABLE monitor_status
    MODIFY uptime_percentage DOUBLE NOT NULL;