CREATE TABLE monitor
(
    id                    UUID          NOT NULL,
    created_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    name                  VARCHAR(255)  NOT NULL,
    url                   VARCHAR(2048) NOT NULL,
    interval_milliseconds INTEGER       NOT NULL,
    timeout_milliseconds  INTEGER       NOT NULL,
    is_active             BOOLEAN       NOT NULL,
    next_check_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    monitor_method        VARCHAR(255),
    user_id               UUID,
    CONSTRAINT pk_monitor PRIMARY KEY (id)
);

CREATE TABLE monitor_logs
(
    id                     UUID    NOT NULL,
    monitor_id             UUID    NOT NULL,
    status_code            INTEGER NOT NULL,
    response_time_in_milli INTEGER NOT NULL,
    is_up                  BOOLEAN NOT NULL,
    error_message          VARCHAR(255),
    checked_at             TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_monitor_logs PRIMARY KEY (id)
);

CREATE TABLE monitor_status
(
    monitor_id        UUID             NOT NULL,
    total_checks      INTEGER          NOT NULL,
    total_up          INTEGER          NOT NULL,
    total_down        INTEGER          NOT NULL,
    uptime_percentage DOUBLE PRECISION NOT NULL,
    last_downtime_at  TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_monitor_status PRIMARY KEY (monitor_id)
);

CREATE TABLE users
(
    id            UUID         NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    user_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE INDEX idx_monitorlogs_monitor_time ON monitor_logs (monitor_id, checked_at);

ALTER TABLE monitor_logs
    ADD CONSTRAINT FK_MONITOR_LOGS_ON_MONITOR FOREIGN KEY (monitor_id) REFERENCES monitor (id);

ALTER TABLE monitor
    ADD CONSTRAINT FK_MONITOR_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE monitor_status
    ADD CONSTRAINT FK_MONITOR_STATUS_ON_MONITOR FOREIGN KEY (monitor_id) REFERENCES monitor (id);