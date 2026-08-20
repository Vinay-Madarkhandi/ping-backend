-- Scheduled maintenance windows: pre-planned spans during which a monitor is automatically
-- paused (skipping checks, alerts, and uptime impact) without the user having to click
-- pause/resume at the exact start/end time.
CREATE TABLE maintenance_window (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monitor_id        UUID NOT NULL REFERENCES monitor(id) ON DELETE CASCADE,
    title             VARCHAR(200),
    starts_at         TIMESTAMP NOT NULL,
    ends_at           TIMESTAMP NOT NULL,
    -- Set once the worker has actually paused the monitor for this window's start.
    applied_at        TIMESTAMP,
    -- True iff this window is the one that paused the monitor (vs. it already being paused for
    -- some other reason) — only that window is allowed to resume it when the window ends.
    paused_by_window  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Set once the worker has resumed the monitor (or determined it wasn't this window's to resume).
    reverted_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_window_monitor ON maintenance_window (monitor_id, starts_at);
-- Partial indexes for the worker's two claim queries (pending starts / pending ends).
CREATE INDEX idx_maintenance_window_pending_start ON maintenance_window (starts_at) WHERE applied_at IS NULL;
CREATE INDEX idx_maintenance_window_pending_end ON maintenance_window (ends_at) WHERE applied_at IS NOT NULL AND reverted_at IS NULL;
