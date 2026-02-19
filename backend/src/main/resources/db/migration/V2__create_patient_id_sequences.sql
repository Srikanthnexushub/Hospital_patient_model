-- V2: Create patient_id_sequences table for atomic year-based ID generation.
-- Row-level pessimistic lock (SELECT FOR UPDATE) ensures no duplicate IDs
-- even under >100,000 concurrent requests/hour.

CREATE TABLE patient_id_sequences (
    year            INTEGER     NOT NULL,
    last_sequence   INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_patient_id_sequences PRIMARY KEY (year)
);

COMMENT ON TABLE patient_id_sequences IS 'Per-year counter for atomic Patient ID generation. Use SELECT FOR UPDATE to increment.';
COMMENT ON COLUMN patient_id_sequences.year IS 'Calendar year (e.g., 2026). PK — one row per year.';
COMMENT ON COLUMN patient_id_sequences.last_sequence IS 'Last assigned sequence number for this year. Incremented atomically via pessimistic lock.';
