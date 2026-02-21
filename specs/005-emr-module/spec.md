# Feature Specification: Electronic Medical Records (EMR) Module

**Feature Branch**: `005-emr-module`
**Created**: 2026-02-20
**Status**: Draft
**Company**: Ai Nexus — Hospital Management System

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Record & View Vitals (Priority: P1)

A nurse or doctor records a patient's physiological measurements (blood pressure, heart rate, temperature, weight, height, oxygen saturation, respiratory rate) at the start of each appointment. The recorded vitals are immediately visible to the clinical team and form part of the patient's ongoing health record.

**Why this priority**: Vitals are the first clinical action taken at every appointment. Without them, clinical staff cannot track trends in patient health, detect deterioration, or correlate symptoms with measurements. This is the most frequent data-entry task across NURSE and DOCTOR roles.

**Independent Test**: A nurse records vitals for a booked appointment and a doctor retrieves those vitals immediately — delivering observable clinical data without any other EMR feature being present.

**Acceptance Scenarios**:

1. **Given** an active appointment exists, **When** a nurse records blood pressure 120/80, heart rate 72, and temperature 37.0°C, **Then** the system saves the record and returns all entered values confirmed.
2. **Given** vitals have already been recorded for an appointment, **When** the same or another clinician submits new measurements, **Then** the existing record is replaced with the new values (upsert).
3. **Given** a nurse submits a vitals record with no fields populated, **When** the record is submitted, **Then** the system rejects it requiring at least one measurement.
4. **Given** vitals are recorded, **When** a doctor views the patient's vitals history, **Then** the last 10 records appear sorted most-recent first, each showing the appointment date it was taken on.
5. **Given** a receptionist attempts to view vitals history, **When** they request the data, **Then** access is denied.
6. **Given** blood pressure diastolic is recorded as greater than systolic, **When** the record is submitted, **Then** the system rejects it with a validation error.
7. **Given** oxygen saturation is provided, **When** the value is outside 0–100, **Then** the system rejects it with a range validation error.

---

### User Story 2 — Problem List (Priority: P1)

A doctor maintains a structured list of a patient's ongoing medical problems — chronic conditions, active diagnoses, and resolved issues. Problems persist across appointments, giving any treating clinician an immediate view of what conditions the patient is managing.

**Why this priority**: A problem list is the cornerstone of continuity of care. Without it, each appointment starts from scratch. NURSE staff need read-only access to understand patient context before a consultation.

**Independent Test**: A doctor adds a problem, updates its status to RESOLVED, and a nurse views the active problem list — delivering persistent clinical context independently of prescriptions or vitals.

**Acceptance Scenarios**:

1. **Given** a patient exists, **When** a doctor adds a problem "Type 2 Diabetes", severity MODERATE, status ACTIVE, **Then** it appears in the patient's problem list with a unique identifier.
2. **Given** an active problem exists, **When** a doctor updates its status to RESOLVED, **Then** the problem is retained in history but excluded from the default active-only view.
3. **Given** a patient has problems of mixed statuses, **When** a nurse filters by ACTIVE, **Then** only active problems are returned.
4. **Given** a receptionist attempts to access the problem list, **When** they request the data, **Then** access is denied.
5. **Given** a nurse attempts to create a problem, **When** the request is submitted, **Then** access is denied — nurses have read-only access.
6. **Given** a problem is updated, **When** the update is saved, **Then** the record reflects who made the change and when.

---

### User Story 3 — Medication List (Priority: P1)

A doctor prescribes medications for a patient, recording the drug name, dosage, frequency, route of administration, and duration. The active medication list is visible to the clinical team at all times, and medications can be discontinued or completed as treatment progresses.

**Why this priority**: Knowing what a patient is currently taking is critical for safe prescribing — drug interactions and contraindications depend on this list. Nurses need visibility to administer medications correctly.

**Independent Test**: A doctor prescribes a medication, a nurse views the active list, and the doctor discontinues it — delivering a complete prescription lifecycle independently of other EMR features.

**Acceptance Scenarios**:

1. **Given** a patient exists, **When** a doctor prescribes "Metformin 500mg twice daily oral" with start date today, **Then** it appears in the active medications list with the prescribing doctor's name auto-populated.
2. **Given** an active medication exists, **When** a doctor sets its status to DISCONTINUED, **Then** it no longer appears in the default active-only view but remains in full history.
3. **Given** a request for medication history is made with status=ALL, **When** the list is returned, **Then** it includes ACTIVE, DISCONTINUED, and COMPLETED medications.
4. **Given** a nurse views the medication list, **When** the list is returned, **Then** all active medications are visible; nurses cannot add or update.
5. **Given** a receptionist attempts to view the medication list, **When** they request the data, **Then** access is denied.
6. **Given** a medication is submitted without a dosage, **When** the record is submitted, **Then** the system rejects it with a validation error.
7. **Given** an endDate is provided that is before the startDate, **When** the record is submitted, **Then** the system rejects it with a validation error.

---

### User Story 4 — Allergy Registry (Priority: P2)

Clinical staff record, view, and deactivate a patient's known allergies — including the substance, type (drug, food, environmental, other), severity, and observed reaction. Allergies are safety-critical and visible to all clinical staff and receptionists in read-only mode.

**Why this priority**: Allergy data is life-critical. It must be captured before medications can be prescribed safely. Receptionists need view access to flag allergies at check-in. This builds on but replaces the free-text allergy field from patient registration with structured, severity-graded records.

**Independent Test**: A nurse records a LIFE_THREATENING penicillin allergy, a receptionist views it at check-in, and a doctor soft-deletes an incorrectly entered allergy — delivering allergy safety visibility independently.

**Acceptance Scenarios**:

1. **Given** a patient exists, **When** a nurse records a DRUG allergy to Penicillin, severity LIFE_THREATENING, reaction "anaphylaxis", **Then** it appears in the patient's allergy list as active.
2. **Given** an allergy entry exists, **When** a doctor deletes it, **Then** it is marked inactive and no longer appears in the default list, but is retained for audit.
3. **Given** a receptionist views a patient's allergies, **When** the data is returned, **Then** all active allergies are visible; receptionists cannot create or delete.
4. **Given** an allergy is submitted without a substance name, **When** the record is submitted, **Then** the system rejects it with a validation error.
5. **Given** an already-inactive allergy is requested for deletion, **When** the request is processed, **Then** the system returns a not-found or already-inactive response.

---

### User Story 5 — Medical Summary (Priority: P2)

A doctor or administrator retrieves a patient's complete clinical snapshot in a single interaction — active problems, active medications, known allergies, the five most recent vitals readings, last visit date, and total visit count. This provides an at-a-glance view before or during a consultation.

**Why this priority**: Doctors need immediate context at the start of a consultation. Without a summary, they must navigate multiple screens. This feature assembles data already captured in US1–US4 into a single response.

**Independent Test**: A doctor retrieves the medical summary for a patient who has at least one problem, one medication, one allergy, and one vitals record — and receives all four in a single response with visit statistics.

**Acceptance Scenarios**:

1. **Given** a patient has active problems, active medications, active allergies, and recorded vitals, **When** a doctor requests the medical summary, **Then** all four categories are returned with last visit date and total visit count.
2. **Given** a patient has no EMR data entered, **When** the summary is requested, **Then** each section returns an empty list and counts show zero — no errors.
3. **Given** a receptionist or nurse requests the medical summary, **When** the request is processed, **Then** access is denied.
4. **Given** the patient has more than five vitals records, **When** the summary is requested, **Then** only the five most recent vitals are included.

---

### Edge Cases

- What happens when vitals are submitted for a non-existent appointment? → System returns not-found error.
- What if a problem's onset date is in the future? → Rejected — onset date cannot be future-dated.
- What if a medication's end date is before its start date? → Rejected with a date-range validation error.
- What if vitals are submitted with only one measurement (e.g., weight only)? → Valid — at least one field is sufficient.
- What if the medical summary is requested for a patient with no EMR data? → Returns empty lists; totalVisits derived from appointment history.
- What if a DOCTOR tries to update another doctor's patient's problem? → Permitted — DOCTOR role is not scoped per-clinician for EMR (contrast with appointments/invoices). Clinical necessity takes precedence.
- What if the same substance allergy is entered twice for the same patient? → Both records are stored; no duplicate check enforced (severity or reaction may differ).

---

## Requirements *(mandatory)*

### Functional Requirements

**Vitals (US1)**

- **FR-001**: The system MUST allow NURSE, DOCTOR, and ADMIN to record vitals against an existing appointment.
- **FR-002**: The system MUST require at least one vital measurement per record; submissions with no measurements MUST be rejected.
- **FR-003**: The system MUST replace (upsert) an existing vitals record when vitals are re-submitted for the same appointment.
- **FR-004**: The system MUST validate that blood pressure diastolic does not exceed systolic, and that oxygen saturation is between 0 and 100.
- **FR-005**: The system MUST return a paginated vitals history per patient sorted most-recent first, defaulting to 10 records per page.
- **FR-006**: The system MUST deny RECEPTIONIST access to vitals data.

**Problem List (US2)**

- **FR-007**: The system MUST allow DOCTOR and ADMIN to create problems with at minimum a title, severity, and status.
- **FR-008**: The system MUST support filtering the problem list by status (ACTIVE, RESOLVED, INACTIVE); the default view shows only ACTIVE.
- **FR-009**: The system MUST allow DOCTOR and ADMIN to update any field of a problem including its status.
- **FR-010**: The system MUST record the creator and last updater with timestamps for every problem.
- **FR-011**: The system MUST deny RECEPTIONIST access to the problem list.

**Medication List (US3)**

- **FR-012**: The system MUST allow DOCTOR and ADMIN to prescribe medications with at minimum name, dosage, frequency, route, and start date.
- **FR-013**: The system MUST auto-populate the prescribedBy field from the authenticated user's identity.
- **FR-014**: The system MUST default the medication list to ACTIVE only; a parameter MUST allow retrieval of full history (ALL statuses).
- **FR-015**: The system MUST allow DOCTOR and ADMIN to update a medication's status to DISCONTINUED or COMPLETED.
- **FR-016**: The system MUST reject a medication record where endDate is provided and falls before startDate.
- **FR-017**: The system MUST deny RECEPTIONIST access to the medication list.

**Allergy Registry (US4)**

- **FR-018**: The system MUST allow DOCTOR, NURSE, and ADMIN to record allergies with at minimum substance, type, severity, and reaction.
- **FR-019**: The system MUST soft-delete allergies (mark inactive) rather than permanently removing them.
- **FR-020**: The system MUST allow RECEPTIONIST read-only access to the allergy list.
- **FR-021**: The system MUST record creator and last updater with timestamps for every allergy record.

**Medical Summary (US5)**

- **FR-022**: The system MUST provide a single response containing active problems, active medications, active allergies, the 5 most recent vitals, last visit date, and total visit count for a patient.
- **FR-023**: The system MUST restrict medical summary access to DOCTOR and ADMIN roles.
- **FR-024**: The system MUST return empty lists (not errors) for any category where no data exists.

**Cross-cutting**

- **FR-025**: The system MUST produce an audit log entry for every create, update, and delete operation across all EMR entities.
- **FR-026**: The system MUST return a not-found response when any EMR operation references a patient or appointment that does not exist.

### Key Entities

- **Vitals Record**: Point-in-time physiological measurements tied to one appointment. Up to 8 measurement types, all optional but at least one required. One record per appointment; re-submission replaces. Linked to both appointment and patient.
- **Problem**: Named medical condition or diagnosis persisting across appointments. Has title, optional ICD code, severity (MILD / MODERATE / SEVERE), and lifecycle status (ACTIVE / RESOLVED / INACTIVE). Belongs to a patient.
- **Medication**: Prescribed drug with dosage, frequency, and administration route. Has start date, optional end date. Status: ACTIVE / DISCONTINUED / COMPLETED. `prescribedBy` auto-set from auth. Belongs to a patient.
- **Allergy**: Recorded sensitivity to a substance, categorised by type and severity with an observed reaction. Soft-deleted when removed. Belongs to a patient.
- **Medical Summary**: A read-only aggregation — not a stored entity — assembled from Problems, Medications, Allergies, Vitals, and Appointments for a given patient.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A nurse can record a full set of vitals for a patient in under 60 seconds.
- **SC-002**: A doctor can view a patient's complete medical summary (problems, medications, allergies, recent vitals) in a single interaction — no more than one navigation step required.
- **SC-003**: All five EMR feature areas are accessible and functional for their respective roles within the same authenticated session.
- **SC-004**: The allergy list for any patient is accessible to reception staff within 5 seconds of patient look-up — enabling pre-consultation safety checks.
- **SC-005**: 100% of create, update, and delete mutations across all EMR entities are traceable via audit records — zero unlogged mutations.
- **SC-006**: Unauthorised access attempts for any of the 5 EMR features return a denial response — no clinical data is ever returned to an unauthorised role.
- **SC-007**: Patients with no EMR data (new patients) return empty results for all summary categories — no errors, no partial failures.

---

## Assumptions & Dependencies

### Assumptions

- Patients are registered before any EMR data is recorded (depends on Module 1).
- Appointments exist before vitals can be recorded — vitals are always tied to an appointment (depends on Module 3).
- The authenticated user's identity for audit fields is available via the existing auth context (depends on Module 2).
- DOCTOR role has access to all patients' EMR data — no per-doctor patient scoping. Clinical necessity overrides the per-doctor scoping used in appointments and billing.
- The free-text `knownAllergies` field from patient registration coexists with the new structured allergy registry; no data migration is performed.
- Vitals units are fixed: temperature in Celsius, weight in kilograms, height in centimetres.

### Dependencies

- **Module 1 (Patient)**: Patient identity and patientId must exist before any EMR record can be created.
- **Module 2 (Auth)**: JWT auth, role enforcement, and authenticated user identity are provided by the existing security layer.
- **Module 3 (Appointments)**: appointmentId must exist for vitals recording; existing clinical notes in appointments are not replaced.
