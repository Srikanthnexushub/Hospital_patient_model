# Feature Specification: Patient Module

**Feature Branch**: `001-patient-module`
**Created**: 2026-02-19
**Status**: Draft
**Version**: 3.0.0
**Company**: Ai Nexus

---

## Overview

The Patient Module is the foundational component of the Hospital Management System.
It enables hospital staff to register new patients, search and retrieve patient records,
view complete patient profiles, update demographic information, and manage patient
active/inactive status. All patient data is classified as Protected Health Information
(PHI) and must be handled with strict access controls and full audit traceability.

This module is a hard dependency for all other hospital modules (Appointments, EMR,
Billing). It does not manage authentication or roles — those are delegated to the Auth
Module.

The system MUST be deployable on local infrastructure using Docker and PostgreSQL,
meet a 99.9% availability SLA, and sustain over 100,000 API requests per hour under
peak hospital operating conditions.

---

## Scope

### In Scope

- Patient registration (create new patient records)
- Patient search and multi-criteria filtering
- Patient profile view
- Patient demographic update
- Patient status management (activate / deactivate — soft delete only)
- Local Docker-based deployment (backend, frontend, database, cache, proxy)
- PostgreSQL as the primary data store
- Enterprise observability (health checks, metrics, structured logging)

### Out of Scope

| Capability | Owning Module |
|---|---|
| User authentication and login | Auth Module |
| Role and permission assignment | Auth Module |
| Appointment scheduling | Appointment Module |
| Medical records / clinical notes | EMR Module |
| Prescriptions and medications | EMR / Pharmacy Module |
| Lab results and imaging | Laboratory / Radiology Module |
| Billing and insurance | Billing Module |
| Patient self-service portal | Patient Portal Module |
| Document upload | Document Module |
| Notifications and reminders | Notification Module |
| Reporting and analytics | Reporting Module |
| Cloud/Kubernetes deployment | Future infrastructure module |

---

## User Roles & Permissions

| Operation | RECEPTIONIST | DOCTOR | NURSE | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Register new patient | ✅ | ❌ | ❌ | ✅ |
| Search patients | ✅ | ✅ | ✅ | ✅ |
| View patient profile | ✅ | ✅ | ✅ | ✅ |
| Update patient demographics | ✅ | ❌ | ❌ | ✅ |
| Deactivate patient | ❌ | ❌ | ❌ | ✅ |
| Activate patient | ❌ | ❌ | ❌ | ✅ |

**Rules**:
- Role is provided by the Auth Module via the authenticated session on every request.
- If a user's role is not one of the four above, all patient module access is denied.
- Attempting any operation without the required role MUST result in an authorization
  error — not a silent redirect.
- Role enforcement MUST occur server-side on every request. Client-side checks are
  cosmetic only and do not constitute a security boundary.

---

## Data Dictionary

### Patient Entity — Complete Field Specification

#### Identity Fields

| Field | Label | Type | Mandatory | Max Length | Rules |
|---|---|---|---|---|---|
| `patientId` | Patient ID | String | Auto-generated | 10 chars | Format: `P` + 4-digit year + 3-digit zero-padded seq (e.g., `P2026001`). Read-only after creation. Never reused. |
| `status` | Status | Enum | Auto-set | — | Values: `ACTIVE`, `INACTIVE`. Default on creation: `ACTIVE`. |

#### Personal Demographics

| Field | Label | Type | Mandatory | Max Length | Validation Rules |
|---|---|---|---|---|---|
| `firstName` | First Name | String | ✅ | 50 chars | Letters, hyphens, apostrophes, and spaces only. Min 1 non-space character. Trimmed before save. |
| `lastName` | Last Name | String | ✅ | 50 chars | Same as firstName. |
| `dateOfBirth` | Date of Birth | Date | ✅ | — | Must be a valid calendar date. Must not be today or a future date. Must not be more than 150 years in the past. Stored as ISO-8601 (`YYYY-MM-DD`). |
| `age` | Age | Integer | Derived | — | Calculated from `dateOfBirth` to current date in years. Never stored — always computed at read time. Read-only on all forms. |
| `gender` | Gender | Enum | ✅ | — | Values: `MALE`, `FEMALE`, `OTHER`. No default — user must explicitly select. |
| `bloodGroup` | Blood Group | Enum | ❌ | — | Values: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, `UNKNOWN`. Defaults to `UNKNOWN` if not selected. |

#### Contact Information

| Field | Label | Type | Mandatory | Max Length | Validation Rules |
|---|---|---|---|---|---|
| `phone` | Phone Number | String | ✅ | 20 chars | Must match one of: `+1-XXX-XXX-XXXX`, `(XXX) XXX-XXXX`, `XXX-XXX-XXXX`. X = digit 0–9. Normalized before storage. |
| `email` | Email Address | String | ❌ | 100 chars | When provided: must match RFC 5322 simplified format. Stored in lowercase. |
| `address` | Street Address | String | ❌ | 200 chars | Free text. No format validation. |
| `city` | City | String | ❌ | 100 chars | Letters, spaces, hyphens only. |
| `state` | State | String | ❌ | 100 chars | Free text (supports international use). |
| `zipCode` | Zip / Postal Code | String | ❌ | 20 chars | Alphanumeric, spaces, and hyphens only. |

#### Emergency Contact

| Field | Label | Type | Mandatory | Max Length | Validation Rules |
|---|---|---|---|---|---|
| `emergencyContactName` | Emergency Contact Name | String | ❌ | 100 chars | Required together with `emergencyContactPhone` if either is provided. |
| `emergencyContactPhone` | Emergency Contact Phone | String | ❌ | 20 chars | Same phone format rules as `phone`. Required with `emergencyContactName`. |
| `emergencyContactRelationship` | Relationship | String | ❌ | 50 chars | Free text. |

**Emergency Contact Rule**: `emergencyContactName` and `emergencyContactPhone` must be
provided together. `emergencyContactRelationship` is always optional.

#### Medical Background

| Field | Label | Type | Mandatory | Max Length | Validation Rules |
|---|---|---|---|---|---|
| `knownAllergies` | Known Allergies | Text | ❌ | 1000 chars | Free text. |
| `chronicConditions` | Chronic Conditions | Text | ❌ | 1000 chars | Free text. |

#### Audit Fields (System-managed — never editable by users)

| Field | Label | Type | Description |
|---|---|---|---|
| `createdAt` | Registration Date | DateTime (UTC) | Set once at creation. Immutable. |
| `createdBy` | Registered By | String | Identity of the registering staff member. Set once. |
| `updatedAt` | Last Updated | DateTime (UTC) | Updated on every write operation. |
| `updatedBy` | Last Updated By | String | Identity of the last updating staff member. |
| `version` | Optimistic Lock Version | Integer | Incremented on every write for optimistic concurrency control. |

---

## Patient ID Generation Rules

1. Format: `P` + `YYYY` (current calendar year at registration) + `NNN` (zero-padded
   3-digit sequence starting at `001`).
2. The sequence resets to `001` at the start of each new calendar year.
3. Sequence is per-year: `P2026999` is followed by `P2027001`.
4. If the per-year sequence exceeds `999`, it expands to 4 digits: `P20261000`.
5. Patient ID MUST be unique across all records regardless of status.
6. Deactivated Patient IDs MUST NOT be reassigned.
7. Generation MUST be atomic using a database sequence — concurrent registrations
   MUST NOT produce duplicate IDs even under high load (>100,000 requests/hour).

---

## Age Calculation Rules

1. Age = number of complete years from `dateOfBirth` to the current request date.
2. DOB = 1990-03-15, today = 2026-02-19 → age = 35 (birthday not yet reached this year).
3. DOB = 1990-02-19, today = 2026-02-19 → age = 36 (birthday is today, counts as complete year).
4. Age is never stored. Always computed at read time.
5. Displayed in years only (no months or days).

---

## Validation Error Messages

| Field | Violation | Error Message |
|---|---|---|
| First Name | Empty or blank | "First name is required." |
| First Name | Invalid characters | "First name may only contain letters, hyphens, apostrophes, and spaces." |
| First Name | Exceeds 50 chars | "First name must not exceed 50 characters." |
| Last Name | Empty or blank | "Last name is required." |
| Last Name | Invalid characters | "Last name may only contain letters, hyphens, apostrophes, and spaces." |
| Last Name | Exceeds 50 chars | "Last name must not exceed 50 characters." |
| Date of Birth | Empty | "Date of birth is required." |
| Date of Birth | Future date | "Date of birth cannot be a future date." |
| Date of Birth | Today's date | "Date of birth cannot be today." |
| Date of Birth | > 150 years ago | "Date of birth must be within the last 150 years." |
| Date of Birth | Invalid date | "Please enter a valid date of birth." |
| Gender | Not selected | "Gender is required." |
| Phone | Empty | "Phone number is required." |
| Phone | Invalid format | "Phone number must match: +1-XXX-XXX-XXXX, (XXX) XXX-XXXX, or XXX-XXX-XXXX." |
| Email | Invalid format | "Please enter a valid email address." |
| Email | Exceeds 100 chars | "Email address must not exceed 100 characters." |
| Emergency Contact | Name without phone | "Emergency contact phone is required when a contact name is provided." |
| Emergency Contact | Phone without name | "Emergency contact name is required when a contact phone is provided." |
| Emergency Contact Phone | Invalid format | "Emergency contact phone must match: +1-XXX-XXX-XXXX, (XXX) XXX-XXXX, or XXX-XXX-XXXX." |
| Known Allergies | Exceeds 1000 chars | "Known allergies must not exceed 1000 characters." |
| Chronic Conditions | Exceeds 1000 chars | "Chronic conditions must not exceed 1000 characters." |

**Duplicate phone warning** (non-blocking, shown inline above submit):
> "⚠ A patient with phone number [PHONE] already exists (Patient ID: [ID], Name: [NAME]).
> You may proceed with registration."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Register a New Patient (Priority: P1)

A receptionist onboards a first-time patient by filling in personal and medical background
details. The system assigns a unique Patient ID referencing the patient across all hospital
workflows.

**Why this priority**: Registration is the entry gate. No appointment, billing, or clinical
record can exist without a registered patient.

**Independent Test**: Fill only the 5 mandatory fields, submit, receive a Patient ID — with
no other story implemented.

**Acceptance Scenarios**:

1. **Given** a receptionist accesses the registration page, **When** the page loads,
   **Then** the system displays a form with four labelled sections: Personal Demographics,
   Contact Information, Emergency Contact, Medical Background.

2. **Given** the form is displayed, **When** viewed, **Then** mandatory fields (First Name,
   Last Name, Date of Birth, Gender, Phone) are visually marked as required.

3. **Given** all mandatory fields are valid and form is submitted, **When** processed,
   **Then** the system creates the patient record with status `ACTIVE`, generates a unique
   Patient ID (e.g., `P2026001`), shows: "Patient registered successfully. Patient ID:
   P2026001", and records `createdAt` (UTC) and `createdBy`.

4. **Given** a valid date of birth is entered, **When** the DOB field loses focus,
   **Then** the calculated age appears next to the field (e.g., "Age: 35 years") without
   requiring form submission.

5. **Given** a valid phone is entered (`+1-555-123-4567`, `(555) 123-4567`, `555-123-4567`),
   **When** the field loses focus, **Then** no error is shown.

6. **Given** an invalid phone is entered (e.g., `12345`), **When** the field loses focus,
   **Then** the field-level error message appears immediately below the field.

7. **Given** an email is entered with invalid format, **When** the field loses focus,
   **Then** the email error message appears.

8. **Given** only `emergencyContactName` is filled without `emergencyContactPhone`,
   **When** the form is submitted, **Then** the emergency contact pairing error is shown
   and the form is not submitted.

9. **Given** a phone number matching an existing patient is entered, **When** the field
   loses focus or the form is submitted, **Then** the non-blocking duplicate warning is
   shown with the existing patient's ID and name. Submit button remains enabled.

10. **Given** mandatory fields are missing or invalid, **When** submitted, **Then** the
    system does NOT submit, highlights each invalid field in red, shows the specific
    error message below each field, and scrolls to the first error if off-screen.

11. **Given** a future date is entered as date of birth, **When** the DOB field loses
    focus, **Then** "Date of birth cannot be a future date." is displayed.

12. **Given** today's date is entered as date of birth, **When** the DOB field loses
    focus, **Then** "Date of birth cannot be today." is displayed.

13. **Given** two receptionists submit registration simultaneously, **When** both are
    processed, **Then** each receives a unique Patient ID — no duplicates, no errors,
    even at >100,000 requests/hour load.

14. **Given** the submit button is clicked once, **When** processing is in progress,
    **Then** the submit button is disabled and shows a loading indicator until the
    response is received — preventing double-submission.

15. **Given** a Doctor or Nurse accesses the registration URL directly, **When** the
    page loads, **Then** an authorization error is shown and the form is not rendered.

16. **Given** the system is under peak load (>100,000 requests/hour), **When** a
    registration is submitted, **Then** the system completes the operation within the
    3-second SLA with no data loss or ID collision.

---

### User Story 2 — Search and Filter Patients (Priority: P2)

A hospital staff member quickly locates a patient from thousands of records using name,
Patient ID, phone, or email, with optional filters.

**Why this priority**: Without search, no downstream workflow is reachable.

**Independent Test**: Type a partial last name, see matching results in real time.

**Acceptance Scenarios**:

1. **Given** a user navigates to the patient list, **When** the page loads, **Then**
   all `ACTIVE` patients are shown sorted by registration date descending.

2. **Given** the list is displayed, **When** rendered, **Then** each row shows: Patient
   ID, Full Name, Age, Gender, Phone Number, Status badge.

3. **Given** the total exceeds 20 records, **When** displayed, **Then** the list
   paginates at 20 per page showing "Showing X–Y of Z patients" with Previous/Next
   controls (disabled at boundaries).

4. **Given** a user types in the search box, **When** typing pauses for 300ms,
   **Then** a case-insensitive partial-match search runs across Patient ID (prefix),
   first name, last name, phone, and email.

5. **Given** a user presses Enter in the search box, **When** pressed, **Then** the
   search executes immediately, bypassing the 300ms debounce.

6. **Given** the search box is cleared, **When** cleared, **Then** the default active
   patient list is immediately restored.

7. **Given** a status filter is selected, **When** applied, **Then** only matching
   status patients are shown (Active → ACTIVE only, Inactive → INACTIVE only,
   All → all records).

8. **Given** a gender filter is selected, **When** applied, **Then** only patients
   with that gender are shown. "All" restores default.

9. **Given** a blood group filter is selected, **When** applied, **Then** only exact
   blood group matches are shown.

10. **Given** search query and multiple filters are active simultaneously, **When**
    applied, **Then** results must satisfy ALL conditions (AND logic, not OR).

11. **Given** no records match search + filter combination, **When** results return,
    **Then** "No patients found matching your search." is shown. No error state.

12. **Given** a user clicks a patient row, **When** clicked, **Then** navigation goes
    to that patient's profile page with the list state (query, filters, page) preserved.

13. **Given** a user clicks "Back to List" from the profile page, **When** clicked,
    **Then** the patient list restores the exact previous search query, filters, and
    page number.

14. **Given** no patients exist, **When** the page loads, **Then** "No patients
    registered yet." is shown with a registration prompt (RECEPTIONIST/ADMIN only).

15. **Given** the system is under peak load (>100,000 requests/hour), **When** a
    search is executed, **Then** results are returned within 2 seconds with correct
    data, no degradation, and no query timeouts.

---

### User Story 3 — View Patient Profile (Priority: P3)

Any authenticated staff member views the complete patient record in a single organized
page.

**Why this priority**: The most frequently executed operation across all roles.

**Independent Test**: Click a patient row, see all four data sections rendered with
correct status badge and audit metadata.

**Acceptance Scenarios**:

1. **Given** a user clicks a patient row, **When** the profile loads, **Then** four
   labelled sections are shown: Personal Demographics, Contact Information, Emergency
   Contact, Medical Background.

2. **Given** the profile loads, **When** rendered, **Then** Personal Demographics shows:
   Patient ID, Full Name, DOB, Age (calculated), Gender, Blood Group.

3. **Given** the profile loads, **When** rendered, **Then** Contact Information shows:
   Phone, Email (or "Not provided"), Address, City, State, Zip.

4. **Given** emergency contact is present, **When** rendered, **Then** the section
   shows: Name, Phone, Relationship.

5. **Given** no emergency contact is stored, **When** rendered, **Then** the section
   shows: "No emergency contact on file."

6. **Given** the profile loads, **When** rendered, **Then** Medical Background shows:
   Blood Group, Known Allergies (or "None recorded"), Chronic Conditions (or "None
   recorded").

7. **Given** patient is `ACTIVE`, **When** rendered, **Then** a green badge labelled
   "Active" is prominently shown. If `INACTIVE`, a red badge labelled "Inactive".

8. **Given** the profile loads, **When** rendered, **Then** audit section shows:
   Registered [date] by [user], Last Updated [date] by [user] (or "Never updated").

9. **Given** user is RECEPTIONIST or ADMIN, **When** profile renders, **Then** "Edit
   Patient" button is visible and actionable.

10. **Given** user is DOCTOR or NURSE, **When** profile renders, **Then** no "Edit
    Patient" button is present (not rendered, not hidden, not disabled).

11. **Given** user is ADMIN and patient is `ACTIVE`, **When** rendered, **Then**
    "Deactivate Patient" button is visible.

12. **Given** user is ADMIN and patient is `INACTIVE`, **When** rendered, **Then**
    "Activate Patient" button is visible.

13. **Given** user is RECEPTIONIST, **When** rendered, **Then** no status management
    buttons are shown.

14. **Given** user clicks "Back to List", **When** clicked, **Then** the patient list
    is restored with the previous search, filters, and page.

15. **Given** a non-existent Patient ID is accessed, **When** the page loads, **Then**
    "Patient not found." is shown with a link back to the patient list.

---

### User Story 4 — Update Patient Information (Priority: P4)

A receptionist or admin corrects or updates a patient's demographic and contact details.

**Why this priority**: Patient records must reflect current information for safe clinical
and administrative operations.

**Independent Test**: Change a phone number, save, verify updated value on profile with
new `updatedAt` timestamp and `updatedBy` identity.

**Acceptance Scenarios**:

1. **Given** a RECEPTIONIST or ADMIN clicks "Edit Patient", **When** the form opens,
   **Then** all current field values are pre-populated exactly as stored.

2. **Given** the edit form is open, **When** rendered, **Then** Patient ID and
   Registration Date are visible but read-only (visually distinct from editable fields).

3. **Given** a user modifies a field, **When** the field loses focus, **Then** the same
   validation rules as registration apply immediately.

4. **Given** valid data is submitted, **When** saved, **Then** changes are persisted,
   `updatedAt` and `updatedBy` are updated, a success banner is shown, and the user is
   returned to the profile view.

5. **Given** invalid data is submitted, **When** validation fails, **Then** no fields
   are saved and error messages are shown for each invalid field.

6. **Given** a user clicks "Cancel", **When** clicked, **Then** no data is written and
   the user is returned to the profile showing the original data.

7. **Given** `phone` (mandatory) is cleared and submitted, **When** processed, **Then**
   "Phone number is required." is shown and nothing is saved.

8. **Given** `emergencyContactName` is cleared but `emergencyContactPhone` remains,
   **When** submitted, **Then** the partial emergency contact error is shown and nothing
   is saved.

9. **Given** a DOCTOR or NURSE accesses the edit form URL directly, **When** loaded,
   **Then** "You do not have permission to edit patient records." is shown. Form is not
   rendered.

10. **Given** no field values are changed and the form is submitted, **When** processed,
    **Then** the system still saves, updates `updatedAt` and `updatedBy` — no-op
    detection and skip is NOT acceptable.

11. **Given** an optional field previously filled is cleared and submitted, **When**
    processed, **Then** the field is saved as empty — intentional clearing is a valid edit.

12. **Given** the system is under peak load (>100,000 requests/hour), **When** an update
    is submitted, **Then** it completes within 2 seconds with no partial saves or silent
    failures.

---

### User Story 5 — Manage Patient Status (Priority: P5)

An admin deactivates or reactivates patient records without permanent deletion.

**Why this priority**: Soft deletion is a HIPAA compliance requirement.

**Independent Test**: Deactivate an active patient, confirm the dialog, see the status
badge change to Inactive (red) with correct timestamp.

**Acceptance Scenarios**:

1. **Given** an ADMIN views an `ACTIVE` patient profile, **When** rendered, **Then**
   "Deactivate Patient" button is shown. "Activate Patient" is not shown.

2. **Given** an ADMIN views an `INACTIVE` patient profile, **When** rendered, **Then**
   "Activate Patient" button is shown. "Deactivate Patient" is not shown.

3. **Given** ADMIN clicks "Deactivate Patient", **When** clicked, **Then** a modal
   confirmation dialog appears with title "Deactivate Patient", body "Are you sure you
   want to deactivate [Full Name] (Patient ID: [ID])? This patient will no longer
   appear in active searches.", and buttons "Confirm Deactivation" (red) and "Cancel".

4. **Given** ADMIN clicks "Confirm Deactivation", **When** confirmed, **Then** status
   changes to `INACTIVE`, `updatedAt` and `updatedBy` are recorded, dialog closes,
   profile refreshes with red "Inactive" badge, success message shown.

5. **Given** ADMIN clicks "Cancel" in the dialog, **When** cancelled, **Then** dialog
   closes with no changes.

6. **Given** ADMIN clicks "Activate Patient", **When** clicked, **Then** (no dialog)
   status immediately changes to `ACTIVE`, `updatedAt` and `updatedBy` are recorded,
   profile refreshes with green "Active" badge, success message shown.

7. **Given** the list is filtered to "Active" and a patient is deactivated, **When**
   deactivation completes, **Then** that patient automatically disappears from the
   active list without a manual page reload.

8. **Given** the list is filtered to "All", **When** a deactivated patient is shown,
   **Then** the red "Inactive" badge is clearly visible.

9. **Given** a RECEPTIONIST, DOCTOR, or NURSE attempts status change via direct URL
   or API, **When** attempted, **Then** the system returns HTTP 403 and makes no change.

---

### Edge Cases

**Registration**
- Double-click on submit MUST create only one patient record. Submit button disabled after
  first click until response received.
- Names with apostrophes (`O'Brien`) and hyphens (`Mary-Jane`) MUST be accepted.
- Single-character first name (e.g., `A`) MUST be accepted.
- Blood group not selected MUST default to `UNKNOWN`.
- Under concurrent load (e.g., 500 simultaneous registrations), zero duplicate Patient IDs
  MUST be produced.

**Search**
- Partial Patient ID prefix (e.g., `P2026`) MUST match all IDs beginning with that string.
- Search is case-insensitive: `smith` matches `Smith`, `SMITH`, `smith`.
- All filters + search active simultaneously use AND logic.
- Resetting filters preserves the active search query.
- Navigating to page 3, then to a profile, then Back MUST restore page 3 — not page 1.

**Profile View**
- A deactivated patient's profile MUST remain fully viewable.
- All optional fields with no data MUST display "Not provided" or "None recorded" — never
  blank space or null.
- Age for a patient born exactly today MUST show the correct age.

**Update**
- Submitting with identical data MUST still update `updatedAt` and `updatedBy`.
- Clearing a previously filled optional field MUST save as null/empty — intentional.

**Status Management**
- Deactivating/activating a patient changes only this module's status field — no cascade
  to other modules.
- Under optimistic lock conflict (version mismatch), the operation MUST fail with a clear
  message: "Patient record was modified by another user. Please reload and try again."

---

## Non-Functional Requirements

### SLA & Availability

| Metric | Target |
|---|---|
| Monthly uptime | ≥ 99.9% (max 43.8 minutes downtime per month) |
| Annual uptime | ≥ 99.9% (max 8.76 hours downtime per year) |
| Planned maintenance window | Announced 48 hours in advance; max 30 minutes per window |
| Recovery Time Objective (RTO) | ≤ 5 minutes from detected failure to service restored |
| Recovery Point Objective (RPO) | ≤ 1 hour (max data loss acceptable on catastrophic failure) |

To achieve 99.9% SLA, the following MUST be in place:
- Health checks on all containers with automatic restart on failure.
- Graceful shutdown: in-flight requests MUST be drained before container stops
  (minimum 30-second drain window).
- Database connection pool with automatic reconnection on connection loss.
- Retry logic for transient failures (read operations: up to 3 retries with exponential
  backoff starting at 100ms).
- Circuit breaker on Auth Module integration: if Auth Module fails for > 5 consecutive
  calls, fail closed with HTTP 503 and an appropriate message.

---

### Throughput & Performance

| Metric | Target | Condition |
|---|---|---|
| Peak throughput | > 100,000 requests/hour (~28 req/sec average, ~150 req/sec burst) | All patient module operations combined |
| Patient list load | ≤ 2 seconds at p95 | Up to 100,000 patient records |
| Search results | ≤ 2 seconds at p95 | Full-text search across 100,000 records |
| Patient registration save | ≤ 3 seconds at p95 | Under peak concurrent load |
| Patient profile load | ≤ 2 seconds at p95 | Any single record |
| Patient update save | ≤ 2 seconds at p95 | Under peak concurrent load |
| Status change | ≤ 1 second at p95 | Single record update |
| Error rate under peak load | < 0.1% | All 5xx errors combined |
| Request timeout threshold | 10 seconds | After which a 503 is returned |

**p95 definition**: 95% of all requests MUST complete within the stated time target.

---

### Scalability

| Metric | Requirement |
|---|---|
| Total patient records | Up to 100,000 records without performance degradation |
| Concurrent authenticated users | Up to 500 simultaneous users |
| Requests per hour (peak) | > 100,000 |
| Database connection pool size | Min: 5, Max: 20 connections per backend instance |
| Patient list rows per page | Fixed at 20 |

---

### Infrastructure — Docker Deployment

The entire system MUST be runnable locally using a single `docker compose up` command.
No manual setup steps beyond Docker installation should be required for a developer or
tester to get the system running.

#### Required Docker Services

| Service | Container Role | Internal Port | External Port |
|---|---|---|---|
| `db` | PostgreSQL 15 primary data store | 5432 | 5432 |
| `backend` | Spring Boot REST API | 8080 | 8080 |
| `frontend` | React web application | 3000 | 3000 |
| `reverse-proxy` | Nginx — routes traffic, handles HTTPS termination | 80 / 443 | 80 / 443 |
| `pgadmin` *(optional, dev only)* | PostgreSQL GUI administration | 5050 | 5050 |

#### Docker Compose Requirements

- All services MUST be defined in a single `docker-compose.yml` at the project root.
- A `.env.example` file MUST document every environment variable required to run the
  stack. No secrets MUST be committed to version control.
- Services MUST declare explicit health checks using Docker's `HEALTHCHECK` instruction.
- Services MUST declare `depends_on` with `condition: service_healthy` to enforce
  correct startup order: `db` must be healthy before `backend` starts.
- All container images MUST be versioned (no `latest` tags in `docker-compose.yml`).
- Data volumes MUST be named and declared explicitly so data persists across
  `docker compose down` (without `--volumes`).
- The `db` service MUST mount an `init.sql` script to initialize the schema on first run.
- Log output from all services MUST be structured JSON directed to stdout/stderr
  (Docker captures and stores these via the default logging driver).

#### Docker Network

- All services MUST run on a private Docker bridge network (`hospital-net`).
- The only service accessible from the host MUST be `reverse-proxy` on ports 80/443.
- `db` MUST NOT be exposed externally in production mode. In development mode, port
  5432 may be exposed for tooling access.

#### Environment Variable Categories

| Category | Variables |
|---|---|
| Database | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` |
| Backend | `SERVER_PORT`, `JWT_SECRET`, `LOG_LEVEL`, `DB_POOL_MIN`, `DB_POOL_MAX` |
| Frontend | `REACT_APP_API_BASE_URL` |
| Nginx | `NGINX_SERVER_NAME`, `SSL_CERT_PATH`, `SSL_KEY_PATH` |

#### Container Resource Limits (Local Development Defaults)

| Service | CPU Limit | Memory Limit |
|---|---|---|
| `db` | 2.0 CPUs | 1 GB |
| `backend` | 1.0 CPU | 512 MB |
| `frontend` | 0.5 CPU | 256 MB |
| `reverse-proxy` | 0.25 CPU | 64 MB |

---

### Infrastructure — PostgreSQL Database

#### Version & Engine

- PostgreSQL 15 or higher MUST be used.
- The database MUST run inside the `db` Docker container defined above.
- The database MUST be initialized from a versioned schema migration script on first
  startup.

#### Schema Requirements

The following tables MUST be created by the init migration:

**`patients` table** — primary patient record store:

| Column | Type | Constraints |
|---|---|---|
| `patient_id` | VARCHAR(12) | PRIMARY KEY |
| `first_name` | VARCHAR(50) | NOT NULL |
| `last_name` | VARCHAR(50) | NOT NULL |
| `date_of_birth` | DATE | NOT NULL |
| `gender` | VARCHAR(10) | NOT NULL CHECK IN ('MALE','FEMALE','OTHER') |
| `blood_group` | VARCHAR(10) | NOT NULL DEFAULT 'UNKNOWN' |
| `phone` | VARCHAR(20) | NOT NULL |
| `email` | VARCHAR(100) | NULLABLE |
| `address` | VARCHAR(200) | NULLABLE |
| `city` | VARCHAR(100) | NULLABLE |
| `state` | VARCHAR(100) | NULLABLE |
| `zip_code` | VARCHAR(20) | NULLABLE |
| `emergency_contact_name` | VARCHAR(100) | NULLABLE |
| `emergency_contact_phone` | VARCHAR(20) | NULLABLE |
| `emergency_contact_relationship` | VARCHAR(50) | NULLABLE |
| `known_allergies` | TEXT | NULLABLE |
| `chronic_conditions` | TEXT | NULLABLE |
| `status` | VARCHAR(10) | NOT NULL DEFAULT 'ACTIVE' CHECK IN ('ACTIVE','INACTIVE') |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| `created_by` | VARCHAR(100) | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| `updated_by` | VARCHAR(100) | NOT NULL |
| `version` | INTEGER | NOT NULL DEFAULT 0 |

**`patient_id_sequences` table** — atomic ID generation:

| Column | Type | Constraints |
|---|---|---|
| `year` | INTEGER | PRIMARY KEY |
| `last_sequence` | INTEGER | NOT NULL DEFAULT 0 |

**`patient_audit_log` table** — immutable audit trail:

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `timestamp` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| `operation` | VARCHAR(20) | NOT NULL CHECK IN ('REGISTER','UPDATE','DEACTIVATE','ACTIVATE') |
| `patient_id` | VARCHAR(12) | NOT NULL |
| `performed_by` | VARCHAR(100) | NOT NULL |
| `changed_fields` | TEXT[] | NULLABLE |

#### Indexing Strategy

The following indexes MUST be created to support >100,000 requests/hour throughput and
≤ 2 second search SLA:

| Index Name | Table | Columns | Type | Purpose |
|---|---|---|---|---|
| `idx_patients_status` | `patients` | `status` | B-Tree | Filter by Active/Inactive |
| `idx_patients_last_name` | `patients` | `last_name` | B-Tree | Name search |
| `idx_patients_first_name` | `patients` | `first_name` | B-Tree | Name search |
| `idx_patients_phone` | `patients` | `phone` | B-Tree | Phone search + duplicate check |
| `idx_patients_email` | `patients` | `email` | B-Tree | Email search |
| `idx_patients_blood_group` | `patients` | `blood_group` | B-Tree | Blood group filter |
| `idx_patients_gender` | `patients` | `gender` | B-Tree | Gender filter |
| `idx_patients_created_at` | `patients` | `created_at DESC` | B-Tree | Default sort order |
| `idx_patients_full_text` | `patients` | `first_name`, `last_name` | GIN (tsvector) | Full-text search |
| `idx_audit_patient_id` | `patient_audit_log` | `patient_id` | B-Tree | Audit queries by patient |
| `idx_audit_performed_by` | `patient_audit_log` | `performed_by` | B-Tree | Audit queries by user |
| `idx_audit_timestamp` | `patient_audit_log` | `timestamp DESC` | B-Tree | Audit chronological queries |

#### PostgreSQL Configuration (Local Docker Tuning)

The following PostgreSQL settings MUST be applied via `postgresql.conf` mounted into
the `db` container to support enterprise-grade throughput:

| Parameter | Value | Rationale |
|---|---|---|
| `max_connections` | 100 | Supports connection pool max of 20 × up to 5 app instances |
| `shared_buffers` | 256MB | 25% of container memory allocation (1 GB) |
| `effective_cache_size` | 768MB | 75% of container memory |
| `work_mem` | 4MB | Per-sort/per-hash memory for complex queries |
| `maintenance_work_mem` | 64MB | For VACUUM, CREATE INDEX operations |
| `wal_buffers` | 16MB | WAL write performance for high-write workloads |
| `checkpoint_completion_target` | 0.9 | Spread checkpoint I/O over time |
| `random_page_cost` | 1.1 | Tuned for SSD/Docker storage |
| `effective_io_concurrency` | 200 | Parallel I/O for bitmap heap scans |
| `autovacuum` | on | MUST remain enabled — prevents table bloat |
| `log_min_duration_statement` | 1000ms | Log queries slower than 1 second for analysis |
| `log_line_prefix` | `'%t [%p]: '` | Structured log prefix with timestamp and PID |
| `timezone` | `UTC` | All timestamps stored in UTC |

#### Connection Pool Requirements

- The backend MUST use a connection pool (e.g., HikariCP) with the following settings:
  - Minimum idle connections: 5
  - Maximum pool size: 20
  - Connection timeout: 3,000ms (fail fast rather than queue indefinitely)
  - Idle timeout: 600,000ms (10 minutes)
  - Max lifetime: 1,800,000ms (30 minutes, shorter than PostgreSQL's idle timeout)
  - Keep-alive query: `SELECT 1` (validates connections before use)

#### Backup & Recovery

| Backup Type | Frequency | Retention | Storage |
|---|---|---|---|
| Full logical backup (`pg_dump`) | Daily at 02:00 UTC | 90 days local | Docker volume `db-backups` |
| Incremental WAL archiving | Continuous | 30 days | Docker volume `db-wal-archive` |
| Long-term HIPAA archive | Monthly snapshot | 7 years minimum | Off-container cold storage |

- Backups MUST be tested for restorability at minimum monthly.
- A `restore.sh` script MUST be provided alongside `docker-compose.yml`.
- Restore from last daily backup MUST be achievable in ≤ 5 minutes (supporting RTO).

---

### Observability & Monitoring

#### Health Check Endpoints

The backend MUST expose the following health check endpoints:

| Endpoint | Purpose | Expected Response |
|---|---|---|
| `GET /actuator/health` | Overall service health (liveness + readiness) | `{"status": "UP"}` |
| `GET /actuator/health/liveness` | Is the process alive? | `{"status": "UP"}` |
| `GET /actuator/health/readiness` | Is it ready to accept traffic? (DB connected, pool available) | `{"status": "UP"}` |
| `GET /actuator/metrics` | Prometheus-compatible metrics endpoint | Metrics in text format |
| `GET /actuator/info` | Build version, commit hash, startup time | JSON info object |

Docker `HEALTHCHECK` for the backend container MUST call `/actuator/health/readiness`
every 10 seconds with a 5-second timeout, 3 retries before marking unhealthy.

#### Metrics to Expose

The following metrics MUST be collected and exposed via the `/actuator/metrics` endpoint:

| Metric | Description |
|---|---|
| `http.server.requests` | Request count, latency histogram per endpoint and status code |
| `patient.registrations.total` | Total successful patient registrations |
| `patient.searches.total` | Total search operations executed |
| `patient.updates.total` | Total patient record updates |
| `patient.status_changes.total` | Total activate/deactivate operations |
| `db.pool.active` | Active database connections in pool |
| `db.pool.idle` | Idle database connections in pool |
| `db.pool.pending` | Requests waiting for a connection |
| `jvm.memory.used` | JVM heap and non-heap memory usage |
| `jvm.gc.pause` | Garbage collection pause times |
| `auth.circuit_breaker.state` | Auth Module circuit breaker state (CLOSED/OPEN/HALF_OPEN) |

#### Structured Logging

All application log output MUST be structured JSON, emitted to stdout, with the following
fields on every log entry:

| Field | Description |
|---|---|
| `timestamp` | ISO-8601 UTC |
| `level` | `INFO`, `WARN`, `ERROR`, `DEBUG` |
| `service` | `patient-module` |
| `traceId` | Unique request trace identifier (for correlating logs across a request) |
| `userId` | Authenticated user identity (MUST be present; MUST NOT be PHI) |
| `operation` | Logical operation name (e.g., `REGISTER_PATIENT`, `SEARCH_PATIENTS`) |
| `patientId` | Patient ID affected (if applicable; NOT other PHI fields) |
| `durationMs` | Request processing time in milliseconds |
| `message` | Human-readable log message |

**PHI restriction**: Log entries MUST NOT include patient name, date of birth, phone,
email, address, medical background, or any other PHI beyond `patientId`.

---

### Security & HIPAA Compliance

- All patient data MUST be classified as PHI.
- Every read, write, and status change MUST produce an immutable audit log entry.
- PHI MUST NOT appear in application logs, error responses, or stack traces.
- All requests without a valid authenticated session MUST be rejected with HTTP 401.
- All requests with insufficient role MUST be rejected with HTTP 403.
- Role enforcement MUST be server-side; client-side checks are cosmetic only.
- All data transmission MUST use HTTPS (TLS 1.2 minimum; TLS 1.3 preferred). Nginx
  reverse proxy handles TLS termination.
- HTTP (port 80) MUST redirect to HTTPS (port 443) — no plain HTTP patient data ever.
- Database passwords and JWT secrets MUST be injected via environment variables from
  `.env` file — never hardcoded in source or committed to version control.
- The `db` container MUST NOT expose port 5432 externally in any production or
  production-like environment.
- SQL queries MUST use parameterized statements — no string concatenation for queries.
- Audit logs MUST be retained for a minimum of 7 years (HIPAA requirement).
- Rate limiting MUST be applied at the Nginx level: max 200 requests per IP per minute
  for API endpoints. Exceeding this returns HTTP 429 with `Retry-After` header.

---

### Resilience & Fault Tolerance

#### Auth Module Circuit Breaker

| State | Trigger | Behavior |
|---|---|---|
| CLOSED (normal) | < 5 failures in 10 seconds | All requests proceed |
| OPEN (failing) | ≥ 5 failures in 10 seconds | All patient requests immediately return HTTP 503: "Authentication service unavailable. Please try again shortly." |
| HALF-OPEN (testing) | After 30-second cooldown | One probe request allowed through. If success → CLOSED. If fail → OPEN again. |

#### Database Failure

- If the database becomes unreachable, the backend MUST return HTTP 503 with:
  "Service temporarily unavailable. Please try again in a moment."
- Connection pool MUST attempt reconnection automatically with exponential backoff
  (initial: 1s, max: 30s, max attempts: indefinite).
- In-flight requests at time of DB failure MUST be failed cleanly — no partial writes,
  no hung connections.

#### Graceful Shutdown

- On receiving SIGTERM, the backend MUST:
  1. Stop accepting new requests immediately (Nginx removes the instance from rotation).
  2. Allow all in-flight requests to complete (drain window: 30 seconds).
  3. Close database connections cleanly.
  4. Exit with code 0.
- Container stop timeout MUST be set to 35 seconds (5s margin above drain window).

#### Read Retries

- Read-only operations (search, profile view) MUST retry up to 3 times on transient
  database errors with exponential backoff (100ms, 200ms, 400ms).
- Write operations (register, update, status change) MUST NOT be retried automatically
  to prevent duplicate writes. Failed writes MUST be surfaced to the user immediately.

---

### Usability

- Registration form completable in under 3 minutes by a trained receptionist.
- Real-time search feedback within 300ms debounce.
- Inline error messages adjacent to each field — not only at top of form.
- Fully functional on desktop (≥1280px), tablet (768–1279px), mobile (320–767px).
- Status badge MUST use both color AND text label (accessible to color-blind users).
- All interactive elements MUST be keyboard-navigable (WCAG 2.1 AA compliance target).

---

### Data Integrity

- Patient ID is unique, immutable after creation, and never reused.
- `dateOfBirth` stored as DATE type, never string.
- Age derived at read time, never stored.
- Concurrent registrations MUST produce no duplicate IDs (enforced by DB sequence lock).
- `createdAt` is immutable — no UPDATE statement MUST ever touch it.
- All writes use optimistic locking (`version` field) to detect concurrent modification
  conflicts. Conflict response: HTTP 409 with user-friendly message.
- Database transactions MUST be used for all write operations (register, update, status
  change, audit log write). Audit log entry and patient record update MUST be committed
  in the same transaction — they MUST NOT diverge.

---

## Patient Status State Machine

```
          [Registration]
               ↓
           ACTIVE ←──────────────────┐
               │                     │
  [Admin: Deactivate + Confirm]  [Admin: Activate]
               │                     │
               ↓                     │
           INACTIVE ─────────────────┘

Rules:
- ACTIVE   → INACTIVE : ADMIN only, requires modal confirmation
- INACTIVE → ACTIVE   : ADMIN only, no confirmation
- No other transitions exist
- Hard delete is FORBIDDEN under all circumstances
- Status change + audit log write are a single atomic transaction
```

---

## Audit Trail Specification

Every write on a patient record MUST generate an audit log entry:

| Field | Value |
|---|---|
| `timestamp` | UTC timestamp of the operation |
| `operation` | `REGISTER`, `UPDATE`, `DEACTIVATE`, `ACTIVATE` |
| `patientId` | Affected Patient ID |
| `performedBy` | Identity of the authenticated staff member |
| `changedFields` | List of field names modified (UPDATE only; null for others) |

Audit log MUST be:
- Written in the same database transaction as the patient record change.
- Append-only — no UPDATE or DELETE permitted on audit records.
- Stored in a separate `patient_audit_log` table.
- Queryable by `patientId` and `performedBy`.
- Retained for a minimum of 7 years (HIPAA).
- Never contain PHI beyond `patientId` (no names, DOB, phone, etc.).

---

## Integration Contract with Auth Module

Every authenticated request must carry the following from the Auth Module:

| Data | Description |
|---|---|
| `userId` | Unique identifier of the logged-in staff member |
| `username` | Display name stored in audit trail (`createdBy`, `updatedBy`) |
| `role` | One of: `RECEPTIONIST`, `DOCTOR`, `NURSE`, `ADMIN` |
| `sessionValid` | Boolean — if false, request is rejected with HTTP 401 |

**Fail-closed rule**: If Auth Module is unavailable (circuit breaker OPEN), ALL patient
module operations MUST be denied. The system MUST NOT fail open.

---

## Requirements *(mandatory)*

### Functional Requirements

**Registration**
- **FR-001**: System MUST allow RECEPTIONIST and ADMIN to register a new patient with 5 mandatory fields: first name, last name, DOB, gender, phone.
- **FR-002**: System MUST generate a unique Patient ID (`P` + year + zero-padded sequence) atomically via a database sequence — safe under >100,000 concurrent requests/hour.
- **FR-003**: System MUST calculate and display patient age on DOB field blur without form submission.
- **FR-004**: System MUST validate phone number against three accepted formats before saving.
- **FR-005**: System MUST validate email format when provided.
- **FR-006**: System MUST display a non-blocking duplicate phone warning (with matching patient ID and name) without blocking registration.
- **FR-007**: System MUST default patient status to `ACTIVE` on registration.
- **FR-008**: System MUST record `createdAt` (UTC) and `createdBy` on registration. These fields MUST be immutable.
- **FR-009**: System MUST require `emergencyContactPhone` when `emergencyContactName` is provided, and vice versa.
- **FR-010**: System MUST disable the submit button after first click until a response is received.
- **FR-011**: System MUST reject DOB that is today, future, or > 150 years in the past.

**Search & Filtering**
- **FR-012**: System MUST display all `ACTIVE` patients sorted by `createdAt` descending by default.
- **FR-013**: System MUST show Patient ID, Full Name, Age, Gender, Phone, Status on each list row.
- **FR-014**: System MUST perform case-insensitive partial-match search (Patient ID prefix, first name, last name, phone, email) with 300ms debounce or Enter key trigger.
- **FR-015**: System MUST support simultaneous filtering by status, gender, and blood group (AND logic).
- **FR-016**: System MUST paginate the list at 20 records per page, showing "Showing X–Y of Z patients".
- **FR-017**: System MUST preserve the patient list state (query, filters, page) across profile navigation.

**Profile View**
- **FR-018**: System MUST display all patient data in four sections: Personal Demographics, Contact Information, Emergency Contact, Medical Background.
- **FR-019**: System MUST display status as a color-coded badge WITH a text label.
- **FR-020**: System MUST display audit metadata: registered date/by, last updated date/by.
- **FR-021**: System MUST show "Edit Patient" button only to RECEPTIONIST and ADMIN.
- **FR-022**: System MUST show "Deactivate/Activate Patient" button only to ADMIN (contextual to status).
- **FR-023**: System MUST display "Not provided" / "None recorded" for empty optional fields.

**Update**
- **FR-024**: System MUST pre-populate all current field values in the edit form.
- **FR-025**: System MUST render Patient ID and Registration Date as read-only in the edit form.
- **FR-026**: System MUST apply identical validation rules on update as on registration.
- **FR-027**: System MUST save even when data is unchanged, updating `updatedAt` and `updatedBy`.
- **FR-028**: System MUST discard all changes and return to profile on Cancel — no data written.
- **FR-029**: System MUST treat clearing an optional field as a valid save (intentional null).
- **FR-030**: System MUST detect optimistic lock conflicts (version mismatch) and return HTTP 409 with a user-friendly message.

**Status Management**
- **FR-031**: System MUST require ADMIN modal confirmation before deactivating.
- **FR-032**: System MUST activate immediately on click with no confirmation.
- **FR-033**: System MUST record `updatedAt` and `updatedBy` on every status change.
- **FR-034**: System MUST NOT permanently delete any patient record under any circumstances.

**Access Control**
- **FR-035**: System MUST enforce role-based access server-side on every request.
- **FR-036**: System MUST reject unauthenticated requests with HTTP 401.
- **FR-037**: System MUST reject insufficient-role requests with HTTP 403.

**Audit**
- **FR-038**: System MUST write an immutable audit log entry in the same transaction as every patient write operation.
- **FR-039**: System MUST NOT include PHI in audit log entries beyond `patientId`.

**Infrastructure & Reliability**
- **FR-040**: System MUST be fully runnable via `docker compose up` with no manual setup beyond Docker installation.
- **FR-041**: System MUST expose `/actuator/health/liveness` and `/actuator/health/readiness` endpoints.
- **FR-042**: System MUST expose structured JSON logs to stdout with all required fields.
- **FR-043**: System MUST implement a circuit breaker for Auth Module integration (open after 5 failures in 10 seconds, reset after 30-second cooldown).
- **FR-044**: System MUST implement graceful shutdown with a 30-second drain window.
- **FR-045**: System MUST apply rate limiting of 200 requests per IP per minute at the proxy layer.
- **FR-046**: System MUST use parameterized queries for all database interactions.
- **FR-047**: System MUST implement database connection pooling (min 5, max 20 connections).
- **FR-048**: System MUST use optimistic locking (version field) on patient records to prevent silent concurrent modification.

---

### Key Entities

- **Patient**: Central record. All demographic, contact, emergency contact, medical
  background, status, audit fields, and version counter. Never deleted.

- **PatientIdSequence**: Per-year counter table enabling atomic, duplicate-free Patient ID
  generation under concurrent load.

- **AuditLogEntry**: Immutable record of every write operation. Stored in a separate table
  in the same transaction as the patient write. Append-only. 7-year retention.

- **Staff Member** *(external — Auth Module)*: Provides `userId`, `username`, `role`.
  Not managed here.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Receptionists complete new patient registration in under 3 minutes.
- **SC-002**: Any authenticated staff member locates a specific patient in under 5 seconds.
- **SC-003**: Search results return within 2 seconds at p95 across 100,000 records.
- **SC-004**: System sustains > 100,000 API requests per hour at < 0.1% error rate.
- **SC-005**: System achieves ≥ 99.9% monthly uptime (max 43.8 minutes downtime/month).
- **SC-006**: System recovers from a container failure within 5 minutes (RTO ≤ 5 min).
- **SC-007**: System loses at most 1 hour of data on catastrophic failure (RPO ≤ 1 hour).
- **SC-008**: Zero duplicate Patient IDs are produced under any concurrent load.
- **SC-009**: Zero patient records are permanently deleted — all deactivations are reversible.
- **SC-010**: Every write operation produces a traceable, immutable audit log entry.
- **SC-011**: Unauthorized access attempts are rejected 100% of the time (0 false passes).
- **SC-012**: The entire local stack starts from `docker compose up` in under 2 minutes on a standard developer machine.
- **SC-013**: All 5 user stories are independently functional and testable.
- **SC-014**: The registration form renders correctly on desktop, tablet, and mobile.

---

## Assumptions

1. Auth Module provides `userId`, `username`, and `role` on every authenticated request.
   This module does not handle login, logout, or role assignment.
2. Docker and Docker Compose are available on the target local machine.
3. PostgreSQL 15 runs inside Docker — no external database host is required for local
   deployment.
4. "Duplicate phone" warning is informational only — registration is never blocked by it.
5. Optional fields may be blank at registration and filled later via update.
6. Age is always derived from date of birth — never stored or manually entered.
7. All timestamps are stored and transmitted in UTC. Client display timezone conversion
   is a future enhancement.
8. Under optimistic lock conflict, last-write-wins is NOT acceptable — the conflicting
   write MUST fail with HTTP 409 and the user must reload and re-attempt.
9. Blood group defaults to `UNKNOWN` when not selected.
10. Patient ID sequence may have gaps (due to rolled-back transactions). Uniqueness is
    guaranteed; contiguity is not.
11. Backup scripts are provided and tested, but automated backup scheduling is managed
    via host-level cron jobs (or Docker's restart policies) — not inside containers.
12. TLS certificates for local HTTPS are self-signed. Production certificates are out of
    scope for this spec.
