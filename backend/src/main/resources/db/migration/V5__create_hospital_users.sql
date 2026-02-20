-- V5: Create hospital_users and staff_id_sequences tables for the Auth Module.
-- Mirrors the structure of V1 (patients) and V2 (patient_id_sequences).
-- No FKs to patient tables — independent domain.

CREATE TABLE hospital_users (
    user_id                 VARCHAR(12)     NOT NULL,
    username                VARCHAR(50)     NOT NULL,
    password_hash           VARCHAR(72)     NOT NULL,
    role                    VARCHAR(20)     NOT NULL,
    email                   VARCHAR(100),
    department              VARCHAR(100),
    status                  VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    failed_attempts         INTEGER         NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(50)     NOT NULL DEFAULT 'SYSTEM',
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(50)     NOT NULL DEFAULT 'SYSTEM',
    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT pk_hospital_users PRIMARY KEY (user_id),
    CONSTRAINT uq_hospital_users_username UNIQUE (username),
    CONSTRAINT chk_hospital_users_role CHECK (role IN ('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')),
    CONSTRAINT chk_hospital_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_hospital_users_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE staff_id_sequences (
    year            INTEGER     NOT NULL,
    last_sequence   INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_staff_id_sequences PRIMARY KEY (year)
);

CREATE INDEX idx_hospital_users_status ON hospital_users (status);
CREATE INDEX idx_hospital_users_role ON hospital_users (role);
CREATE INDEX idx_hospital_users_locked_until ON hospital_users (locked_until) WHERE locked_until IS NOT NULL;

COMMENT ON TABLE hospital_users IS 'Hospital staff accounts. Hard deletes forbidden — use status=INACTIVE.';
COMMENT ON COLUMN hospital_users.user_id IS 'U + YYYY + zero-padded sequence (e.g. U2026001). Immutable after creation.';
COMMENT ON COLUMN hospital_users.password_hash IS 'BCrypt hash (strength 12). Never expose in API responses or logs.';
COMMENT ON COLUMN hospital_users.version IS 'JPA @Version for optimistic locking — prevents lockout counter race conditions.';
COMMENT ON COLUMN hospital_users.locked_until IS 'NULL = not locked. Set after 5 consecutive login failures.';

COMMENT ON TABLE staff_id_sequences IS 'Per-year counter for atomic Staff User ID generation. Use SELECT FOR UPDATE to increment.';
