# Implementation Plan: Appointment Scheduling Module

**Branch**: `003-appointment-scheduling` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)

---

## Summary

Implement the Appointment Scheduling Module (Module 3) as an additive extension to the existing Spring Boot 3.2.x / React 18 / PostgreSQL 15 stack. The module adds 7 user stories covering appointment booking (US1), search/view (US2), status lifecycle (US3), detail updates (US4), doctor availability (US5), clinical notes (US6), and patient history (US7). It introduces 4 new database tables (Flyway V8–V11), ~18 new backend Java classes following the proven layered-architecture pattern, and 4 new React pages. No existing modules are modified; the new code composes on top of `JwtAuthFilter`, `RoleGuard`, `AuthContext`, and the existing `patients` + `hospital_users` tables.

---

## Technical Context

**Language/Version**: Java 17 (backend), JavaScript ES2022 (frontend)
**Primary Dependencies**: Spring Boot 3.2.3, Spring Data JPA / Hibernate 6, Spring Security 6, MapStruct 1.5.5, Lombok 1.18.38, Resilience4j 2.2.0, Micrometer Prometheus, jjwt 0.12.5, React 18, TanStack Query v5, React Hook Form, Zod, Tailwind CSS — **all pre-existing; zero new dependencies required**
**Storage**: PostgreSQL 15 — 4 new tables via Flyway V8–V11
**Testing**: JUnit 5 + Mockito (unit tests), Testcontainers PostgreSQL 15 (integration tests), Vitest + React Testing Library (frontend)
**Target Platform**: Existing Docker Compose stack (backend:8080, nginx:443, postgres:5432, frontend:3000)
**Performance Goals**: Appointment list p95 ≤ 200 ms; availability check p95 ≤ 100 ms; all mutations p95 ≤ 500 ms
**Constraints**: Conflict detection atomic (`SELECT FOR UPDATE`); clinical notes never in logs; AES-256 via JDK `javax.crypto` (no new library); zero double-booking tolerance; Flyway V1–V7 untouched
**Scale/Scope**: 99.9% SLA; ≥ 50 concurrent bookings without data corruption; ≤ 100 per page

---

## Constitution Check

*GATE: Must pass before implementation begins.*

| Gate | Status | Notes |
|------|--------|-------|
| Spec-Driven: `spec.md` complete and checklist all green | ✅ PASS | spec.md written; requirements.md checklist all `[X]` |
| HIPAA-First: PHI never in logs | ✅ PASS | `NotesEncryptionConverter` encrypts at JPA boundary; audit log stores field names only; `privateNotes` excluded by role |
| Test-First: TDD — tests written before implementation | ✅ PASS | Tasks plan includes unit + integration tests before each implementation phase |
| Layered Architecture: Controller → Service → Repository | ✅ PASS | No business logic in controllers; DTOs at boundary; entities not exposed in API |
| RBAC: Server-side role check on every endpoint | ✅ PASS | `roleGuard.requireRoles(...)` called at start of every service method |
| No new tech stack additions | ✅ PASS | AES-256 uses JDK 17 built-in `javax.crypto`; no new Maven dependencies |
| Existing modules untouched | ✅ PASS | Flyway V1–V7 frozen; no edits to Patient, Auth entities, services, or controllers |
| SecurityConfig: new endpoints automatically protected | ✅ PASS | `.anyRequest().authenticated()` covers all new `/api/v1/appointments/**` and `/api/v1/doctors/**` paths |

---

## Project Structure

### Documentation (this feature)

```text
specs/003-appointment-scheduling/
├── spec.md              ✅ complete
├── plan.md              ✅ this file
├── research.md          ✅ Phase 0 complete
├── data-model.md        ✅ Phase 1 complete
├── quickstart.md        ✅ Phase 1 complete
├── contracts/
│   ├── appointments.yml     ✅ Phase 1 complete
│   └── clinical-notes.yml   ✅ Phase 1 complete
├── checklists/
│   └── requirements.md  ✅ all green
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code — Backend (new files only)

```text
backend/src/main/java/com/ainexus/hospital/patient/

├── controller/
│   ├── AppointmentController.java
│   │   Endpoints: POST /appointments, GET /appointments, GET /appointments/today,
│   │              GET /appointments/{id}, PATCH /appointments/{id},
│   │              PATCH /appointments/{id}/status
│   │              GET /patients/{patientId}/appointments
│   ├── DoctorScheduleController.java
│   │   Endpoints: GET /doctors/{doctorId}/schedule,
│   │              GET /doctors/{doctorId}/availability
│   └── ClinicalNotesController.java
│       Endpoints: POST /appointments/{id}/notes,
│                  GET  /appointments/{id}/notes

├── service/
│   ├── AppointmentService.java
│   │   Methods: bookAppointment, listAppointments, getTodayAppointments,
│   │            getAppointment, updateAppointment, changeStatus,
│   │            getPatientAppointmentHistory
│   ├── AppointmentIdGeneratorService.java
│   │   Methods: generateAppointmentId(year), generateAppointmentId()
│   │   Pattern: mirrors PatientIdGeneratorService exactly
│   ├── DoctorAvailabilityService.java
│   │   Methods: getSchedule(doctorId, date), getAvailability(doctorId, date)
│   └── ClinicalNotesService.java
│       Methods: upsertNotes(appointmentId, request), getNotes(appointmentId)

├── repository/
│   ├── AppointmentRepository.java
│   │   Custom queries: findOverlappingAppointments (PESSIMISTIC_WRITE),
│   │                   search with optional filters,
│   │                   findByDoctorIdAndAppointmentDate
│   ├── AppointmentIdSequenceRepository.java
│   │   Custom query: findByYearForUpdate (mirrors PatientIdSequenceRepository)
│   ├── AppointmentAuditLogRepository.java
│   └── ClinicalNotesRepository.java

├── entity/
│   ├── Appointment.java             @Entity, @Table("appointments"), @Version
│   ├── AppointmentIdSequence.java   @Entity, @Table("appointment_id_sequences")
│   ├── AppointmentAuditLog.java     @Entity, @Table("appointment_audit_log"), BIGSERIAL PK
│   ├── ClinicalNotes.java           @Entity, @Table("clinical_notes"), appointmentId as PK
│   ├── AppointmentStatus.java       Enum: SCHEDULED|CONFIRMED|CHECKED_IN|IN_PROGRESS|COMPLETED|CANCELLED|NO_SHOW
│   ├── AppointmentType.java         Enum: GENERAL_CONSULTATION|FOLLOW_UP|SPECIALIST|EMERGENCY|ROUTINE_CHECKUP|PROCEDURE
│   └── AppointmentAction.java       Enum: CONFIRM|CHECK_IN|START|COMPLETE|CANCEL|NO_SHOW

├── dto/
│   ├── request/
│   │   ├── BookAppointmentRequest.java         Java record with Bean Validation
│   │   ├── UpdateAppointmentRequest.java       Java record; all fields nullable
│   │   ├── AppointmentStatusChangeRequest.java Java record; action + optional reason
│   │   └── ClinicalNotesRequest.java           Java record; all fields nullable
│   └── response/
│       ├── AppointmentResponse.java            Full detail including patientName, doctorName
│       ├── AppointmentSummaryResponse.java     For list views
│       ├── AppointmentStatusChangeResponse.java
│       ├── AvailabilityResponse.java           doctorId, doctorName, date, slots[]
│       ├── TimeSlotResponse.java               startTime, endTime, available, appointmentId?
│       └── ClinicalNotesResponse.java          All note fields; service nulls privateNotes for RECEPTIONIST/NURSE

├── mapper/
│   ├── AppointmentMapper.java       MapStruct: Appointment ↔ AppointmentResponse/Summary
│   └── ClinicalNotesMapper.java     MapStruct: ClinicalNotes ↔ ClinicalNotesResponse

├── audit/
│   └── AppointmentAuditService.java
│       Method: writeAuditLog(appointmentId, action, fromStatus, toStatus, performedBy, details)

├── config/
│   └── NotesEncryptionConfig.java
│       @Bean SecretKeySpec notesEncryptionKey() — reads APP_NOTES_ENCRYPTION_KEY env var

├── validation/
│   └── NotesEncryptionConverter.java
│       AttributeConverter<String, String>: AES/GCM/NoPadding, random 12-byte IV per encrypt

└── exception/
    └── AppointmentNotFoundException.java

backend/src/main/resources/db/migration/
├── V8__create_appointments.sql
├── V9__create_appointment_id_sequences.sql
├── V10__create_appointment_audit_log.sql
└── V11__create_clinical_notes.sql

backend/src/test/java/com/ainexus/hospital/patient/
├── integration/
│   ├── AppointmentLifecycleIT.java      Scenario 1: full lifecycle
│   ├── AppointmentConflictIT.java       Scenario 2: conflict detection
│   ├── AppointmentRbacIT.java           Scenario 3: RBAC matrix
│   ├── DoctorAvailabilityIT.java        Scenario 4: availability
│   ├── AppointmentUpdateIT.java         Scenario 5: update + optimistic lock
│   ├── ClinicalNotesIT.java             Scenario 6: notes CRUD + access control
│   ├── PatientAppointmentHistoryIT.java Scenario 7: patient history
│   └── AppointmentCancellationIT.java   Scenario 8: cancel/no-show
└── unit/
    ├── service/
    │   ├── AppointmentServiceTest.java
    │   ├── AppointmentIdGeneratorServiceTest.java
    │   ├── DoctorAvailabilityServiceTest.java
    │   └── ClinicalNotesServiceTest.java
    └── validation/
        └── NotesEncryptionConverterTest.java
```

### Source Code — Frontend (new files only)

```text
frontend/src/
├── api/
│   └── appointmentApi.js
│       Functions: bookAppointment, listAppointments, getTodayAppointments,
│                  getAppointment, updateAppointment, changeAppointmentStatus,
│                  getDoctorAvailability, getDoctorSchedule,
│                  getPatientAppointments, upsertClinicalNotes, getClinicalNotes

├── pages/
│   ├── AppointmentListPage.jsx       Today's schedule + search/filter (US2)
│   ├── AppointmentBookingPage.jsx    Booking form with availability check (US1 + US5)
│   ├── AppointmentDetailPage.jsx     Full detail + status action buttons (US3)
│   └── DoctorAvailabilityPage.jsx    30-minute slot grid (US5)

├── components/
│   └── appointment/
│       ├── AppointmentCard.jsx
│       ├── AppointmentStatusBadge.jsx  Color-coded status chip
│       ├── StatusActionButton.jsx      Role-aware action buttons (cosmetic guard only)
│       └── TimeSlotGrid.jsx            20-slot availability grid

└── hooks/
    └── useAppointments.js
        TanStack Query v5 hooks: useAppointments, useAppointment, useBookAppointment,
        useUpdateAppointment, useChangeAppointmentStatus, useDoctorAvailability,
        useClinicalNotes, useUpsertClinicalNotes
```

---

## Key Implementation Details

### Conflict Detection (Critical Path)

```java
// AppointmentRepository.java
@Query("""
    SELECT a FROM Appointment a
    WHERE a.doctorId = :doctorId
    AND a.appointmentDate = :date
    AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
    AND a.startTime < :endTime
    AND a.endTime > :startTime
    AND (:excludeId IS NULL OR a.appointmentId != :excludeId)
    """)
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Appointment> findOverlappingAppointments(
    @Param("doctorId") String doctorId,
    @Param("date") LocalDate date,
    @Param("startTime") LocalTime startTime,
    @Param("endTime") LocalTime endTime,
    @Param("excludeId") String excludeId   // null for new bookings; appointmentId for updates
);
```

The `@Lock(PESSIMISTIC_WRITE)` issues `SELECT FOR UPDATE` in PostgreSQL, serializing concurrent booking attempts for the same doctor+slot combination.

### State Machine Enforcement

```java
// AppointmentService.java — inside changeStatus()
private static final Map<AppointmentAction, Set<AppointmentStatus>> VALID_TRANSITIONS = Map.of(
    AppointmentAction.CONFIRM,   Set.of(AppointmentStatus.SCHEDULED),
    AppointmentAction.CHECK_IN,  Set.of(AppointmentStatus.CONFIRMED),
    AppointmentAction.START,     Set.of(AppointmentStatus.CHECKED_IN),
    AppointmentAction.COMPLETE,  Set.of(AppointmentStatus.IN_PROGRESS),
    AppointmentAction.CANCEL,    Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED,
                                        AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS),
    AppointmentAction.NO_SHOW,   Set.of(AppointmentStatus.CONFIRMED)
);

private static final Map<AppointmentAction, Set<String>> ACTION_ROLES = Map.of(
    AppointmentAction.CONFIRM,   Set.of("RECEPTIONIST", "ADMIN"),
    AppointmentAction.CHECK_IN,  Set.of("RECEPTIONIST", "NURSE", "ADMIN"),
    AppointmentAction.START,     Set.of("DOCTOR", "ADMIN"),
    AppointmentAction.COMPLETE,  Set.of("DOCTOR", "ADMIN"),
    AppointmentAction.CANCEL,    Set.of("RECEPTIONIST", "ADMIN", "DOCTOR"),
    AppointmentAction.NO_SHOW,   Set.of("RECEPTIONIST", "ADMIN")
);
// Admin escape hatch: ADMIN can cancel from ANY status (checked first)
```

### AES-256 Encryption Converter

```java
// NotesEncryptionConverter.java
@Converter
@Component
public class NotesEncryptionConverter implements AttributeConverter<String, String> {
    // Algorithm: AES/GCM/NoPadding, 256-bit key, 12-byte random IV per encrypt
    // Storage format: Base64(IV || ciphertext)
    // Null passthrough: null plaintext → null ciphertext (avoids encrypting null)
}
```

### Doctor Role Filtering

```java
// AppointmentService.listAppointments()
AuthContext ctx = AuthContext.Holder.get();
if ("DOCTOR".equals(ctx.getRole())) {
    // Force filter to own appointments — cannot be overridden by request param
    effectiveDoctorId = ctx.getUserId();
}
```

### Appointment ID Format

```
APT + YYYY + 4-digit zero-padded sequence
Examples: APT20260001, APT20260099, APT20269999
Past 9999: APT202610000 (natural expansion, within VARCHAR(14))
```

---

## Environment Variables (new for Module 3)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `APP_NOTES_ENCRYPTION_KEY` | **Required** | — | Base64-encoded 32-byte AES-256 key. Generate: `openssl rand -base64 32` |

**`docker-compose.yml` backend service** must add:
```yaml
APP_NOTES_ENCRYPTION_KEY: ${APP_NOTES_ENCRYPTION_KEY}
```

**`.env`** must add:
```
APP_NOTES_ENCRYPTION_KEY=<output of openssl rand -base64 32>
```

---

## Complexity Tracking

No constitution violations. All design decisions follow existing patterns:

| Pattern | Module 1 Precedent | Module 3 Use |
|---------|-------------------|--------------|
| ID generation with SELECT FOR UPDATE | `PatientIdGeneratorService` | `AppointmentIdGeneratorService` |
| Immutable audit log (BIGSERIAL, no FK) | `PatientAuditLog` | `AppointmentAuditLog` |
| Optimistic locking via `@Version` + If-Match | `Patient.version` | `Appointment.version` |
| Service-layer RBAC via `RoleGuard` | `PatientService` | `AppointmentService` |
| DTOs as Java records | Module 2 auth DTOs | All new request/response DTOs |
| Layered: Controller → Service → Repository | All existing code | All new code |

The only novel pattern is `NotesEncryptionConverter` (JPA `AttributeConverter`). This is a standard Spring Data JPA mechanism using JDK built-in crypto — no new library, no new infrastructure.
