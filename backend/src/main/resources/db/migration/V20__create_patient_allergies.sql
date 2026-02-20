-- EMR Module: patient_allergies table
-- Soft-delete only (active = false). Records are never hard-deleted (safety + audit).

CREATE TABLE patient_allergies (
    id          UUID            NOT NULL,
    patient_id  VARCHAR(14)     NOT NULL,
    substance   VARCHAR(200)    NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    severity    VARCHAR(20)     NOT NULL,
    reaction    TEXT            NOT NULL,
    onset_date  DATE,
    notes       TEXT,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT pk_patient_allergies PRIMARY KEY (id)
);

CREATE INDEX idx_patient_allergies_patient_id ON patient_allergies (patient_id, active);
