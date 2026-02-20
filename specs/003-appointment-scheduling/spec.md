# Feature Specification: Appointment Scheduling Module

**Feature Branch**: `003-appointment-scheduling`
**Created**: 2026-02-20
**Status**: Draft
**Company**: Ai Nexus — Hospital Management System

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Book Appointment (Priority: P1)

A RECEPTIONIST or ADMIN selects a registered, active patient and an available doctor, enters the appointment date, time, duration, visit type, and reason, then submits the booking. The system validates that no conflicting appointment exists for the selected doctor in that time window, assigns a unique appointment ID, and creates the appointment in SCHEDULED status.

**Why this priority**: Booking is the entry point to the entire scheduling workflow. Without it no other story has data to operate on; it is the core operational function receptionists perform dozens of times daily.

**Independent Test**: Create an appointment via the booking form and verify the system returns a unique appointment ID, the appointment appears in the doctor's schedule, and the patient record shows a pending appointment.

**Acceptance Scenarios**:

1. **Given** an active patient `P2026001` and active doctor `dr.jones`, **When** a RECEPTIONIST books a GENERAL_CONSULTATION for 2026-03-10 at 09:00 for 30 minutes, **Then** the system returns `APT20260001` with status `SCHEDULED`.
2. **Given** doctor `dr.jones` already has a CONFIRMED appointment 09:00–09:30 on 2026-03-10, **When** a RECEPTIONIST attempts to book another 30-minute slot at 09:15, **Then** the system rejects with a conflict error and no appointment is created.
3. **Given** a patient with status `INACTIVE`, **When** a RECEPTIONIST attempts to book, **Then** the system rejects with a patient-not-active error.
4. **Given** a staff account with role `NURSE`, **When** attempting to call the booking endpoint, **Then** the system rejects with a forbidden error.
5. **Given** an invalid `durationMinutes` value of 25, **When** booking, **Then** the system rejects with a validation error listing allowed values.

---

### User Story 2 — View & Search Appointments (Priority: P1)

Authenticated staff can list, filter, and view appointments. A DOCTOR sees only their own appointments; RECEPTIONIST, NURSE, and ADMIN see all. Filters include doctor, patient, exact date, date range, status, and type. A quick "today" shortcut and a per-doctor schedule view support common workflows. Each result includes patient and doctor names alongside all appointment fields.

**Why this priority**: Staff need to see the schedule before they can act on it. Without search/view, the booked appointments are invisible; this story enables every downstream workflow.

**Independent Test**: Book two appointments for different doctors, then confirm that: (a) searching without filters returns both for ADMIN; (b) searching as the first doctor returns only their own appointment.

**Acceptance Scenarios**:

1. **Given** 5 appointments across 3 doctors, **When** ADMIN lists all with no filters, **Then** all 5 are returned sorted by `appointmentDate + startTime` ascending.
2. **Given** the same 5 appointments, **When** DOCTOR `dr.jones` lists appointments, **Then** only appointments where `doctorId = dr.jones` are returned.
3. **Given** appointments on multiple dates, **When** any staff calls the `/today` endpoint, **Then** only appointments for the current calendar date are returned.
4. **Given** appointments with mixed statuses, **When** filtering by `status=CONFIRMED`, **Then** only CONFIRMED appointments are returned.
5. **Given** a valid `appointmentId`, **When** any staff fetches the detail endpoint, **Then** the response includes patient name, doctor name, and all appointment fields including audit timestamps.

---

### User Story 3 — Appointment Status Lifecycle (Priority: P2)

Staff move appointments through a defined state machine: SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED (or to CANCELLED/NO_SHOW at allowed transitions). Each status change is validated against permitted transitions for the actor's role. Every transition produces an immutable audit log entry recording who made the change, from/to status, and timestamp. Cancellation requires a reason.

**Why this priority**: Status management is the operational heartbeat of the module. Once appointments are booked (P1), staff must be able to confirm, check in, start, and complete them; without this the bookings are static and useless.

**Independent Test**: Book an appointment, then walk it through the full happy path SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED as different roles, verifying each state transition succeeds and each produces an audit entry.

**Acceptance Scenarios**:

1. **Given** a SCHEDULED appointment, **When** RECEPTIONIST sends action `CONFIRM`, **Then** status changes to CONFIRMED and an audit entry is written.
2. **Given** a CONFIRMED appointment, **When** RECEPTIONIST sends action `CHECK_IN`, **Then** status changes to CHECKED_IN.
3. **Given** a CHECKED_IN appointment, **When** DOCTOR sends action `START`, **Then** status changes to IN_PROGRESS.
4. **Given** an IN_PROGRESS appointment, **When** DOCTOR sends action `COMPLETE`, **Then** status changes to COMPLETED.
5. **Given** a CONFIRMED appointment, **When** RECEPTIONIST sends action `CANCEL` without a reason, **Then** the system rejects with a validation error.
6. **Given** a CONFIRMED appointment, **When** NURSE attempts action `CONFIRM` (not permitted for NURSE), **Then** the system rejects with a forbidden error.
7. **Given** a COMPLETED appointment, **When** ADMIN sends action `CANCEL` (admin escape hatch), **Then** status changes to CANCELLED.

---

### User Story 4 — Update Appointment Details (Priority: P2)

A RECEPTIONIST or ADMIN can update the date, time, duration, type, reason, or notes of an appointment that is still SCHEDULED or CONFIRMED. Patient and doctor cannot be changed — a new appointment must be created instead. Rescheduling re-runs conflict detection. Optimistic locking via `If-Match` header prevents lost-update races.

**Why this priority**: Corrections and reschedules are a daily reality. This story prevents the anti-pattern of cancelling and re-booking whenever a time changes.

**Independent Test**: Book an appointment at 09:00, then update it to 10:00 and verify the original slot is freed and the new slot is occupied in conflict detection.

**Acceptance Scenarios**:

1. **Given** a SCHEDULED appointment at 09:00, **When** RECEPTIONIST updates `startTime` to 10:00 with a valid `If-Match` version, **Then** the appointment reflects 10:00 and conflict detection clears the old slot.
2. **Given** a target 10:00 slot already taken by another appointment, **When** RECEPTIONIST reschedules to 10:00, **Then** the system rejects with a conflict error.
3. **Given** an IN_PROGRESS appointment, **When** RECEPTIONIST attempts to update, **Then** the system rejects with a status-not-editable error.
4. **Given** a stale version in `If-Match`, **When** RECEPTIONIST updates, **Then** the system rejects with a 409 version-conflict error.
5. **Given** a RECEPTIONIST update request that attempts to change `doctorId`, **Then** the system ignores or rejects the `doctorId` field.

---

### User Story 5 — Doctor Availability (Priority: P2)

A RECEPTIONIST queries a doctor's availability for a specific date. The system divides 08:00–18:00 into 30-minute slots and marks each as AVAILABLE or OCCUPIED. A slot is occupied if any SCHEDULED, CONFIRMED, CHECKED_IN, or IN_PROGRESS appointment overlaps it. The response includes the doctor's name, date, and full slot list with the blocking appointment ID where applicable.

**Why this priority**: Availability lookup is a prerequisite UX accelerator for booking. Without it, receptionists must mentally cross-reference the schedule to find open slots — this story makes booking friction-free.

**Independent Test**: Book a 60-minute appointment at 09:00 and query availability; slots 09:00–09:30 and 09:30–10:00 must both appear as OCCUPIED, all others as AVAILABLE.

**Acceptance Scenarios**:

1. **Given** no appointments on 2026-03-10 for `dr.jones`, **When** RECEPTIONIST checks availability, **Then** all 20 slots (08:00–18:00) are AVAILABLE.
2. **Given** a 60-minute CONFIRMED appointment at 09:00, **When** RECEPTIONIST checks availability, **Then** slots 09:00 and 09:30 are OCCUPIED with the blocking `appointmentId`.
3. **Given** a CANCELLED appointment at 10:00, **When** checking availability, **Then** the 10:00 slot is AVAILABLE (cancelled appointments do not block slots).

---

### User Story 6 — Clinical Notes (Priority: P3)

The assigned DOCTOR (or ADMIN) can record clinical notes — chief complaint, diagnosis, treatment plan, prescription, and follow-up instructions — for an appointment that is IN_PROGRESS or COMPLETED. A `privateNotes` field is visible only to DOCTOR and ADMIN, never to RECEPTIONIST or NURSE. All note fields are encrypted at rest. Notes can be retrieved and updated after creation.

**Why this priority**: Clinical documentation is essential for patient care continuity but is a physician workflow distinct from scheduling mechanics. It requires the status lifecycle (P2) to be complete first.

**Independent Test**: Complete an appointment, then add clinical notes as the assigned DOCTOR. Verify notes can be retrieved by the DOCTOR and ADMIN but that `privateNotes` is absent from the RECEPTIONIST/NURSE response.

**Acceptance Scenarios**:

1. **Given** an IN_PROGRESS appointment, **When** the assigned DOCTOR posts notes, **Then** the notes are stored and retrievable with all fields populated.
2. **Given** a SCHEDULED appointment, **When** DOCTOR attempts to post notes, **Then** the system rejects with a status-not-eligible error.
3. **Given** notes with `privateNotes` set, **When** RECEPTIONIST fetches notes, **Then** `privateNotes` is absent from the response.
4. **Given** notes with `privateNotes` set, **When** ADMIN fetches notes, **Then** `privateNotes` is present in the response.
5. **Given** a different DOCTOR than the assigned one, **When** attempting to post notes, **Then** the system rejects with a forbidden error.

---

### User Story 7 — Patient Appointment History (Priority: P3)

Any authenticated staff member can view the complete appointment history for a specific patient, paginated and sorted by date descending. A DOCTOR sees only appointments they attended; RECEPTIONIST, NURSE, and ADMIN see the full history regardless of doctor.

**Why this priority**: History lookup supports continuity of care and pre-visit preparation. It depends on at least some appointments existing (P1) and ideally notes (P3) for full value.

**Independent Test**: Register a patient, create appointments with two different doctors, then verify: ADMIN sees both; each DOCTOR sees only their own; RECEPTIONIST sees both.

**Acceptance Scenarios**:

1. **Given** a patient with 8 appointments across 3 doctors, **When** ADMIN fetches history page 0 size 5, **Then** 5 appointments are returned sorted by date DESC, with `totalElements=8`.
2. **Given** the same patient, **When** DOCTOR `dr.jones` fetches history, **Then** only appointments where `doctorId=dr.jones` are returned.
3. **Given** a patient with no appointments, **When** any staff fetches history, **Then** an empty page with `totalElements=0` is returned.

---

### Edge Cases

- What happens when a doctor's last appointment is cancelled — do previously OCCUPIED availability slots become AVAILABLE immediately?
- How does conflict detection handle midnight-crossing appointments (e.g., 23:30 for 60 min)? System uses hospital local time; appointments outside 08:00–18:00 are rejected at validation.
- What if two receptionists book the same doctor slot simultaneously? Conflict detection must be atomic (SELECT FOR UPDATE) to prevent double-booking.
- What happens when `followUpDays` is set but `followUpRequired` is false? System must reject — `followUpDays` requires `followUpRequired=true`.
- What if an ADMIN tries to update an appointment that is IN_PROGRESS or COMPLETED? System must reject — only SCHEDULED/CONFIRMED can be updated.
- What if the `appointmentId` sequence rolls over 9999 in a given year? ID format expands naturally (5-digit zero-pad) since `VARCHAR(14)` allows it.

---

## Requirements *(mandatory)*

### Functional Requirements

**Booking**

- **FR-001**: System MUST allow RECEPTIONIST and ADMIN to book appointments for active patients with active doctors.
- **FR-002**: System MUST generate unique appointment IDs in format `APT` + 4-digit year + 4-digit zero-padded sequence per year (e.g., `APT20260001`).
- **FR-003**: System MUST validate that `durationMinutes` is one of: 15, 30, 45, 60, 90, 120.
- **FR-004**: System MUST validate that `appointmentType` is one of: `GENERAL_CONSULTATION`, `FOLLOW_UP`, `SPECIALIST`, `EMERGENCY`, `ROUTINE_CHECKUP`, `PROCEDURE`.
- **FR-005**: System MUST reject booking if the patient's status is not `ACTIVE`.
- **FR-006**: System MUST reject booking if the doctor does not exist or does not have role `DOCTOR` and status `ACTIVE`.
- **FR-007**: System MUST detect and reject overlapping appointments for the same doctor atomically.
- **FR-008**: System MUST set appointment status to `SCHEDULED` on creation.
- **FR-009**: System MUST write an audit log entry for every appointment creation.

**Search & View**

- **FR-010**: System MUST return paginated appointment lists sorted by `appointmentDate + startTime` ascending by default.
- **FR-011**: System MUST support filtering by `doctorId`, `patientId`, `date`, `dateFrom`, `dateTo`, `status`, and `type`.
- **FR-012**: System MUST restrict DOCTOR role to seeing only their own appointments in all list views.
- **FR-013**: System MUST provide a `/today` endpoint returning only today's appointments scoped by the caller's role.
- **FR-014**: System MUST include patient name, doctor name, and audit timestamps in all appointment responses.
- **FR-015**: System MUST support fetching all appointments for a specific doctor on a specific date via `/doctors/{doctorId}/schedule`.

**Status Lifecycle**

- **FR-016**: System MUST enforce the defined state machine transitions and reject invalid transitions with a descriptive error.
- **FR-017**: System MUST check role permissions for each allowed transition and reject unauthorized actors with a forbidden error.
- **FR-018**: System MUST require a non-empty `reason` field when action is `CANCEL`.
- **FR-019**: System MUST allow ADMIN to cancel any appointment regardless of current status.
- **FR-020**: System MUST write an immutable audit log entry for every status change, recording: `appointmentId`, `action`, `fromStatus`, `toStatus`, `performedBy`, `performedAt`.

**Update Details**

- **FR-021**: System MUST allow RECEPTIONIST and ADMIN to update `appointmentDate`, `startTime`, `durationMinutes`, `type`, `reason`, and `notes` when status is `SCHEDULED` or `CONFIRMED`.
- **FR-022**: System MUST reject updates when appointment status is `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`.
- **FR-023**: System MUST ignore attempts to change `patientId` or `doctorId` in update requests.
- **FR-024**: System MUST re-run conflict detection when `appointmentDate`, `startTime`, or `durationMinutes` changes.
- **FR-025**: System MUST implement optimistic locking via `If-Match` header and reject stale versions with HTTP 409.
- **FR-026**: System MUST write an audit log entry for every appointment update.

**Doctor Availability**

- **FR-027**: System MUST return a 30-minute slot grid from 08:00 to 18:00 for the requested doctor and date.
- **FR-028**: System MUST mark a slot as OCCUPIED if any appointment with status `SCHEDULED`, `CONFIRMED`, `CHECKED_IN`, or `IN_PROGRESS` overlaps it.
- **FR-029**: System MUST include the blocking `appointmentId` in OCCUPIED slots.
- **FR-030**: System MUST include the doctor's display name in the availability response.

**Clinical Notes**

- **FR-031**: System MUST restrict note creation/update to the assigned doctor and ADMIN.
- **FR-032**: System MUST reject note creation if appointment status is not `IN_PROGRESS` or `COMPLETED`.
- **FR-033**: System MUST store all clinical note fields encrypted at rest (AES-256 application-level).
- **FR-034**: System MUST exclude `privateNotes` from responses when the caller's role is `RECEPTIONIST` or `NURSE`.
- **FR-035**: System MUST never include clinical note content in application logs.

**Patient History**

- **FR-036**: System MUST return paginated appointment history for a patient sorted by `appointmentDate` descending.
- **FR-037**: System MUST restrict DOCTOR role to seeing only their own appointments in patient history.

### Key Entities

- **Appointment**: Core scheduling record linking a patient and a doctor to a date/time slot. Carries status, type, duration, reason, notes, cancel reason, version for optimistic locking, and full audit timestamps.
- **AppointmentIdSequence**: Per-year counter for generating sequential appointment IDs (mirrors the patient ID sequence pattern).
- **AppointmentAuditLog**: Immutable record of every state change or mutation. Each entry captures action, from/to status, performer, and timestamp — never updated or deleted.
- **ClinicalNotes**: One-to-one extension of Appointment holding structured clinical documentation. Encrypted at rest. Includes follow-up instructions and a private-notes field restricted to privileged roles.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A RECEPTIONIST can complete a full appointment booking (patient search → slot selection → confirmation) in under 90 seconds.
- **SC-002**: The appointment list for a single day loads and renders in under 200 ms at the 95th percentile under normal hospital load.
- **SC-003**: Doctor availability lookup completes in under 100 ms at the 95th percentile.
- **SC-004**: Double-booking is prevented in 100% of concurrent same-slot booking attempts (zero tolerance for scheduling conflicts).
- **SC-005**: Every appointment mutation (create, update, status change, note add) produces exactly one audit log entry — zero omissions.
- **SC-006**: Clinical notes containing `privateNotes` are never visible in any RECEPTIONIST or NURSE response — zero privacy leaks.
- **SC-007**: Walking an appointment from `SCHEDULED` to `COMPLETED` via the defined role sequence requires no manual workaround — 100% of the happy-path transitions are achievable through the UI and API.
- **SC-008**: Cancelled and no-show appointments are retained indefinitely and appear in patient history — zero hard-deletes.
- **SC-009**: All appointment mutations complete within 500 ms at the 95th percentile.
- **SC-010**: The system supports at least 50 concurrent appointment bookings without data corruption or double-booking.

---

## Assumptions

1. The existing `hospital_users` table (Module 2) contains doctor records with `role = DOCTOR` — no separate doctor entity is needed; doctor validation queries this table.
2. All appointment times are in hospital local time — no timezone conversion or UTC normalization is required.
3. The working day for availability purposes is 08:00–18:00; appointments outside this window are rejected at booking time.
4. Appointment ID sequences restart at 0001 each calendar year (same pattern as Patient IDs).
5. A doctor may have at most one active (non-cancelled) appointment overlapping any given minute — back-to-back appointments are allowed but overlapping slots are not.
6. Clinical notes are one note record per appointment (upsert semantics — POST creates or updates).
7. AES-256 encryption key for clinical notes is managed via environment variable — the key is never hardcoded.
8. Flyway migrations V1–V7 are frozen; new tables use V8, V9, V10, V11 (one per new table).
9. The `patientId` referenced in appointments is the generated patient ID (e.g., `P2026001`) from Module 1.
10. `NURSE` role may view appointments and assist with check-in but may not book or cancel.

---

## Dependencies

- **Module 1 (Patient)** — `patients` table and patient status must be queryable; `patientId` is a foreign key.
- **Module 2 (Auth)** — `hospital_users` table provides doctor lookup; `JwtAuthFilter`, `RoleGuard`, and `AuthContext` are used unchanged.
- **Flyway V8+** — four new migration files for `appointments`, `appointment_audit_log`, `clinical_notes`, `appointment_id_sequences`.

---

## Out of Scope

- SMS or email appointment reminders
- Online patient self-booking portal
- Video / telemedicine appointment support
- Multi-location or multi-branch scheduling
- Insurance pre-authorization workflow
- Recurring appointment series
- Waiting-list management
- Resource scheduling (operating rooms, equipment)
