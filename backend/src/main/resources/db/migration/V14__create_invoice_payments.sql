-- V14: Invoice payments — append-only, immutable once recorded
CREATE TABLE invoice_payments (
    id               BIGINT         GENERATED ALWAYS AS IDENTITY,
    invoice_id       VARCHAR(16)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    payment_method   VARCHAR(20)    NOT NULL,
    reference_number VARCHAR(100),
    notes            TEXT,
    paid_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    recorded_by      VARCHAR(100)   NOT NULL,

    CONSTRAINT pk_invoice_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (invoice_id),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0)
);
