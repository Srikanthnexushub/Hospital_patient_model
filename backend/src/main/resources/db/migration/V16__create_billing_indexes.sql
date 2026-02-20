-- V16: Billing performance indexes
CREATE INDEX idx_invoices_patient_id     ON invoices (patient_id);
CREATE INDEX idx_invoices_appointment_id ON invoices (appointment_id);
CREATE INDEX idx_invoices_status         ON invoices (status);
CREATE INDEX idx_invoices_created_at     ON invoices (created_at DESC);
CREATE INDEX idx_invoices_doctor_id      ON invoices (doctor_id);

CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items (invoice_id);
CREATE INDEX idx_invoice_payments_invoice_id   ON invoice_payments (invoice_id);
CREATE INDEX idx_invoice_audit_invoice_id      ON invoice_audit_log (invoice_id);
CREATE INDEX idx_invoice_audit_performed_at    ON invoice_audit_log (performed_at DESC);
