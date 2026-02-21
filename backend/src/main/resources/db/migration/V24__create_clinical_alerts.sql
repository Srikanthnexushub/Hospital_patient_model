-- V24: Clinical Alerts table for Module 6 Clinical Intelligence
CREATE TABLE IF NOT EXISTS clinical_alerts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       VARCHAR(14)  NOT NULL REFERENCES patients(patient_id),
    alert_type       VARCHAR(40)  NOT NULL,
    severity         VARCHAR(20)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    source           VARCHAR(200) NOT NULL,
    trigger_value    VARCHAR(200),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL,
    acknowledged_at  TIMESTAMPTZ,
    acknowledged_by  VARCHAR(100),
    dismissed_at     TIMESTAMPTZ,
    dismiss_reason   TEXT
);

CREATE INDEX IF NOT EXISTS idx_clinical_alerts_patient_id
    ON clinical_alerts (patient_id);

CREATE INDEX IF NOT EXISTS idx_clinical_alerts_patient_type_status
    ON clinical_alerts (patient_id, alert_type, status);

CREATE INDEX IF NOT EXISTS idx_clinical_alerts_status_severity
    ON clinical_alerts (status, severity);
