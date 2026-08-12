ALTER TABLE admin ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE admin ADD COLUMN IF NOT EXISTS password_configured BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE admin ADD COLUMN IF NOT EXISTS invited_at TIMESTAMP;
ALTER TABLE admin ADD COLUMN IF NOT EXISTS invited_by BIGINT REFERENCES admin(id) ON DELETE SET NULL;
ALTER TABLE admin ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;
ALTER TABLE admin ADD COLUMN IF NOT EXISTS session_version INTEGER NOT NULL DEFAULT 0;

UPDATE admin SET status = 'ACTIVE', password_configured = TRUE,
                 activated_at = COALESCE(activated_at, created_at);

CREATE TABLE IF NOT EXISTS admin_password_token (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES admin(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_password_token_admin_purpose
    ON admin_password_token(admin_id, purpose);
CREATE INDEX IF NOT EXISTS idx_admin_password_token_expiry
    ON admin_password_token(expires_at);
