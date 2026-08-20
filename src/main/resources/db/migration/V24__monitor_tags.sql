-- Free-form labels a user can attach to a monitor for grouping/filtering (e.g. "prod", "api").
CREATE TABLE monitor_tag (
    monitor_id UUID NOT NULL REFERENCES monitor(id) ON DELETE CASCADE,
    tag        VARCHAR(30) NOT NULL,
    PRIMARY KEY (monitor_id, tag)
);

CREATE INDEX idx_monitor_tag_tag ON monitor_tag(tag);
