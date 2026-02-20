-- V13: Invoice line items — immutable once inserted
CREATE TABLE invoice_line_items (
    id           BIGINT         GENERATED ALWAYS AS IDENTITY,
    invoice_id   VARCHAR(16)    NOT NULL,
    service_code VARCHAR(20),
    description  TEXT           NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    line_total   NUMERIC(12, 2) NOT NULL,

    CONSTRAINT pk_invoice_line_items PRIMARY KEY (id),
    CONSTRAINT fk_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (invoice_id),
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_unit_price_positive CHECK (unit_price > 0)
);
