-- V8: Create appointments table
-- Module 3 — Appointment Scheduling

CREATE TABLE appointments (
    appointment_id   VARCHAR(14)  NOT NULL,
    patient_id       VARCHAR(12)  NOT NULL,
    doctor_id        VARCHAR(12)  NOT NULL,
    appointment_date DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    duration_minutes INT          NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    reason           TEXT,
    notes            TEXT,
    cancel_reason    TEXT,
    version          INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(50)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_appointments PRIMARY KEY (appointment_id),
    CONSTRAINT chk_appointment_duration CHECK (duration_minutes IN (15, 30, 45, 60, 90, 120)),
    CONSTRAINT chk_appointment_status   CHECK (status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT chk_appointment_type     CHECK (type IN ('GENERAL_CONSULTATION','FOLLOW_UP','SPECIALIST','EMERGENCY','ROUTINE_CHECKUP','PROCEDURE')),
    CONSTRAINT chk_end_after_start      CHECK (end_time > start_time)
);

-- Index for conflict detection (doctor + date overlap queries)
CREATE INDEX idx_appointments_doctor_date ON appointments (doctor_id, appointment_date);

-- Index for patient history queries
CREATE INDEX idx_appointments_patient_date ON appointments (patient_id, appointment_date DESC);

-- Index for status filtering
CREATE INDEX idx_appointments_status ON appointments (status);
