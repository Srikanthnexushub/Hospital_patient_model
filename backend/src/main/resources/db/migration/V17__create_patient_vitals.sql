-- EMR Module: patient_vitals table
-- One vitals record per appointment (UNIQUE on appointment_id).
-- Re-POST replaces the existing record (upsert handled in service layer).
-- All measurement columns are nullable — at least one non-null enforced at service layer.

CREATE TABLE patient_vitals (
    id                       BIGSERIAL       NOT NULL,
    appointment_id           VARCHAR(14)     NOT NULL,
    patient_id               VARCHAR(14)     NOT NULL,
    blood_pressure_systolic  INT,
    blood_pressure_diastolic INT,
    heart_rate               INT,
    temperature              NUMERIC(4,1),
    weight                   NUMERIC(5,2),
    height                   NUMERIC(5,1),
    oxygen_saturation        INT,
    respiratory_rate         INT,
    recorded_by              VARCHAR(100)    NOT NULL,
    recorded_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_patient_vitals PRIMARY KEY (id),
    CONSTRAINT uq_patient_vitals_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_patient_vitals_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id)
);

CREATE INDEX idx_patient_vitals_patient_id ON patient_vitals (patient_id, recorded_at DESC);
