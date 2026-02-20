# Quickstart & Integration Scenarios: EMR Module

**Branch**: `005-emr-module` | **Date**: 2026-02-20
**Phase 1 output** — key integration test scenarios for `speckit.tasks`

---

## Prerequisites

All scenarios require:
1. A patient registered via Module 1 (e.g., `patientId = "P2025001"`)
2. An appointment booked via Module 3 (e.g., `appointmentId = "A20261001001"`, status `IN_PROGRESS` or `COMPLETED`)
3. Valid JWT tokens for each role (see `BaseIntegrationTest.buildTestJwt(role)`)

---

## Scenario 1 — Nurse Records Vitals (US1 Happy Path)

```http
POST /api/v1/appointments/A20261001001/vitals
Authorization: Bearer <NURSE_JWT>
Content-Type: application/json

{
  "bloodPressureSystolic": 120,
  "bloodPressureDiastolic": 80,
  "heartRate": 72,
  "temperature": 37.0
}
```

**Expected**: `200 OK` with all 4 submitted values confirmed. `recordedBy` = nurse username.

**Re-POST (upsert)**:
```http
POST /api/v1/appointments/A20261001001/vitals
Authorization: Bearer <NURSE_JWT>
{ "heartRate": 75, "temperature": 37.2 }
```

**Expected**: `200 OK`. Previous `bloodPressureSystolic` / `bloodPressureDiastolic` values are replaced with `null`; new heartRate and temperature returned. (Full replacement, not merge.)

---

## Scenario 2 — Doctor Views Vitals History (US1)

```http
GET /api/v1/patients/P2025001/vitals?page=0&size=10
Authorization: Bearer <DOCTOR_JWT>
```

**Expected**: `200 OK` with paginated list sorted by `recordedAt DESC`.

**RECEPTIONIST denied**:
```http
GET /api/v1/patients/P2025001/vitals
Authorization: Bearer <RECEPTIONIST_JWT>
```

**Expected**: `403 Forbidden`.

---

## Scenario 3 — Doctor Adds and Resolves a Problem (US2)

```http
POST /api/v1/patients/P2025001/problems
Authorization: Bearer <DOCTOR_JWT>
{
  "title": "Type 2 Diabetes",
  "severity": "MODERATE",
  "status": "ACTIVE",
  "onsetDate": "2020-03-15",
  "icdCode": "E11"
}
```

**Expected**: `201 Created` with `id` (UUID), `createdBy` = doctor username.

```http
PATCH /api/v1/patients/P2025001/problems/{problemId}
Authorization: Bearer <DOCTOR_JWT>
{ "status": "RESOLVED" }
```

**Expected**: `200 OK`. Problem `status = RESOLVED`.

```http
GET /api/v1/patients/P2025001/problems?status=ACTIVE
Authorization: Bearer <NURSE_JWT>
```

**Expected**: `200 OK`. Resolved problem NOT in list.

**Nurse tries to create (denied)**:
```http
POST /api/v1/patients/P2025001/problems
Authorization: Bearer <NURSE_JWT>
{ "title": "Hypertension", "severity": "MILD", "status": "ACTIVE" }
```

**Expected**: `403 Forbidden`.

---

## Scenario 4 — Doctor Prescribes and Discontinues Medication (US3)

```http
POST /api/v1/patients/P2025001/medications
Authorization: Bearer <DOCTOR_JWT>
{
  "medicationName": "Metformin",
  "dosage": "500mg",
  "frequency": "twice daily",
  "route": "ORAL",
  "startDate": "2026-02-20"
}
```

**Expected**: `201 Created`. `prescribedBy` auto-set from JWT — NOT from request body.

```http
PATCH /api/v1/patients/P2025001/medications/{medicationId}
Authorization: Bearer <DOCTOR_JWT>
{ "status": "DISCONTINUED", "endDate": "2026-02-20" }
```

**Expected**: `200 OK`. `status = DISCONTINUED`.

```http
GET /api/v1/patients/P2025001/medications?status=ALL
Authorization: Bearer <NURSE_JWT>
```

**Expected**: `200 OK`. Both ACTIVE and DISCONTINUED medications returned.

---

## Scenario 5 — Nurse Records Allergy, Receptionist Views It (US4)

```http
POST /api/v1/patients/P2025001/allergies
Authorization: Bearer <NURSE_JWT>
{
  "substance": "Penicillin",
  "type": "DRUG",
  "severity": "LIFE_THREATENING",
  "reaction": "anaphylaxis"
}
```

**Expected**: `201 Created`. `active = true`.

```http
GET /api/v1/patients/P2025001/allergies
Authorization: Bearer <RECEPTIONIST_JWT>
```

**Expected**: `200 OK`. Allergy visible to receptionist (read-only access).

```http
DELETE /api/v1/patients/P2025001/allergies/{allergyId}
Authorization: Bearer <DOCTOR_JWT>
```

**Expected**: `204 No Content`. Allergy soft-deleted (`active = false`).

**Re-delete (already inactive)**:
```http
DELETE /api/v1/patients/P2025001/allergies/{allergyId}
Authorization: Bearer <DOCTOR_JWT>
```

**Expected**: `404 Not Found`.

---

## Scenario 6 — Doctor Gets Medical Summary (US5)

```http
GET /api/v1/patients/P2025001/medical-summary
Authorization: Bearer <DOCTOR_JWT>
```

**Expected** (patient has all 4 data types):
```json
{
  "patientId": "P2025001",
  "activeProblems": [ { "id": "...", "title": "Type 2 Diabetes", ... } ],
  "activeMedications": [ { "id": "...", "medicationName": "Metformin", ... } ],
  "allergies": [ { "id": "...", "substance": "Penicillin", "severity": "LIFE_THREATENING" } ],
  "recentVitals": [ { "heartRate": 72, "temperature": 37.0, "recordedAt": "..." } ],
  "lastVisitDate": "2026-02-20",
  "totalVisits": 3
}
```

**New patient (no EMR data)**:
```json
{
  "patientId": "P2026999",
  "activeProblems": [],
  "activeMedications": [],
  "allergies": [],
  "recentVitals": [],
  "lastVisitDate": null,
  "totalVisits": 0
}
```

**Nurse denied**:
```http
GET /api/v1/patients/P2025001/medical-summary
Authorization: Bearer <NURSE_JWT>
```
**Expected**: `403 Forbidden`.

---

## Scenario 7 — Validation Edge Cases

**BP diastolic > systolic**:
```json
{ "bloodPressureSystolic": 80, "bloodPressureDiastolic": 120 }
```
**Expected**: `400 Bad Request`.

**O2 saturation out of range**:
```json
{ "oxygenSaturation": 105 }
```
**Expected**: `400 Bad Request`.

**Empty vitals (no measurements)**:
```json
{}
```
**Expected**: `400 Bad Request` — "at least one measurement required".

**Future onset date for problem**:
```json
{ "title": "Hypertension", "severity": "MILD", "status": "ACTIVE", "onsetDate": "2099-01-01" }
```
**Expected**: `400 Bad Request`.

**Medication endDate before startDate**:
```json
{ "medicationName": "Aspirin", "dosage": "100mg", "frequency": "daily", "route": "ORAL", "startDate": "2026-02-20", "endDate": "2025-01-01" }
```
**Expected**: `400 Bad Request`.

**Vitals for non-existent appointment**:
```http
POST /api/v1/appointments/NONEXISTENT/vitals
```
**Expected**: `404 Not Found`.

---

## RBAC Matrix

| Endpoint | ADMIN | DOCTOR | NURSE | RECEPTIONIST |
|---|---|---|---|---|
| POST vitals | ✓ | ✓ | ✓ | ✗ 403 |
| GET vitals (appointment) | ✓ | ✓ | ✓ | ✗ 403 |
| GET vitals (patient list) | ✓ | ✓ | ✓ | ✗ 403 |
| POST problem | ✓ | ✓ | ✗ 403 | ✗ 403 |
| GET problems | ✓ | ✓ | ✓ | ✗ 403 |
| PATCH problem | ✓ | ✓ | ✗ 403 | ✗ 403 |
| POST medication | ✓ | ✓ | ✗ 403 | ✗ 403 |
| GET medications | ✓ | ✓ | ✓ | ✗ 403 |
| PATCH medication | ✓ | ✓ | ✗ 403 | ✗ 403 |
| POST allergy | ✓ | ✓ | ✓ | ✗ 403 |
| GET allergies | ✓ | ✓ | ✓ | ✓ |
| DELETE allergy | ✓ | ✓ | ✓ | ✗ 403 |
| GET medical-summary | ✓ | ✓ | ✗ 403 | ✗ 403 |

---

## Frontend Integration Points

| Feature | Frontend Page / Component |
|---|---|
| Record vitals | New `VitalsSection` in `AppointmentDetailPage` (NURSE/DOCTOR/ADMIN, status IN_PROGRESS) |
| View vitals history | `PatientProfilePage` new "Vitals" tab |
| Problem list | `PatientProfilePage` new "Problems" tab |
| Medication list | `PatientProfilePage` new "Medications" tab |
| Allergy list | `PatientProfilePage` new "Allergies" tab (RECEPTIONIST read-only) |
| Medical summary | New `MedicalSummaryPage` accessible from patient profile (DOCTOR/ADMIN only) |
