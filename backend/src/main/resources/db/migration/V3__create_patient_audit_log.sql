-- V3: Create patient_audit_log — immutable audit trail.
-- No FK to patients intentionally (HIPAA retention independence).
-- Application code MUST NOT issue UPDATE or DELETE on this table.

CREATE TABLE patient_audit_log (
    id              BIGSERIAL       NOT NULL,
    timestamp       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    operation       VARCHAR(20)     NOT NULL,
    patient_id      VARCHAR(12)     NOT NULL,
    performed_by    VARCHAR(100)    NOT NULL,
    changed_fields  TEXT[],

    CONSTRAINT pk_patient_audit_log PRIMARY KEY (id),
    CONSTRAINT chk_audit_operation CHECK (
        operation IN ('REGISTER', 'UPDATE', 'DEACTIVATE', 'ACTIVATE')
    )
);

COMMENT ON TABLE patient_audit_log IS 'Immutable audit trail for all patient write operations. Append-only. Retain 7 years (HIPAA).';
COMMENT ON COLUMN patient_audit_log.changed_fields IS 'List of field NAMES modified (UPDATE only). No PHI values stored here.';
COMMENT ON COLUMN patient_audit_log.patient_id IS 'NOT a FK — ensures audit records survive even if patient table were modified.';
