-- V1: Create patients table
-- All PHI fields stored here; no hard deletes ever permitted on this table.

CREATE TABLE patients (
    patient_id                      VARCHAR(12)     NOT NULL,
    first_name                      VARCHAR(50)     NOT NULL,
    last_name                       VARCHAR(50)     NOT NULL,
    date_of_birth                   DATE            NOT NULL,
    gender                          VARCHAR(10)     NOT NULL,
    blood_group                     VARCHAR(10)     NOT NULL DEFAULT 'UNKNOWN',
    phone                           VARCHAR(20)     NOT NULL,
    email                           VARCHAR(100),
    address                         VARCHAR(200),
    city                            VARCHAR(100),
    state                           VARCHAR(100),
    zip_code                        VARCHAR(20),
    emergency_contact_name          VARCHAR(100),
    emergency_contact_phone         VARCHAR(20),
    emergency_contact_relationship  VARCHAR(50),
    known_allergies                 TEXT,
    chronic_conditions              TEXT,
    status                          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(100)    NOT NULL,
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by                      VARCHAR(100)    NOT NULL,
    version                         INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT pk_patients PRIMARY KEY (patient_id),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_patients_blood_group CHECK (
        blood_group IN ('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG','O_POS','O_NEG','UNKNOWN')
    )
);

COMMENT ON TABLE patients IS 'Primary patient record store. Hard deletes forbidden. Use status=INACTIVE for soft delete.';
COMMENT ON COLUMN patients.patient_id IS 'P + YYYY + zero-padded sequence. Immutable after creation.';
COMMENT ON COLUMN patients.version IS 'JPA @Version field for optimistic locking.';
COMMENT ON COLUMN patients.created_at IS 'Immutable — never updated after INSERT.';
