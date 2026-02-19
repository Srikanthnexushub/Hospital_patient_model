-- V4: Create performance indexes to support >100,000 requests/hour
-- and ≤ 2 second search SLA across 100,000 patient records.

-- ── patients table indexes ────────────────────────────────────────────────

-- Default list: sorted by registration date descending
CREATE INDEX idx_patients_created_at  ON patients (created_at DESC);

-- Status filter (default: ACTIVE)
CREATE INDEX idx_patients_status      ON patients (status);

-- Name search (ILIKE case-insensitive partial match)
CREATE INDEX idx_patients_last_name   ON patients (last_name);
CREATE INDEX idx_patients_first_name  ON patients (first_name);

-- Phone search + duplicate check
CREATE INDEX idx_patients_phone       ON patients (phone);

-- Optional filters
CREATE INDEX idx_patients_email       ON patients (email);
CREATE INDEX idx_patients_blood_group ON patients (blood_group);
CREATE INDEX idx_patients_gender      ON patients (gender);

-- GIN full-text index on combined first_name + last_name for combined name search
CREATE INDEX idx_patients_full_text ON patients
    USING GIN (to_tsvector('english', first_name || ' ' || last_name));

-- ── patient_audit_log table indexes ──────────────────────────────────────

CREATE INDEX idx_audit_patient_id   ON patient_audit_log (patient_id);
CREATE INDEX idx_audit_performed_by ON patient_audit_log (performed_by);
CREATE INDEX idx_audit_timestamp    ON patient_audit_log (timestamp DESC);
