-- V23: Lab Results table for Module 6 Clinical Intelligence
CREATE TABLE IF NOT EXISTS lab_results (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID           NOT NULL UNIQUE REFERENCES lab_orders(id),
    patient_id            VARCHAR(14)    NOT NULL REFERENCES patients(patient_id),
    value                 TEXT           NOT NULL,
    unit                  VARCHAR(50),
    reference_range_low   NUMERIC(10,3),
    reference_range_high  NUMERIC(10,3),
    interpretation        VARCHAR(30)    NOT NULL,
    result_notes          TEXT,
    resulted_by           VARCHAR(100)   NOT NULL,
    resulted_at           TIMESTAMPTZ    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lab_results_patient_id
    ON lab_results (patient_id, resulted_at DESC);
