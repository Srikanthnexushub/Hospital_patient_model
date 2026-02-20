# Data Model: Electronic Medical Records (EMR) Module

**Branch**: `005-emr-module` | **Date**: 2026-02-20
**Phase 1 output** for `plan.md`

---

## New Tables (Flyway V17–V21)

### V17: `patient_vitals`

One record per appointment; re-POST replaces existing (upsert). All measurement columns are nullable — at least one must be non-null (enforced at service level, not DB).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGSERIAL` | PK NOT NULL | Auto-incrementing integer PK |
| `appointment_id` | `VARCHAR(14)` | NOT NULL UNIQUE FK → `appointments(appointment_id)` | One vitals record per appointment |
| `patient_id` | `VARCHAR(14)` | NOT NULL | Denormalized for query performance |
| `blood_pressure_systolic` | `INT` | NULL | mmHg |
| `blood_pressure_diastolic` | `INT` | NULL | mmHg; must be ≤ systolic when both provided |
| `heart_rate` | `INT` | NULL | bpm |
| `temperature` | `NUMERIC(4,1)` | NULL | Celsius |
| `weight` | `NUMERIC(5,2)` | NULL | kg |
| `height` | `NUMERIC(5,1)` | NULL | cm |
| `oxygen_saturation` | `INT` | NULL | 0–100% |
| `respiratory_rate` | `INT` | NULL | breaths/min |
| `recorded_by` | `VARCHAR(100)` | NOT NULL | Auth context username |
| `recorded_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | When vitals were submitted |

**Indexes**: `idx_patient_vitals_patient_id` on `(patient_id, recorded_at DESC)`

---

### V18: `patient_problems`

Persistent problem list per patient. Problems survive across appointments.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK NOT NULL DEFAULT `gen_random_uuid()` | Hibernate 6 `GenerationType.UUID` |
| `patient_id` | `VARCHAR(14)` | NOT NULL | FK reference; denormalized |
| `title` | `VARCHAR(200)` | NOT NULL | e.g., "Type 2 Diabetes" |
| `description` | `TEXT` | NULL | Free-text elaboration |
| `icd_code` | `VARCHAR(20)` | NULL | ICD-10 code (optional) |
| `severity` | `VARCHAR(20)` | NOT NULL | `MILD` \| `MODERATE` \| `SEVERE` |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE` \| `RESOLVED` \| `INACTIVE` |
| `onset_date` | `DATE` | NULL | Cannot be future-dated (service validation) |
| `notes` | `TEXT` | NULL | Clinical notes |
| `created_by` | `VARCHAR(100)` | NOT NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | |
| `updated_by` | `VARCHAR(100)` | NULL | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |

**Indexes**: `idx_patient_problems_patient_id` on `(patient_id, status)`

---

### V19: `patient_medications`

Active and historical prescriptions per patient.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK NOT NULL DEFAULT `gen_random_uuid()` | |
| `patient_id` | `VARCHAR(14)` | NOT NULL | |
| `medication_name` | `VARCHAR(200)` | NOT NULL | e.g., "Metformin" |
| `generic_name` | `VARCHAR(200)` | NULL | |
| `dosage` | `VARCHAR(100)` | NOT NULL | e.g., "500mg" |
| `frequency` | `VARCHAR(100)` | NOT NULL | e.g., "twice daily" |
| `route` | `VARCHAR(20)` | NOT NULL | `ORAL` \| `IV` \| `IM` \| `TOPICAL` \| `INHALED` \| `OTHER` |
| `start_date` | `DATE` | NOT NULL | |
| `end_date` | `DATE` | NULL | Must be ≥ `start_date` when provided |
| `indication` | `TEXT` | NULL | Reason for prescription |
| `prescribed_by` | `VARCHAR(100)` | NOT NULL | Auto-set from auth context; not user-supplied |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE` \| `DISCONTINUED` \| `COMPLETED` |
| `notes` | `TEXT` | NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |

**Indexes**: `idx_patient_medications_patient_id` on `(patient_id, status)`

---

### V20: `patient_allergies`

Structured allergy registry per patient. Soft-deleted (never hard-deleted).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK NOT NULL DEFAULT `gen_random_uuid()` | |
| `patient_id` | `VARCHAR(14)` | NOT NULL | |
| `substance` | `VARCHAR(200)` | NOT NULL | e.g., "Penicillin" |
| `type` | `VARCHAR(20)` | NOT NULL | `DRUG` \| `FOOD` \| `ENVIRONMENTAL` \| `OTHER` |
| `severity` | `VARCHAR(20)` | NOT NULL | `MILD` \| `MODERATE` \| `SEVERE` \| `LIFE_THREATENING` |
| `reaction` | `TEXT` | NOT NULL | e.g., "anaphylaxis" |
| `onset_date` | `DATE` | NULL | |
| `notes` | `TEXT` | NULL | |
| `active` | `BOOLEAN` | NOT NULL DEFAULT TRUE | False = soft-deleted |
| `created_by` | `VARCHAR(100)` | NOT NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | |
| `updated_by` | `VARCHAR(100)` | NULL | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |

**Indexes**: `idx_patient_allergies_patient_id` on `(patient_id, active)`

---

### V21: `emr_audit_log`

Single audit table for all EMR mutations. `Propagation.MANDATORY` — writes atomically with parent entity.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `BIGSERIAL` | PK NOT NULL | |
| `entity_type` | `VARCHAR(20)` | NOT NULL | `VITAL` \| `PROBLEM` \| `MEDICATION` \| `ALLERGY` |
| `entity_id` | `VARCHAR(50)` | NOT NULL | BIGINT as string for VITAL; UUID string for others |
| `patient_id` | `VARCHAR(14)` | NOT NULL | Denormalized for filtering |
| `action` | `VARCHAR(30)` | NOT NULL | `CREATE` \| `UPDATE` \| `DISCONTINUE` \| `RESOLVE` \| `DELETE` |
| `performed_by` | `VARCHAR(100)` | NOT NULL | |
| `performed_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | |
| `details` | `TEXT` | NULL | Field names or status transitions only — no PHI values |

**Indexes**:
- `idx_emr_audit_patient_id` on `(patient_id, performed_at DESC)`
- `idx_emr_audit_entity` on `(entity_type, entity_id, performed_at DESC)`

---

## Entity Relationships

```
patients (patient_id VARCHAR(14)) ─── 1:N ──> patient_vitals (patient_id)
patients                          ─── 1:N ──> patient_problems (patient_id)
patients                          ─── 1:N ──> patient_medications (patient_id)
patients                          ─── 1:N ──> patient_allergies (patient_id)

appointments (appointment_id VARCHAR(14)) ─── 1:1 ──> patient_vitals (appointment_id UNIQUE)

emr_audit_log.entity_id points to one of: patient_vitals.id | patient_problems.id | patient_medications.id | patient_allergies.id
(not enforced by FK — same pattern as existing audit tables)
```

---

## Java Entities

### Package: `com.ainexus.hospital.patient.entity`

| Entity Class | Table | PK Type | Pattern |
|---|---|---|---|
| `PatientVitals` | `patient_vitals` | `Long` (`BIGSERIAL`) | Standard JPA `GenerationType.IDENTITY` |
| `PatientProblem` | `patient_problems` | `UUID` | `GenerationType.UUID` + `columnDefinition = "uuid"` |
| `PatientMedication` | `patient_medications` | `UUID` | `GenerationType.UUID` + `columnDefinition = "uuid"` |
| `PatientAllergy` | `patient_allergies` | `UUID` | `GenerationType.UUID` + `columnDefinition = "uuid"` |
| `EmrAuditLog` | `emr_audit_log` | `Long` (`BIGSERIAL`) | `GenerationType.IDENTITY` |

### Enums (package: `com.ainexus.hospital.patient.entity`)

| Enum | Values | Used By |
|---|---|---|
| `ProblemSeverity` | `MILD`, `MODERATE`, `SEVERE` | `PatientProblem` |
| `ProblemStatus` | `ACTIVE`, `RESOLVED`, `INACTIVE` | `PatientProblem` |
| `MedicationRoute` | `ORAL`, `IV`, `IM`, `TOPICAL`, `INHALED`, `OTHER` | `PatientMedication` |
| `MedicationStatus` | `ACTIVE`, `DISCONTINUED`, `COMPLETED` | `PatientMedication` |
| `AllergyType` | `DRUG`, `FOOD`, `ENVIRONMENTAL`, `OTHER` | `PatientAllergy` |
| `AllergySeverity` | `MILD`, `MODERATE`, `SEVERE`, `LIFE_THREATENING` | `PatientAllergy` |

All enums use `@JsonValue` for serialization as display names (lowercase or exact match to spec values).

---

## Validation Rules (enforced at service layer)

| Entity | Rule | Error |
|---|---|---|
| `PatientVitals` | At least one measurement field non-null | 400 |
| `PatientVitals` | `bloodPressureDiastolic` ≤ `bloodPressureSystolic` (when both provided) | 400 |
| `PatientVitals` | `oxygenSaturation` ∈ [0, 100] (when provided) | 400 |
| `PatientVitals` | `appointmentId` must reference an existing appointment | 404 |
| `PatientProblem` | `onsetDate` cannot be in the future | 400 |
| `PatientMedication` | `endDate` ≥ `startDate` (when `endDate` provided) | 400 |
| `PatientAllergy` | `substance`, `type`, `severity`, `reaction` all required | 400 |
| Any entity | `patientId` must reference an existing patient | 404 |

---

## DTOs

### Package: `com.ainexus.hospital.patient.dto`

**Request DTOs** (records with `@NotNull` / `@Valid` on required fields):
- `RecordVitalsRequest` — 8 nullable measurement fields + custom validator `@AtLeastOneVitalPresent`
- `CreateProblemRequest` — title (required), severity (required), status (required), optional fields
- `UpdateProblemRequest` — all fields optional (partial update)
- `PrescribeMedicationRequest` — medicationName, dosage, frequency, route, startDate (required)
- `UpdateMedicationRequest` — status, endDate, notes (partial update)
- `RecordAllergyRequest` — substance, type, severity, reaction (required)

**Response DTOs**:
- `VitalsResponse` — all fields + `recordedBy`, `recordedAt`, `appointmentId`
- `ProblemResponse` — all fields + `createdBy`, `createdAt`, `updatedBy`, `updatedAt`
- `MedicationResponse` — all fields including `prescribedBy`
- `AllergyResponse` — all fields including `active`
- `MedicalSummaryResponse` — `activeProblems` (List), `activeMedications` (List), `allergies` (List), `recentVitals` (List, max 5), `lastVisitDate`, `totalVisits`

---

## Controllers

### Package: `com.ainexus.hospital.patient.controller`

| Controller | Endpoints |
|---|---|
| `VitalsController` | `POST /api/v1/appointments/{appointmentId}/vitals`, `GET /api/v1/appointments/{appointmentId}/vitals`, `GET /api/v1/patients/{patientId}/vitals` |
| `ProblemController` | `POST /api/v1/patients/{patientId}/problems`, `GET /api/v1/patients/{patientId}/problems`, `PATCH /api/v1/patients/{patientId}/problems/{problemId}` |
| `MedicationController` | `POST /api/v1/patients/{patientId}/medications`, `GET /api/v1/patients/{patientId}/medications`, `PATCH /api/v1/patients/{patientId}/medications/{medicationId}` |
| `AllergyController` | `POST /api/v1/patients/{patientId}/allergies`, `GET /api/v1/patients/{patientId}/allergies`, `DELETE /api/v1/patients/{patientId}/allergies/{allergyId}` |
| `MedicalSummaryController` | `GET /api/v1/patients/{patientId}/medical-summary` |

---

## Services

### Package: `com.ainexus.hospital.patient.service`

| Service | Dependencies |
|---|---|
| `VitalsService` | `VitalsRepository`, `AppointmentRepository`, `PatientRepository`, `EmrAuditService`, `RoleGuard`, `MeterRegistry` |
| `ProblemService` | `ProblemRepository`, `PatientRepository`, `EmrAuditService`, `RoleGuard`, `MeterRegistry` |
| `MedicationService` | `MedicationRepository`, `PatientRepository`, `EmrAuditService`, `RoleGuard`, `MeterRegistry` |
| `AllergyService` | `AllergyRepository`, `PatientRepository`, `EmrAuditService`, `RoleGuard`, `MeterRegistry` |
| `MedicalSummaryService` | `ProblemRepository`, `MedicationRepository`, `AllergyRepository`, `VitalsRepository`, `AppointmentRepository` |

### Package: `com.ainexus.hospital.patient.audit`

| Class | Purpose |
|---|---|
| `EmrAuditService` | Single audit service for all EMR entities; `Propagation.MANDATORY` |
| `EmrAuditLog` | Entity for `emr_audit_log` table |
| `EmrAuditLogRepository` | `JpaRepository<EmrAuditLog, Long>` |
