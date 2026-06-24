-- Soft delete / archival: monitors are never hard-deleted in the normal lifecycle, so
-- incident and outage history is preserved. The claim query filters deleted_at IS NULL.
ALTER TABLE monitor ADD COLUMN deleted_at TIMESTAMP WITHOUT TIME ZONE;
