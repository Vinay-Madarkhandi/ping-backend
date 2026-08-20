-- Public, unauthenticated status pages: a user picks a subset of their monitors to publish under
-- a shareable slug. status_page_monitor is the selection join table.
CREATE TABLE status_page (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slug        VARCHAR(64) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_status_page_user ON status_page(user_id);
-- Slug already has a UNIQUE constraint (and its backing index) from the column definition above;
-- the public read path looks it up directly, so no separate index is needed.

CREATE TABLE status_page_monitor (
    status_page_id UUID NOT NULL REFERENCES status_page(id) ON DELETE CASCADE,
    monitor_id     UUID NOT NULL REFERENCES monitor(id) ON DELETE CASCADE,
    PRIMARY KEY (status_page_id, monitor_id)
);

CREATE INDEX idx_status_page_monitor_monitor ON status_page_monitor(monitor_id);
