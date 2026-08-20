-- Password reset tokens for forgot-password flow
-- Tokens are single-use, expire after 1 hour, and stored hashed for security

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_token_hash ON password_reset_token(token_hash);
CREATE INDEX idx_password_reset_token_expires ON password_reset_token(expires_at);
