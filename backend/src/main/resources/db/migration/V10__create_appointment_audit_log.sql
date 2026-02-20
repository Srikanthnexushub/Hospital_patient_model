-- V10: Create appointment_audit_log table
-- Immutable history of all appointment state changes (HIPAA audit requirement).
-- No FK to appointments — retained even if appointment record is ever purged.

CREATE TABLE appointment_audit_log (
    id             BIGSERIAL    NOT NULL,
    appointment_id VARCHAR(14)  NOT NULL,
    action         VARCHAR(30)  NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)  NOT NULL,
    performed_by   VARCHAR(50)  NOT NULL,
    performed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    details        TEXT,

    CONSTRAINT pk_appointment_audit_log PRIMARY KEY (id)
);

-- Index for retrieving audit trail for a specific appointment
CREATE INDEX idx_appointment_audit_appointment_id ON appointment_audit_log (appointment_id, performed_at DESC);
