-- V7: Create auth_audit_log — immutable audit trail for authentication events.
-- No FK to hospital_users intentionally (HIPAA retention independence).
-- Application code MUST NOT issue UPDATE or DELETE on this table.

CREATE TABLE auth_audit_log (
    id              BIGSERIAL       NOT NULL,
    timestamp       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    event_type      VARCHAR(30)     NOT NULL,
    actor_user_id   VARCHAR(12)     NOT NULL,
    target_user_id  VARCHAR(12),
    outcome         VARCHAR(10)     NOT NULL,
    ip_address      VARCHAR(45),
    details         TEXT,

    CONSTRAINT pk_auth_audit_log PRIMARY KEY (id),
    CONSTRAINT chk_auth_audit_event_type CHECK (
        event_type IN (
            'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED',
            'LOGOUT', 'TOKEN_REFRESH',
            'USER_CREATED', 'USER_UPDATED', 'USER_DEACTIVATED'
        )
    ),
    CONSTRAINT chk_auth_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_auth_audit_actor ON auth_audit_log (actor_user_id);
CREATE INDEX idx_auth_audit_timestamp ON auth_audit_log (timestamp DESC);
CREATE INDEX idx_auth_audit_event_type ON auth_audit_log (event_type);

COMMENT ON TABLE auth_audit_log IS 'Immutable audit trail for all auth events. Append-only. Retain per HIPAA policy.';
COMMENT ON COLUMN auth_audit_log.details IS 'Additional context. MUST NOT contain passwords, tokens, PHI, or secrets.';
COMMENT ON COLUMN auth_audit_log.actor_user_id IS 'NOT a FK — ensures audit records survive user account modifications.';
