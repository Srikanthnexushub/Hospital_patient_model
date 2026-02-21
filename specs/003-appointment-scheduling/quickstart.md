# Quickstart: Appointment Scheduling Integration Scenarios

**Branch**: `003-appointment-scheduling`
**Phase**: 1 — Design
**Date**: 2026-02-20

These scenarios are the basis for integration tests (`*IT.java`). Each scenario is independently executable using the `BaseIntegrationTest` pattern with Testcontainers.

---

## Prerequisites (seeded before each scenario)

```java
// Seed: one ACTIVE patient
String PATIENT_ID = "P2025001";

// Seed: one ACTIVE DOCTOR (inserted directly via JdbcTemplate in BaseIntegrationTest)
String DOCTOR_ID = "U2025001";  // role=DOCTOR, status=ACTIVE

// Seed: one ACTIVE RECEPTIONIST
String RECEPTIONIST_ID = "U2025002"; // role=RECEPTIONIST, status=ACTIVE

// JWT tokens available via buildTestJwt(role):
// buildTestJwt("RECEPTIONIST") → receptionist token
// buildTestJwt("DOCTOR")       → doctor token (userId injected as DOCTOR_ID in test setup)
// buildTestJwt("ADMIN")        → admin token
// buildTestJwt("NURSE")        → nurse token
```

---

## Scenario 1: Happy Path — Full Appointment Lifecycle (US1 + US3)

Tests the complete state machine: SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED.

```
Step 1  POST /api/v1/appointments (RECEPTIONIST)
        Body: { patientId: P2025001, doctorId: U2025001, appointmentDate: tomorrow,
                startTime: "09:00", durationMinutes: 30, type: GENERAL_CONSULTATION,
                reason: "Annual checkup" }
        → 201 { appointmentId: APT2026xxxx, status: SCHEDULED }

Step 2  PATCH /api/v1/appointments/{id}/status (RECEPTIONIST)
        Body: { action: CONFIRM }
        → 200 { newStatus: CONFIRMED }

Step 3  PATCH /api/v1/appointments/{id}/status (RECEPTIONIST)
        Body: { action: CHECK_IN }
        → 200 { newStatus: CHECKED_IN }

Step 4  PATCH /api/v1/appointments/{id}/status (DOCTOR)
        Body: { action: START }
        → 200 { newStatus: IN_PROGRESS }

Step 5  PATCH /api/v1/appointments/{id}/status (DOCTOR)
        Body: { action: COMPLETE }
        → 200 { newStatus: COMPLETED }

Step 6  GET /api/v1/appointments/{id}
        → 200 { status: COMPLETED, patientName: ..., doctorName: ... }

Verify: 5 audit log entries exist for this appointmentId
```

---

## Scenario 2: Conflict Detection (US1)

Two simultaneous booking attempts for the same doctor + slot.

```
Step 1  POST /api/v1/appointments (RECEPTIONIST)
        Body: { doctorId: U2025001, appointmentDate: tomorrow,
                startTime: "10:00", durationMinutes: 60, type: FOLLOW_UP, ... }
        → 201 { status: SCHEDULED, startTime: 10:00, endTime: 11:00 }

Step 2  POST /api/v1/appointments (RECEPTIONIST)
        Body: { doctorId: U2025001, appointmentDate: tomorrow,
                startTime: "10:30", durationMinutes: 30, type: FOLLOW_UP, ... }
        (overlaps 10:00–11:00)
        → 409 { message: "Doctor has a conflicting appointment in this time slot." }

Step 3  POST /api/v1/appointments (RECEPTIONIST)
        Body: { doctorId: U2025001, appointmentDate: tomorrow,
                startTime: "11:00", durationMinutes: 30, type: FOLLOW_UP, ... }
        (no overlap — starts exactly when first ends)
        → 201 { status: SCHEDULED }
```

---

## Scenario 3: RBAC Enforcement (US1, US3, US4, US6)

```
Step 1  POST /api/v1/appointments with NURSE token
        → 403 Forbidden

Step 2  PATCH /api/v1/appointments/{id}/status { action: CONFIRM } with NURSE token
        → 403 Forbidden (NURSE cannot CONFIRM)

Step 3  PATCH /api/v1/appointments/{id}/status { action: CHECK_IN } with NURSE token
        (on CONFIRMED appointment)
        → 200 { newStatus: CHECKED_IN }  ← NURSE CAN check in

Step 4  POST /api/v1/appointments/{id}/notes with RECEPTIONIST token
        → 403 Forbidden

Step 5  PATCH /api/v1/appointments/{id} with NURSE token (update details)
        → 403 Forbidden
```

---

## Scenario 4: Doctor Availability (US5)

```
Step 1  Book a 60-minute appointment at 09:00 for U2025001 (status: CONFIRMED)
        endTime = 10:00

Step 2  GET /api/v1/doctors/U2025001/availability?date=tomorrow
        → 200 {
            doctorId: U2025001,
            doctorName: "...",
            slots: [
              { startTime: "08:00", endTime: "08:30", available: true },
              { startTime: "08:30", endTime: "09:00", available: true },
              { startTime: "09:00", endTime: "09:30", available: false, appointmentId: "APT..." },
              { startTime: "09:30", endTime: "10:00", available: false, appointmentId: "APT..." },
              { startTime: "10:00", endTime: "10:30", available: true },
              ... (17 more slots all available)
            ]
          }

Step 3  Cancel the appointment (ADMIN)
        PATCH /api/v1/appointments/{id}/status { action: CANCEL, reason: "Patient request" }

Step 4  GET /api/v1/doctors/U2025001/availability?date=tomorrow
        → All 20 slots available (cancelled appointment no longer blocks)
```

---

## Scenario 5: Update Appointment (US4)

```
Step 1  Book appointment at 09:00 (SCHEDULED). Capture version = 0.

Step 2  PATCH /api/v1/appointments/{id}
        Headers: If-Match: 0
        Body: { startTime: "10:00" }
        → 200 { startTime: "10:00", version: 1 }

Step 3  Repeat PATCH with old version (If-Match: 0)
        → 409 Version conflict

Step 4  Attempt to update a COMPLETED appointment
        → 400 or 409 "Appointment status does not allow updates"

Step 5  Attempt to change doctorId in body
        → 200 but doctorId unchanged in response (field ignored)
```

---

## Scenario 6: Clinical Notes (US6)

```
Step 1  Book → Confirm → Check-In → Start appointment (IN_PROGRESS)

Step 2  POST /api/v1/appointments/{id}/notes (DOCTOR token for assigned doctor)
        Body: {
          chiefComplaint: "Headache",
          diagnosis: "Tension headache",
          treatment: "Rest and ibuprofen",
          prescription: "Ibuprofen 400mg as needed",
          followUpRequired: true,
          followUpDays: 14,
          privateNotes: "Patient appeared anxious"
        }
        → 200 (notes stored encrypted)

Step 3  GET /api/v1/appointments/{id}/notes (DOCTOR token)
        → 200 { ..., privateNotes: "Patient appeared anxious" }

Step 4  GET /api/v1/appointments/{id}/notes (RECEPTIONIST token)
        → 200 { ..., privateNotes: null }  ← privateNotes excluded

Step 5  POST /api/v1/appointments/{id}/notes (different DOCTOR token, not assigned)
        → 403 Forbidden

Step 6  POST /api/v1/appointments/{id}/notes (ADMIN token)
        Body: { chiefComplaint: "Updated complaint" }
        → 200 (admin can update notes)
```

---

## Scenario 7: Patient Appointment History (US7)

```
Step 1  Book 3 appointments for P2025001:
        - APT-A with doctor U2025001 (COMPLETED)
        - APT-B with doctor U2025001 (SCHEDULED)
        - APT-C with doctor U2025002 (COMPLETED)

Step 2  GET /api/v1/patients/P2025001/appointments (ADMIN token)
        → 200 { totalElements: 3 } — all 3 returned, sorted by date DESC

Step 3  GET /api/v1/patients/P2025001/appointments (DOCTOR token for U2025001)
        → 200 { totalElements: 2 } — only APT-A and APT-B (own appointments only)

Step 4  GET /api/v1/patients/P2025001/appointments (RECEPTIONIST token)
        → 200 { totalElements: 3 } — all appointments

Step 5  GET /api/v1/patients/P9999999/appointments (non-existent patient)
        → 404 Not Found
```

---

## Scenario 8: Cancel with No-Show (US3)

```
Step 1  Book and CONFIRM appointment.

Step 2  PATCH /api/v1/appointments/{id}/status { action: NO_SHOW } (RECEPTIONIST)
        → 200 { newStatus: NO_SHOW }

Step 3  GET /api/v1/appointments/{id}
        → 200 { status: NO_SHOW } — record retained (soft-delete)

Step 4  POST /api/v1/appointments (RECEPTIONIST) — book same slot for same doctor
        → 201 { status: SCHEDULED } — slot is now free (NO_SHOW doesn't block availability)

Step 5  PATCH /api/v1/appointments/{id}/status { action: CANCEL } (without reason)
        → 400 "cancel reason is required"
```

---

## Integration Test Class Mapping

| Scenario | Suggested IT Class |
|----------|--------------------|
| 1 — Full lifecycle | `AppointmentLifecycleIT` |
| 2 — Conflict detection | `AppointmentConflictIT` |
| 3 — RBAC matrix | `AppointmentRbacIT` |
| 4 — Availability | `DoctorAvailabilityIT` |
| 5 — Update + optimistic lock | `AppointmentUpdateIT` |
| 6 — Clinical notes | `ClinicalNotesIT` |
| 7 — Patient history | `PatientAppointmentHistoryIT` |
| 8 — Cancel/no-show | `AppointmentCancellationIT` |

---

## BaseIntegrationTest Extension for Module 3

The existing `BaseIntegrationTest` `@BeforeEach` must also truncate the new tables:

```java
jdbcTemplate.execute("TRUNCATE TABLE clinical_notes CASCADE");
jdbcTemplate.execute("TRUNCATE TABLE appointment_audit_log RESTART IDENTITY CASCADE");
jdbcTemplate.execute("TRUNCATE TABLE appointment_id_sequences CASCADE");
jdbcTemplate.execute("TRUNCATE TABLE appointments CASCADE");
// Existing tables (patients, patient_audit_log, etc.) remain as-is
```

Additionally, test helper methods needed:
```java
// Seed a doctor into hospital_users for appointment tests
protected String seedDoctor(String username);

// Seed a patient with P2025xxx ID
protected String seedPatient(String firstName, String lastName);

// Quick book helper — returns appointmentId
protected String bookAppointment(String patientId, String doctorId, String date, String startTime);
```
