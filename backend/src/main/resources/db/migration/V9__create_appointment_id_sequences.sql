-- V9: Create appointment_id_sequences table
-- Mirrors patient_id_sequences pattern for atomic ID generation

CREATE TABLE appointment_id_sequences (
    year          INT NOT NULL,
    last_sequence INT NOT NULL DEFAULT 0,

    CONSTRAINT pk_appointment_id_sequences PRIMARY KEY (year)
);
