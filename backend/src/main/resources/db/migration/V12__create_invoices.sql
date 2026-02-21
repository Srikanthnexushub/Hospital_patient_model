-- V12: Invoice core tables
-- invoices — primary billing document, one per appointment
CREATE TABLE invoices (
    invoice_id        VARCHAR(16)    NOT NULL,
    appointment_id    VARCHAR(14)    NOT NULL,
    patient_id        VARCHAR(12)    NOT NULL,
    doctor_id         VARCHAR(100)   NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    total_amount      NUMERIC(12, 2) NOT NULL,
    discount_percent  NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    discount_amount   NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    tax_rate          NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount        NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    net_amount        NUMERIC(12, 2) NOT NULL,
    amount_due        NUMERIC(12, 2) NOT NULL,
    amount_paid       NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    notes             TEXT,
    cancel_reason     TEXT,
    version           INTEGER        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100)   NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(100)   NOT NULL,

    CONSTRAINT pk_invoices PRIMARY KEY (invoice_id),
    CONSTRAINT uq_invoices_appointment_id UNIQUE (appointment_id),
    CONSTRAINT fk_invoices_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_invoices_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id),
    CONSTRAINT chk_discount_percent CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT chk_amount_paid_positive CHECK (amount_paid >= 0),
    CONSTRAINT chk_total_amount_positive CHECK (total_amount >= 0)
);

-- invoice_id_sequences — per-year counter for INV ID generation
CREATE TABLE invoice_id_sequences (
    year          INTEGER NOT NULL,
    last_sequence INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_invoice_id_sequences PRIMARY KEY (year)
);
