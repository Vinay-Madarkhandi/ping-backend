-- Daily aggregate stats so historical reporting survives the raw-log purge (30-day retention).
CREATE TABLE monitor_daily_stats
(
    id               UUID             NOT NULL,
    monitor_id       UUID             NOT NULL,
    stat_day         DATE             NOT NULL,
    total_checks     INTEGER          NOT NULL,
    total_up         INTEGER          NOT NULL,
    total_down       INTEGER          NOT NULL,
    avg_response_ms  DOUBLE PRECISION NOT NULL,
    downtime_seconds BIGINT           NOT NULL,
    CONSTRAINT pk_monitor_daily_stats PRIMARY KEY (id),
    CONSTRAINT uk_daily_stats_monitor_day UNIQUE (monitor_id, stat_day)
);

CREATE INDEX idx_daily_stats_monitor ON monitor_daily_stats (monitor_id, stat_day);
