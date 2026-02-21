-- V11: Create clinical_notes table
-- One-to-one relationship with appointments (appointmentId is PK and FK).
-- All text fields are AES-256-GCM encrypted at the application layer (HIPAA).

CREATE TABLE clinical_notes (
    appointment_id    VARCHAR(14)  NOT NULL,
    chief_complaint   TEXT,
    diagnosis         TEXT,
    treatment         TEXT,
    prescription      TEXT,
    follow_up_required BOOLEAN     NOT NULL DEFAULT FALSE,
    follow_up_days    INT,
    private_notes     TEXT,
    created_by        VARCHAR(50)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_clinical_notes        PRIMARY KEY (appointment_id),
    CONSTRAINT fk_clinical_notes_appt   FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
                                        ON DELETE CASCADE
);
