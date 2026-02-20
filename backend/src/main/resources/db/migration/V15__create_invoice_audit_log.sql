-- V15: Invoice audit log — no FK on invoice_id so audit survives even if invoice is purged
CREATE TABLE invoice_audit_log (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    invoice_id   VARCHAR(16)  NOT NULL,
    action       VARCHAR(30)  NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)  NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    details      TEXT,

    CONSTRAINT pk_invoice_audit_log PRIMARY KEY (id)
);
