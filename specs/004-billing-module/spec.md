# Feature Specification: Billing & Invoicing Module

**Feature Branch**: `004-billing-module`
**Created**: 2026-02-20
**Status**: Draft

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Generate Invoice (Priority: P1)

A receptionist completes a patient visit and immediately creates an invoice listing the services provided — consultation fee, lab tests, medication — along with any applicable discount. The system calculates the total, net, tax, and amount due automatically and returns a uniquely identified invoice ready for the patient.

**Why this priority**: Billing cannot proceed without an invoice. This is the entry point for the entire financial lifecycle and directly enables revenue collection.

**Independent Test**: Create an invoice for a completed appointment with two line items and a 10% discount. Verify the invoiceId is generated, totals are computed correctly, and the invoice is in DRAFT status.

**Acceptance Scenarios**:

1. **Given** a completed appointment exists, **When** a receptionist submits an invoice with at least one line item, **Then** the system creates an invoice with a unique ID, computes all monetary totals correctly (totalAmount, discountAmount, netAmount, taxAmount, amountDue), and returns it in DRAFT status.
2. **Given** an appointment already has an invoice, **When** a receptionist tries to create a second invoice for the same appointment, **Then** the system rejects the request with a clear duplicate error.
3. **Given** an appointment does not exist, **When** a receptionist submits an invoice for that appointmentId, **Then** the system rejects the request with a not-found error.
4. **Given** a NURSE is authenticated, **When** they attempt to create an invoice, **Then** the system denies access.
5. **Given** a line item with quantity 2 and unit price 150.00 and a 10% discount, **When** the invoice is created, **Then** totalAmount = 300.00, discountAmount = 30.00, netAmount = 270.00, amountDue = 270.00 (assuming 0% tax).

---

### User Story 2 — View & Search Invoices (Priority: P1)

Receptionist and admin staff can browse, filter, and view all invoices in the system. Doctors can view invoices only for their own patients. The invoice detail page shows line items, payment history, and current balance.

**Why this priority**: Staff must be able to retrieve and review invoices for patient queries, billing disputes, and daily reconciliation without creating new records.

**Independent Test**: Seed three invoices across two patients and two doctors. Verify filtering by patientId, status, and date range returns the correct subset. Verify a DOCTOR only sees their own patients' invoices.

**Acceptance Scenarios**:

1. **Given** invoices exist, **When** a receptionist searches by patientId, **Then** only invoices for that patient are returned.
2. **Given** invoices with various statuses exist, **When** filtered by status=PAID, **Then** only PAID invoices are returned.
3. **Given** a doctor is authenticated, **When** they list invoices, **Then** only invoices linked to appointments they performed are returned.
4. **Given** a NURSE is authenticated, **When** they request the invoice list, **Then** the system denies access.
5. **Given** an invoiceId, **When** any authorised staff fetches it, **Then** the response includes full line item detail and all recorded payments.

---

### User Story 3 — Record Payment (Priority: P2)

A receptionist receives payment from a patient (cash, card, insurance, bank transfer, or cheque) and records it against the invoice. The system automatically updates the amount paid, amount due, and transitions the invoice status to PARTIALLY_PAID or PAID based on the remaining balance.

**Why this priority**: Payment recording is the revenue collection step. Without it, invoices remain outstanding indefinitely and financial reporting is inaccurate.

**Independent Test**: Create an ISSUED invoice for 300.00. Record a partial payment of 100.00 and verify status becomes PARTIALLY_PAID and amountDue = 200.00. Record a second payment of 200.00 and verify status becomes PAID and amountDue = 0.00.

**Acceptance Scenarios**:

1. **Given** an ISSUED invoice for 300.00, **When** a payment of 100.00 is recorded, **Then** amountPaid = 100.00, amountDue = 200.00, status = PARTIALLY_PAID.
2. **Given** a PARTIALLY_PAID invoice with amountDue = 200.00, **When** a payment of 200.00 is recorded, **Then** amountPaid = 300.00, amountDue = 0.00, status = PAID.
3. **Given** an invoice with amountDue = 50.00, **When** a payment of 100.00 is recorded (overpayment), **Then** amountDue = -50.00, status = PAID, and the overpayment is recorded.
4. **Given** a CANCELLED invoice, **When** a receptionist attempts to record a payment, **Then** the system rejects the request.
5. **Given** a payment is recorded, **Then** an audit log entry captures who recorded it, the amount, method, and timestamp.

---

### User Story 4 — Cancel / Write-off Invoice (Priority: P2)

An admin cancels an invoice that was created in error (from DRAFT or ISSUED), or writes off an uncollectable debt (from ISSUED or PARTIALLY_PAID). Both actions require a mandatory reason and are permanently recorded in the audit log.

**Why this priority**: Financial hygiene requires the ability to correct mistakes and record bad debts. These are administrative escape valves that only admin should hold.

**Independent Test**: Create a DRAFT invoice and cancel it. Create an ISSUED invoice with a partial payment and write it off. Verify both result in immutable audit entries and the invoice cannot be modified further.

**Acceptance Scenarios**:

1. **Given** a DRAFT invoice, **When** an admin cancels it with a reason, **Then** status becomes CANCELLED and an audit entry is created.
2. **Given** an ISSUED invoice, **When** an admin cancels it with a reason, **Then** status becomes CANCELLED.
3. **Given** a PAID invoice, **When** an admin attempts to cancel it, **Then** the system rejects the request (invalid transition).
4. **Given** a PARTIALLY_PAID invoice, **When** an admin writes it off with a reason, **Then** status becomes WRITTEN_OFF and an audit entry is created.
5. **Given** a RECEPTIONIST is authenticated, **When** they attempt to cancel an invoice, **Then** the system denies access.

---

### User Story 5 — Financial Summary Report (Priority: P2)

An admin selects a date range and views a summary of financial performance: total invoiced, total collected, outstanding balances, write-offs, cancellations, overdue count, and a breakdown by payment method.

**Why this priority**: Management visibility into revenue is essential for operations. Without reporting, leadership cannot see daily or monthly financial health.

**Independent Test**: Seed invoices spanning two months with known amounts across all statuses. Query the report for month 1 and verify all aggregate figures match the seeded data exactly.

**Acceptance Scenarios**:

1. **Given** invoices created within a date range, **When** the admin requests the financial report, **Then** the response includes totalInvoiced, totalCollected, totalOutstanding, totalWrittenOff, totalCancelled, and counts by status.
2. **Given** an invoice in ISSUED status with an appointment date before today, **When** the report is generated, **Then** it is counted as overdue.
3. **Given** payments across multiple methods (CASH, CARD, INSURANCE), **When** the report is generated, **Then** byPaymentMethod shows each method's total collected.
4. **Given** a RECEPTIONIST is authenticated, **When** they request the financial report, **Then** the system denies access.
5. **Given** no invoices exist in the requested date range, **When** the admin requests the report, **Then** all values return as zero — not an error.

---

### Edge Cases

- What happens when a line item has quantity 0 or a negative unit price? → System rejects with a validation error.
- What happens if discountPercent exceeds 100? → System rejects; discount cannot exceed total.
- What happens if a payment amount is 0 or negative? → System rejects the payment.
- What happens if the appointment linked to an invoice is subsequently cancelled? → Invoice remains valid; appointment cancellation does not auto-cancel the invoice — admin must action it manually.
- What happens if dateFrom > dateTo in the financial report? → System rejects with a validation error.
- What happens if no invoices exist in the requested date range? → Report returns all zero values.
- Can a PAID invoice be written off? → No; only ISSUED and PARTIALLY_PAID can be written off.
- Can a CANCELLED invoice have its status changed again? → No; CANCELLED and WRITTEN_OFF are terminal states.

---

## Requirements *(mandatory)*

### Functional Requirements

**Invoice Generation (US1)**

- **FR-001**: The system MUST allow RECEPTIONIST and ADMIN to generate an invoice linked to an existing appointment.
- **FR-002**: Each invoice MUST contain at least one line item with description, quantity (positive integer), and unit price (positive decimal).
- **FR-003**: The system MUST automatically compute totalAmount, discountAmount, netAmount, taxAmount, and amountDue from line items and discount.
- **FR-004**: The system MUST enforce one invoice per appointment; duplicate invoice attempts MUST be rejected with a clear error.
- **FR-005**: Invoice identifiers MUST be unique, sequential per year, and human-readable (prefix + year + zero-padded sequence number).
- **FR-006**: A new invoice MUST start in DRAFT status.
- **FR-007**: The system MUST deny invoice creation to NURSE and DOCTOR roles.
- **FR-008**: Tax rate MUST be configurable per deployment without code changes; default is 0%.
- **FR-009**: All monetary amounts MUST be stored and returned with two decimal places; floating-point arithmetic MUST NOT be used for financial calculations.

**Invoice Search & View (US2)**

- **FR-010**: The system MUST allow authorised staff to list invoices with filters: patientId, appointmentId, status, and date range.
- **FR-011**: DOCTOR role MUST only see invoices for appointments where they are the assigned doctor.
- **FR-012**: NURSE role MUST be denied access to all invoice endpoints.
- **FR-013**: Invoice detail MUST include all line items and the complete payment history.
- **FR-014**: The system MUST support listing all invoices for a specific patient, paginated.

**Payment Recording (US3)**

- **FR-015**: The system MUST allow RECEPTIONIST and ADMIN to record a payment against an ISSUED or PARTIALLY_PAID invoice.
- **FR-016**: Each payment MUST include: amount (positive decimal), payment method (one of: CASH, CARD, INSURANCE, BANK_TRANSFER, CHEQUE), and optional reference number and notes.
- **FR-017**: Upon payment, the system MUST update amountPaid and amountDue atomically.
- **FR-018**: The system MUST auto-transition invoice status: amountDue > 0 → PARTIALLY_PAID; amountDue ≤ 0 → PAID.
- **FR-019**: Overpayments MUST be accepted; amountDue may go negative (represents credit to the patient).
- **FR-020**: The system MUST reject payments against DRAFT, CANCELLED, WRITTEN_OFF, or PAID invoices.
- **FR-021**: Every payment MUST produce an immutable audit log entry.

**Cancel / Write-off (US4)**

- **FR-022**: ADMIN MUST be able to cancel an invoice in DRAFT or ISSUED status; reason is mandatory.
- **FR-023**: ADMIN MUST be able to write off an invoice in ISSUED or PARTIALLY_PAID status; reason is mandatory.
- **FR-024**: Cancelled and written-off invoices MUST be retained (no physical deletion); they MUST NOT be further modified.
- **FR-025**: Every status change action MUST produce an immutable audit log entry recording who, what, and when.
- **FR-026**: Non-ADMIN roles MUST be denied access to cancel and write-off operations.

**Financial Report (US5)**

- **FR-027**: The system MUST provide a financial summary report filterable by date range (based on invoice creation date).
- **FR-028**: The report MUST include: totalInvoiced, totalCollected, totalOutstanding, totalWrittenOff, totalCancelled, invoiceCount, paidCount, partialCount, overdueCount, and collected amounts broken down by payment method.
- **FR-029**: An invoice MUST be classified as overdue if its status is ISSUED or PARTIALLY_PAID and the linked appointment date is before today.
- **FR-030**: Only ADMIN role MUST have access to the financial report endpoint.

---

### Key Entities

- **Invoice**: The primary financial document linked one-to-one with an appointment. Tracks monetary totals, current status, and a version counter for concurrency control. Immutable after creation except through defined lifecycle actions.
- **Invoice Line Item**: A single billable service within an invoice (e.g., "General Consultation", "Blood Panel"). An invoice may have multiple line items. Line items are immutable after invoice creation.
- **Invoice Payment**: A recorded payment event against an invoice. Immutable once recorded. Captures amount, payment method, optional reference number, and the staff member who recorded it.
- **Invoice Audit Log**: An immutable record of every state-changing operation on an invoice. Used for compliance, financial audits, and dispute resolution.
- **Invoice ID Sequence**: A per-year counter used to generate sequential, human-readable invoice identifiers unique within a calendar year.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A receptionist can generate a complete multi-item invoice and receive confirmation in under 2 minutes.
- **SC-002**: Invoice list and search results load within 1 second for datasets up to 10,000 invoices with active filters applied.
- **SC-003**: Payment recording updates invoice balance and status within the same interaction — no manual refresh required.
- **SC-004**: The financial summary report for any 30-day range returns within 2 seconds regardless of invoice volume.
- **SC-005**: 100% of invoice mutations (create, payment, status change) produce an audit log entry with no silent failures.
- **SC-006**: Duplicate invoice prevention is enforced: billing the same appointment twice always fails with a clear, actionable error message.
- **SC-007**: All monetary calculations are accurate to 2 decimal places across any combination of line items, discounts, and tax rates.
- **SC-008**: Role enforcement is consistent across all endpoints: NURSE denied entirely; DOCTOR restricted to own patients; cancel/write-off/report restricted to ADMIN only.

---

## Assumptions

- Tax rate defaults to 0% and is configured at the deployment level, not per-invoice.
- Invoices may be created for appointments in any non-cancelled status (COMPLETED or IN_PROGRESS), to support billing during long procedures.
- All amounts are in the hospital's local currency; no multi-currency support is in scope.
- Line items are immutable after invoice creation; corrections require cancelling and re-creating the invoice.
- Overdue classification is based on the linked appointment date, not the invoice creation date.
- Invoice list date filter applies to invoice creation date; overdue logic uses appointment date.
- The financial report date range filter applies to invoice creation date.
- Payment against a DRAFT invoice is not allowed; the invoice must be ISSUED first before accepting payments.

---

## Dependencies

- **Module 1 (Patient)**: Patient entity provides patientId and patient name for invoice display and filtering.
- **Module 2 (Auth)**: JWT authentication and RoleGuard provide RBAC enforcement on all billing endpoints. Existing roles (RECEPTIONIST, DOCTOR, NURSE, ADMIN) are used unchanged.
- **Module 3 (Appointment Scheduling)**: Appointment entity provides the link between a clinical event and its invoice. appointmentId, doctorId, patientId, and appointmentDate are read from the appointments table and must not be modified by this module.
