-- Email verification system
-- Add email_verified flag to users and create verification tokens table

-- Add email_verified column to users (default false for existing users)
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

-- Email verification tokens table
CREATE TABLE email_verification_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_token_hash ON email_verification_token(token_hash);
CREATE INDEX idx_email_verification_expires ON email_verification_token(expires_at);
