-- V6: Create token_blacklist table for JWT revocation (logout / explicit invalidation).
-- No FK to hospital_users intentionally (retention independence, same principle as audit logs).

CREATE TABLE token_blacklist (
    jti             VARCHAR(36)     NOT NULL,
    user_id         VARCHAR(12)     NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_token_blacklist PRIMARY KEY (jti)
);

CREATE INDEX idx_token_blacklist_expires_at ON token_blacklist (expires_at);
CREATE INDEX idx_token_blacklist_user_id ON token_blacklist (user_id);

COMMENT ON TABLE token_blacklist IS 'Revoked JWT entries. Checked on every authenticated request. Purge entries where expires_at < NOW().';
COMMENT ON COLUMN token_blacklist.jti IS 'UUID4 from the JWT jti claim. Primary key for O(1) lookup.';
COMMENT ON COLUMN token_blacklist.expires_at IS 'Copied from JWT exp claim. Used by cleanup job to purge stale entries.';
