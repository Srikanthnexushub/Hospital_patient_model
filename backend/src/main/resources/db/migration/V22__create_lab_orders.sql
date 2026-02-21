-- V22: Lab Orders table for Module 6 Clinical Intelligence
CREATE TABLE IF NOT EXISTS lab_orders (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id        VARCHAR(14)  NOT NULL REFERENCES patients(patient_id),
    appointment_id    VARCHAR(14)  REFERENCES appointments(appointment_id),
    test_name         VARCHAR(200) NOT NULL,
    test_code         VARCHAR(50),
    category          VARCHAR(30)  NOT NULL,
    priority          VARCHAR(20)  NOT NULL DEFAULT 'ROUTINE',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ordered_by        VARCHAR(100) NOT NULL,
    ordered_at        TIMESTAMPTZ  NOT NULL,
    notes             TEXT,
    cancelled_reason  TEXT
);

CREATE INDEX IF NOT EXISTS idx_lab_orders_patient_id
    ON lab_orders (patient_id);

CREATE INDEX IF NOT EXISTS idx_lab_orders_patient_status
    ON lab_orders (patient_id, status);
