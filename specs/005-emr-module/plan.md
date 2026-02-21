# Implementation Plan: Electronic Medical Records (EMR) Module

**Branch**: `005-emr-module` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-emr-module/spec.md`

---

## Summary

Implement the clinical backbone of the HMS — an EMR module covering 5 user stories:
- **US1 Vitals** (P1): Record physiological measurements per appointment (upsert); paginated history per patient.
- **US2 Problem List** (P1): Persist ongoing diagnoses with lifecycle (ACTIVE → RESOLVED → INACTIVE).
- **US3 Medication List** (P1): Prescribe and manage medications with status transitions.
- **US4 Allergy Registry** (P2): Structured allergy records with soft-delete.
- **US5 Medical Summary** (P2): Aggregated clinical snapshot in a single GET.

Technical approach: extend the existing Spring Boot 3.2.x monolith (same Maven module, same package root `com.ainexus.hospital.patient`) with 4 new JPA entities, 5 new service classes, 5 new controllers, 1 shared audit service, 5 Flyway migrations (V17–V21), and full frontend UI pages/tabs on the React SPA.

---

## Technical Context

**Language/Version**: Java 17 / Spring Boot 3.2.x (Hibernate 6.4.x)
**Primary Dependencies**: Spring Data JPA, Spring Security (existing), MapStruct, Lombok, Resilience4j, Flyway, Micrometer
**Storage**: PostgreSQL 15 — 4 new entity tables + 1 audit table via Flyway V17–V21
**Testing**: JUnit 5, Mockito, Testcontainers (existing `BaseIntegrationTest`), Vitest + React Testing Library
**Target Platform**: Linux server (Docker Compose)
**Performance Goals**: Standard CRUD; < 200ms p95 for all endpoints (consistent with prior modules)
**Constraints**:
- Existing Flyway migrations V1–V16 MUST NOT be modified
- Existing entities (Patient, Appointment, Invoice, etc.) MUST NOT be modified
- Clinical Notes in appointments table are NOT replaced — EMR extends them
- `prescribedBy` is always auth-context-derived (never user-supplied)
- DOCTOR role is NOT scoped per-patient for EMR (unlike appointments/billing)
**Scale/Scope**: Same user base as existing modules (~50 concurrent users)
**Frontend**: React 18 / Vite / TanStack Query v5 / React Hook Form / Zod / Tailwind CSS — same patterns as billing module frontend

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked post-design.*

| Gate | Status | Notes |
|---|---|---|
| Spec exists and is approved | ✓ PASS | `specs/005-emr-module/spec.md` — all 15 checklist items pass |
| HIPAA-First: PHI not in logs | ✓ PASS | `emr_audit_log.details` stores field names and action context only; no clinical values |
| Test-first (TDD) | ✓ PASS | Integration tests written before implementation in task phases |
| Layered architecture | ✓ PASS | Controller → Service → Repository; DTOs at boundary; no entity leakage |
| RBAC on every endpoint | ✓ PASS | `RoleGuard.requireRoles()` called at top of every service method; 13 endpoints covered |
| No modification to frozen modules | ✓ PASS | Only new files; `AppointmentDetailPage.jsx` gets a new `VitalsSection` component (additive only) |
| Audit log for every mutation | ✓ PASS | `EmrAuditService` with `Propagation.MANDATORY` — called in every create/update/delete |

**Post-design re-check**: All gates pass. No violations requiring justification.

---

## Research Decisions (from research.md)

| Decision | Choice | Rationale |
|---|---|---|
| UUID PK strategy | `GenerationType.UUID` + `columnDefinition = "uuid"` (Hibernate 6 native) | No extra annotations; PostgreSQL native uuid type (16 bytes); matches Hibernate 6.4 |
| Vitals upsert | `findByAppointmentId()` → create/update in `@Transactional` | Matches InvoiceService pattern; clean audit integration; low write frequency |
| Audit table | Single `emr_audit_log` with `entity_type` discriminator | All EMR entities in same clinical domain; simpler cross-entity queries |
| Audit service | Single `EmrAuditService` with `Propagation.MANDATORY` | Matches InvoiceAuditService/AppointmentAuditService pattern; atomic clinical writes |
| Visit stats for summary | Query `AppointmentRepository` for COMPLETED appointments | Same JPA context; no HTTP calls; efficient JPQL aggregate query |
| `prescribedBy` | Always from `AuthContext.Holder.get().getUsername()` | Prevents forgery; consistent with auth-context pattern |
| `patient_id` width | `VARCHAR(14)` | Matches patient ID format: `P` + 4-digit year + 3+ seq digits |

---

## Project Structure

### Documentation (this feature)

```text
specs/005-emr-module/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: technical decisions
├── data-model.md        # Phase 1: entities, tables, DTOs
├── quickstart.md        # Phase 1: integration scenarios
├── contracts/
│   ├── vitals.yaml          # OpenAPI for US1
│   ├── problems.yaml        # OpenAPI for US2
│   ├── medications.yaml     # OpenAPI for US3
│   ├── allergies.yaml       # OpenAPI for US4
│   └── medical-summary.yaml # OpenAPI for US5
├── checklists/
│   └── requirements.md  # Spec quality checklist (all passed)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code

```text
backend/src/main/java/com/ainexus/hospital/patient/
├── entity/
│   ├── PatientVitals.java              # NEW — patient_vitals table
│   ├── PatientProblem.java             # NEW — patient_problems table
│   ├── PatientMedication.java          # NEW — patient_medications table
│   ├── PatientAllergy.java             # NEW — patient_allergies table
│   ├── EmrAuditLog.java                # NEW — emr_audit_log table
│   ├── ProblemSeverity.java            # NEW — enum
│   ├── ProblemStatus.java              # NEW — enum
│   ├── MedicationRoute.java            # NEW — enum
│   ├── MedicationStatus.java           # NEW — enum
│   ├── AllergyType.java                # NEW — enum
│   └── AllergySeverity.java            # NEW — enum
├── repository/
│   ├── VitalsRepository.java           # NEW
│   ├── ProblemRepository.java          # NEW
│   ├── MedicationRepository.java       # NEW
│   ├── AllergyRepository.java          # NEW
│   └── EmrAuditLogRepository.java      # NEW
├── dto/
│   ├── RecordVitalsRequest.java        # NEW
│   ├── VitalsResponse.java             # NEW
│   ├── CreateProblemRequest.java       # NEW
│   ├── UpdateProblemRequest.java       # NEW
│   ├── ProblemResponse.java            # NEW
│   ├── PrescribeMedicationRequest.java # NEW
│   ├── UpdateMedicationRequest.java    # NEW
│   ├── MedicationResponse.java         # NEW
│   ├── RecordAllergyRequest.java       # NEW
│   ├── AllergyResponse.java            # NEW
│   └── MedicalSummaryResponse.java     # NEW (aggregated)
├── mapper/
│   ├── VitalsMapper.java               # NEW — MapStruct
│   ├── ProblemMapper.java              # NEW
│   ├── MedicationMapper.java           # NEW
│   └── AllergyMapper.java             # NEW
├── validation/
│   └── AtLeastOneVitalPresent.java     # NEW — custom validator for vitals
├── service/
│   ├── VitalsService.java              # NEW
│   ├── ProblemService.java             # NEW
│   ├── MedicationService.java          # NEW
│   ├── AllergyService.java             # NEW
│   └── MedicalSummaryService.java      # NEW
├── controller/
│   ├── VitalsController.java           # NEW
│   ├── ProblemController.java          # NEW
│   ├── MedicationController.java       # NEW
│   ├── AllergyController.java          # NEW
│   └── MedicalSummaryController.java   # NEW
└── audit/
    ├── EmrAuditService.java            # NEW — Propagation.MANDATORY
    ├── EmrAuditLog.java                # NEW entity (also in entity/ — same class)
    └── EmrAuditLogRepository.java      # NEW

backend/src/main/resources/db/migration/
├── V17__create_patient_vitals.sql      # NEW
├── V18__create_patient_problems.sql    # NEW
├── V19__create_patient_medications.sql # NEW
├── V20__create_patient_allergies.sql   # NEW
└── V21__create_emr_audit_log.sql       # NEW

backend/src/test/java/com/ainexus/hospital/patient/
├── service/
│   ├── VitalsServiceTest.java          # NEW — unit tests
│   ├── ProblemServiceTest.java         # NEW
│   ├── MedicationServiceTest.java      # NEW
│   ├── AllergyServiceTest.java         # NEW
│   └── MedicalSummaryServiceTest.java  # NEW
└── integration/
    ├── VitalsIT.java                   # NEW — Testcontainers
    ├── ProblemIT.java                  # NEW
    ├── MedicationIT.java               # NEW
    ├── AllergyIT.java                  # NEW
    ├── MedicalSummaryIT.java           # NEW
    └── EmrRbacIT.java                  # NEW — full RBAC matrix (13 endpoints × 4 roles)

frontend/src/
├── api/
│   └── emrApi.js                       # NEW — axios calls for all 5 EMR areas
├── hooks/
│   └── useEmr.js                       # NEW — TanStack Query hooks
└── pages/
    ├── PatientProfilePage.jsx           # MODIFIED — add Vitals/Problems/Medications/Allergies tabs
    └── MedicalSummaryPage.jsx           # NEW — DOCTOR/ADMIN only
```

**Structure Decision**: Web application (Option 2) — existing `backend/` + `frontend/` layout. All new files are additive. No existing files are deleted or renamed. `PatientProfilePage.jsx` is the only modified existing file (new tabs added).

---

## Complexity Tracking

No constitution violations. No justification table needed.

---

## Implementation Notes

### Key Patterns to Follow

1. **Service constructor injection** (not `@Autowired` field injection) — matches all existing services
2. **`RoleGuard.requireRoles()`** at top of every service write/read method
3. **`AuthContext.Holder.get()`** for username population (recorded_by, created_by, prescribed_by)
4. **`@Transactional`** on all service write methods; `EmrAuditService` called within same transaction
5. **`Propagation.MANDATORY`** on `EmrAuditService.writeAuditLog()` — fail fast if caller forgot `@Transactional`
6. **MapStruct mappers** for entity↔DTO; never expose entities from controller layer
7. **`PatientRepository.existsById(patientId)`** check at top of all patient-scoped operations
8. **`AppointmentRepository.existsById(appointmentId)`** check before saving vitals

### Upsert Vitals Logic (Service Pseudocode)

```
recordVitals(appointmentId, request):
  roleGuard.requireRoles("NURSE", "DOCTOR", "ADMIN")
  if not appointmentRepository.existsById(appointmentId): throw 404
  patientId = appointmentRepository.findById(appointmentId).patientId
  existing = vitalsRepository.findByAppointmentId(appointmentId)
  vitals = existing.orElseGet(PatientVitals::new)
  action = existing.isPresent() ? "UPDATE" : "CREATE"
  // map all request fields onto vitals
  vitals.recordedBy = authContext.username
  vitals.recordedAt = now()
  vitalsRepository.save(vitals)
  emrAuditService.writeAuditLog("VITAL", vitals.id, patientId, action, authContext.username, null)
  return mapper.toResponse(vitals)
```

### Medical Summary Query Strategy

`MedicalSummaryService` assembles data from 5 repositories in a single `@Transactional(readOnly = true)` method:
- `problemRepository.findByPatientIdAndStatus(patientId, ACTIVE)`
- `medicationRepository.findByPatientIdAndStatus(patientId, ACTIVE)`
- `allergyRepository.findByPatientIdAndActiveTrue(patientId)`
- `vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId)`
- `appointmentRepository.countByPatientIdAndStatus(patientId, COMPLETED)`
- `appointmentRepository.findTopByPatientIdAndStatusOrderByAppointmentDateDesc(patientId, COMPLETED).appointmentDate`

### Frontend Architecture

Same pattern as billing module:
- `emrApi.js` → thin axios wrappers (one function per endpoint)
- `useEmr.js` → TanStack Query `useQuery`/`useMutation` hooks
- `PatientProfilePage.jsx` → tabbed layout (existing Info / new Vitals / Problems / Medications / Allergies)
- `MedicalSummaryPage.jsx` → route `/patients/:patientId/medical-summary` (DOCTOR/ADMIN only)
- `VitalsSection` embedded in `AppointmentDetailPage.jsx` for recording (NURSE/DOCTOR/ADMIN, appointment IN_PROGRESS)
