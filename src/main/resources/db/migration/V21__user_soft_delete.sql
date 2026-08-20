-- Add soft delete support for users
-- When deleted, user is marked with deleted_at timestamp and email is anonymized

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
