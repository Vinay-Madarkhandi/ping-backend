-- Revoked tokens table for real logout
-- Stores JWT IDs (jti) of revoked tokens until their natural expiry

CREATE TABLE revoked_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti VARCHAR(255) NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_revoked_token_jti ON revoked_token(jti);
CREATE INDEX idx_revoked_token_expires ON revoked_token(expires_at);
