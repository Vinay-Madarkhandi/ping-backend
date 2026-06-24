-- Records when a monitor was paused/resumed so paused time can be excluded from uptime denominators.
CREATE TABLE monitor_pause_window
(
    id               UUID                        NOT NULL,
    monitor_id       UUID                        NOT NULL,
    paused_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resumed_at       TIMESTAMP WITHOUT TIME ZONE,
    -- = monitor_id while paused, NULL once resumed; UNIQUE -> at most one open pause window per monitor.
    open_for_monitor UUID,
    CONSTRAINT pk_monitor_pause_window PRIMARY KEY (id),
    CONSTRAINT uk_pause_window_open UNIQUE (open_for_monitor)
);

CREATE INDEX idx_pause_window_monitor ON monitor_pause_window (monitor_id, paused_at);

ALTER TABLE monitor_pause_window
    ADD CONSTRAINT fk_pause_window_on_monitor FOREIGN KEY (monitor_id) REFERENCES monitor (id);
