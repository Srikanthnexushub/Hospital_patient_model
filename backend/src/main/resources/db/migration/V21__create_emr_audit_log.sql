-- EMR Module: emr_audit_log table
-- Single audit table for all EMR entities (VITAL, PROBLEM, MEDICATION, ALLERGY).
-- Written with Propagation.MANDATORY — always atomic with the parent entity save.
-- HIPAA: details column stores field names / action context only; no clinical PHI values.

CREATE TABLE emr_audit_log (
    id           BIGSERIAL    NOT NULL,
    entity_type  VARCHAR(20)  NOT NULL,
    entity_id    VARCHAR(50)  NOT NULL,
    patient_id   VARCHAR(14)  NOT NULL,
    action       VARCHAR(30)  NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    details      TEXT,

    CONSTRAINT pk_emr_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_emr_audit_patient_id ON emr_audit_log (patient_id, performed_at DESC);
CREATE INDEX idx_emr_audit_entity     ON emr_audit_log (entity_type, entity_id, performed_at DESC);
