CREATE TABLE monitor
(
    id                    BINARY(16)    NOT NULL,
    created_at            datetime      NOT NULL,
    updated_at            datetime      NOT NULL,
    name                  VARCHAR(255)  NOT NULL,
    url                   VARCHAR(2048) NOT NULL,
    interval_milliseconds INT           NOT NULL,
    timeout_milliseconds  INT           NOT NULL,
    is_active             BIT(1)        NOT NULL,
    monitor_method        enum('GET', 'POST') NULL,
    user_id               BINARY(16)    NULL,
    CONSTRAINT pk_monitor PRIMARY KEY (id)
);

CREATE TABLE monitor_logs
(
    id                     BINARY(16)   NOT NULL,
    monitor_id             BINARY(16)   NOT NULL,
    status_code            INT      NOT NULL,
    response_time_in_milli INT      NOT NULL,
    is_up                  BIT(1)   NOT NULL,
    error_message          VARCHAR(255) NULL,
    checked_at             datetime NOT NULL,
    CONSTRAINT pk_monitor_logs PRIMARY KEY (id)
);

CREATE TABLE monitor_status
(
    monitor_id       BINARY(16) NOT NULL,
    total_checks     INT NOT NULL,
    total_up         INT NOT NULL,
    total_down       INT NOT NULL,
    uptime_percentage DOUBLE NULL,
    last_downtime_at datetime NULL,
    updated_at       datetime NULL,
    CONSTRAINT pk_monitor_status PRIMARY KEY (monitor_id)
);

CREATE TABLE users
(
    id            BINARY(16)   NOT NULL,
    created_at    datetime     NOT NULL,
    updated_at    datetime     NOT NULL,
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