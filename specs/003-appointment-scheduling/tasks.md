# Tasks: Appointment Scheduling Module

**Input**: Design documents from `/specs/003-appointment-scheduling/`
**Branch**: `003-appointment-scheduling`
**Generated**: 2026-02-20

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to
- Exact file paths are provided in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Environment configuration, shared enums, and encryption infrastructure needed by every user story.

- [X] T001 Add `APP_NOTES_ENCRYPTION_KEY=<openssl rand -base64 32 output>` to `.env`
- [X] T002 Add `APP_NOTES_ENCRYPTION_KEY: ${APP_NOTES_ENCRYPTION_KEY}` to backend service `environment:` block in `docker-compose.yml`
- [X] T003 Add `APP_NOTES_ENCRYPTION_KEY=` placeholder to `.env.example`
- [X] T004 [P] Create `AppointmentStatus` enum (SCHEDULED, CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW) in `backend/src/main/java/com/ainexus/hospital/patient/entity/AppointmentStatus.java`
- [X] T005 [P] Create `AppointmentType` enum (GENERAL_CONSULTATION, FOLLOW_UP, SPECIALIST, EMERGENCY, ROUTINE_CHECKUP, PROCEDURE) in `backend/src/main/java/com/ainexus/hospital/patient/entity/AppointmentType.java`
- [X] T006 [P] Create `AppointmentAction` enum (CONFIRM, CHECK_IN, START, COMPLETE, CANCEL, NO_SHOW) in `backend/src/main/java/com/ainexus/hospital/patient/entity/AppointmentAction.java`
- [X] T007 [P] Create `AppointmentNotFoundException` extending `RuntimeException` with constructor `(String appointmentId)` in `backend/src/main/java/com/ainexus/hospital/patient/exception/AppointmentNotFoundException.java`
- [X] T008 Add `@ExceptionHandler(AppointmentNotFoundException.class)` returning HTTP 404 to `GlobalExceptionHandler` in `backend/src/main/java/com/ainexus/hospital/patient/exception/GlobalExceptionHandler.java`
- [X] T009 Create `NotesEncryptionConfig` `@Configuration` class that reads `APP_NOTES_ENCRYPTION_KEY` env var (base64-decoded to 32 bytes) and exposes it as `@Bean SecretKeySpec notesEncryptionKey()` in `backend/src/main/java/com/ainexus/hospital/patient/config/NotesEncryptionConfig.java`
- [X] T010 Create `NotesEncryptionConverter` `@Component` implementing `AttributeConverter<String, String>`: `convertToDatabaseColumn` encrypts with `AES/GCM/NoPadding` + random 12-byte IV, stores `Base64(IV || ciphertext)`; `convertToEntityAttribute` decrypts; null passthrough; `@Autowired` `SecretKeySpec notesEncryptionKey` in `backend/src/main/java/com/ainexus/hospital/patient/validation/NotesEncryptionConverter.java`

**Checkpoint**: Enums, encryption infrastructure, and env vars ready.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database migrations + core entities + shared services that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T011 Create `V8__create_appointments.sql`: `appointments` table with all columns from data-model.md — `appointment_id VARCHAR(14) NOT NULL PK`, `patient_id VARCHAR(12) NOT NULL FK→patients`, `doctor_id VARCHAR(12) NOT NULL`, `appointment_date DATE NOT NULL`, `start_time TIME NOT NULL`, `end_time TIME NOT NULL`, `duration_minutes INT NOT NULL CHECK IN(15,30,45,60,90,120)`, `type VARCHAR(30) NOT NULL CHECK IN(...)`, `status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK IN(...)`, `reason TEXT`, `notes TEXT`, `cancel_reason TEXT`, `created_at/by`, `updated_at/by`, `version INT DEFAULT 0`; indexes: `idx_appointments_doctor_date(doctor_id, appointment_date)`, `idx_appointments_patient_id(patient_id)`, `idx_appointments_status(status)`, `idx_appointments_date_status(appointment_date, status)` in `backend/src/main/resources/db/migration/V8__create_appointments.sql`
- [X] T012 Create `V9__create_appointment_id_sequences.sql`: table `appointment_id_sequences(year INT NOT NULL PK, last_sequence INT NOT NULL DEFAULT 0)` in `backend/src/main/resources/db/migration/V9__create_appointment_id_sequences.sql`
- [X] T013 Create `V10__create_appointment_audit_log.sql`: table `appointment_audit_log(id BIGSERIAL PK, appointment_id VARCHAR(14) NOT NULL (NOT a FK), action VARCHAR(20) NOT NULL CHECK IN(CREATE,UPDATE,CONFIRM,CHECK_IN,START,COMPLETE,CANCEL,NO_SHOW,NOTE_ADDED,NOTE_UPDATED), from_status VARCHAR(20), to_status VARCHAR(20), performed_by VARCHAR(50) NOT NULL, performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), details TEXT)`; comment: append-only in `backend/src/main/resources/db/migration/V10__create_appointment_audit_log.sql`
- [X] T014 Create `V11__create_clinical_notes.sql`: table `clinical_notes(appointment_id VARCHAR(14) PK FK→appointments, chief_complaint TEXT, diagnosis TEXT, treatment TEXT, prescription TEXT, follow_up_required BOOL NOT NULL DEFAULT FALSE, follow_up_days INT, private_notes TEXT, created_by VARCHAR(50) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())` in `backend/src/main/resources/db/migration/V11__create_clinical_notes.sql`
- [X] T015 [P] Create `Appointment` JPA entity: `@Entity @Table("appointments")`, `@Id appointmentId VARCHAR(14)`, all fields typed with `LocalDate`, `LocalTime`, `@Enumerated(EnumType.STRING)` for `AppointmentStatus`/`AppointmentType`, `@Version Integer version`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` in `backend/src/main/java/com/ainexus/hospital/patient/entity/Appointment.java`
- [X] T016 [P] Create `AppointmentIdSequence` JPA entity: `@Entity @Table("appointment_id_sequences")`, `@Id Integer year`, `Integer lastSequence` — mirrors `PatientIdSequence` exactly in `backend/src/main/java/com/ainexus/hospital/patient/entity/AppointmentIdSequence.java`
- [X] T017 [P] Create `AppointmentAuditLog` JPA entity: `@Entity @Table("appointment_audit_log")`, `@Id @GeneratedValue(IDENTITY) Long id`, `String appointmentId`, `String action`, `String fromStatus`, `String toStatus`, `String performedBy`, `OffsetDateTime performedAt`, `String details`; Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` in `backend/src/main/java/com/ainexus/hospital/patient/entity/AppointmentAuditLog.java`
- [X] T018 Create `AppointmentIdSequenceRepository` interface extending `JpaRepository<AppointmentIdSequence, Integer>` with `@Query("SELECT s FROM AppointmentIdSequence s WHERE s.year = :year") @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<AppointmentIdSequence> findByYearForUpdate(@Param("year") Integer year)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentIdSequenceRepository.java`
- [X] T019 [P] Create `AppointmentAuditLogRepository` interface extending `JpaRepository<AppointmentAuditLog, Long>` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentAuditLogRepository.java`
- [X] T020 Create `AppointmentIdGeneratorService` `@Service`: `@Transactional generateAppointmentId(Integer year)` — SELECT FOR UPDATE on sequence row, increment, format `APT` + year + 4-digit zero-padded seq (e.g. `APT20260001`; past 9999: natural expansion); no-arg `generateAppointmentId()` overload uses `LocalDate.now().getYear()` — mirrors `PatientIdGeneratorService` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentIdGeneratorService.java`
- [X] T021 Create `AppointmentAuditService` `@Service` with `writeAuditLog(String appointmentId, String action, String fromStatus, String toStatus, String performedBy, String details)` — builds and saves `AppointmentAuditLog` entry; null-safe for fromStatus/toStatus in `backend/src/main/java/com/ainexus/hospital/patient/audit/AppointmentAuditService.java`
- [X] T022 [P] Create `AppointmentResponse` Java record: `(String appointmentId, String patientId, String patientName, String doctorId, String doctorName, LocalDate appointmentDate, LocalTime startTime, LocalTime endTime, Integer durationMinutes, AppointmentType type, AppointmentStatus status, String reason, String notes, String cancelReason, Integer version, OffsetDateTime createdAt, String createdBy, OffsetDateTime updatedAt, String updatedBy)` in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/AppointmentResponse.java`
- [X] T023 [P] Create `AppointmentSummaryResponse` Java record: `(String appointmentId, String patientId, String patientName, String doctorId, String doctorName, LocalDate appointmentDate, LocalTime startTime, LocalTime endTime, Integer durationMinutes, AppointmentType type, AppointmentStatus status)` in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/AppointmentSummaryResponse.java`
- [X] T024 Create `AppointmentMapper` `@Mapper(componentModel = "spring")`: `toResponse(Appointment a, String patientName, String doctorName)` → `AppointmentResponse`; `toSummary(Appointment a, String patientName, String doctorName)` → `AppointmentSummaryResponse` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/AppointmentMapper.java`
- [X] T025 Update `BaseIntegrationTest.java` `@BeforeEach` to also truncate (in reverse FK order): `TRUNCATE TABLE clinical_notes CASCADE`, `TRUNCATE TABLE appointment_audit_log RESTART IDENTITY CASCADE`, `TRUNCATE TABLE appointment_id_sequences CASCADE`, `TRUNCATE TABLE appointments CASCADE` — add these before the existing patient table truncations in `backend/src/test/java/com/ainexus/hospital/patient/integration/BaseIntegrationTest.java`

**Checkpoint**: All migrations, entities, repositories, and shared services ready. User story implementation can begin.

---

## Phase 3: User Story 1 — Book Appointment (Priority: P1) 🎯 MVP

**Goal**: RECEPTIONIST/ADMIN can book an appointment for an active patient with an active doctor. System generates `APT20260001`-style ID, detects conflicts atomically, and returns the full appointment.

**Independent Test**: POST /api/v1/appointments with valid patient/doctor → 201 with APT ID. POST with overlapping slot → 409. POST as NURSE → 403.

- [X] T026 [US1] Create `BookAppointmentRequest` Java record with validation: `@NotBlank String patientId`, `@NotBlank String doctorId`, `@NotNull LocalDate appointmentDate`, `@NotNull LocalTime startTime`, `@NotNull Integer durationMinutes` (validated against allowed set [15,30,45,60,90,120] via custom validator or `@Pattern`), `@NotNull AppointmentType type`, `@NotBlank String reason`, `String notes` (nullable) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/BookAppointmentRequest.java`
- [X] T027 [US1] Create `AppointmentRepository` interface extending `JpaRepository<Appointment, String>` with: `@Query @Lock(PESSIMISTIC_WRITE) List<Appointment> findOverlappingAppointments(@Param("doctorId"), @Param("date"), @Param("startTime"), @Param("endTime"), @Param("excludeId") String excludeId)` — JPQL: `status NOT IN ('CANCELLED','NO_SHOW') AND startTime < :endTime AND endTime > :startTime AND (:excludeId IS NULL OR appointmentId != :excludeId)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T028 [US1] Implement `AppointmentService.bookAppointment(BookAppointmentRequest)`: `roleGuard.requireRoles("RECEPTIONIST","ADMIN")`, load patient via `PatientRepository` (throw 404 if missing, throw 409 if not ACTIVE), load doctor via `HospitalUserRepository` (throw 404 if missing, throw 409 if role≠DOCTOR or status≠ACTIVE), compute `endTime = startTime + durationMinutes`, call `findOverlappingAppointments` (throw 409 on overlap), generate ID via `AppointmentIdGeneratorService`, set all audit fields, save, call `AppointmentAuditService.writeAuditLog(id, "CREATE", null, "SCHEDULED", performer, null)`, return `AppointmentResponse` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T029 [US1] Implement `AppointmentController` `POST /api/v1/appointments`: `@PostMapping`, `@Valid @RequestBody BookAppointmentRequest`, delegate to `AppointmentService.bookAppointment()`, return `ResponseEntity.status(201).body(...)` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T030 [US1] Unit test `AppointmentServiceTest`: mock repos and services; test bookAppointment happy path generates correct ID; test inactive patient → ConflictException; test non-existent patient → PatientNotFoundException; test non-DOCTOR user → ConflictException; test overlapping slot → ConflictException; test NURSE role → ForbiddenException in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/AppointmentServiceTest.java`
- [X] T031 [US1] Integration test `AppointmentBookingIT`: happy path → 201 with `APT2026xxxx`; overlapping slot → 409; back-to-back (no gap) → 201; inactive patient → 409; non-existent patient → 404; non-existent doctor → 404; NURSE token → 403; invalid durationMinutes → 400 in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentBookingIT.java`
- [X] T032 [P] [US1] Create frontend `appointmentApi.js` with `bookAppointment(data)` function calling `POST /api/v1/appointments`; import and reuse the shared Axios `api` instance from `patientApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T033 [P] [US1] Create frontend `useAppointments.js` with `useBookAppointment()` TanStack Query `useMutation` hook (invalidates `['appointments']` on success) in `frontend/src/hooks/useAppointments.js`
- [X] T034 [US1] Create `AppointmentBookingPage.jsx`: react-hook-form with zod schema (patientId, doctorId, appointmentDate, startTime, durationMinutes, type, reason); submit via `useBookAppointment`; display success with appointment ID; display field errors from 400 response in `frontend/src/pages/AppointmentBookingPage.jsx`

**Checkpoint**: US1 fully functional — can book appointments via API and UI.

---

## Phase 4: User Story 2 — View & Search Appointments (Priority: P1)

**Goal**: Any authenticated staff can list, filter, and view appointments. DOCTOR auto-filtered to own. Today shortcut, doctor schedule view, full detail with patient/doctor names.

**Independent Test**: List as ADMIN returns all; list as DOCTOR returns own only; detail endpoint returns patientName + doctorName; today shortcut returns only today's.

- [X] T035 [US2] Add multi-filter search query to `AppointmentRepository`: JPQL `findWithFilters` accepting optional `doctorId`, `patientId`, `date` (exact), `dateFrom`, `dateTo`, `status`, `type` — use `Specification` or `@Query` with `:param IS NULL OR field = :param` pattern; sorted by `appointmentDate ASC, startTime ASC`; `Pageable` support in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T036 [US2] Add `findByDoctorIdAndAppointmentDate(String doctorId, LocalDate date, Pageable pageable)` Spring Data method to `AppointmentRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T037 [US2] Add `findByPatientIdOrderByAppointmentDateDesc(String patientId, Pageable pageable)` Spring Data method to `AppointmentRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T038 [US2] Implement `AppointmentService.listAppointments(filters, page, size)`: `roleGuard.requireAuthenticated()`; if `ctx.getRole().equals("DOCTOR")` force `effectiveDoctorId = ctx.getUserId()`; delegate to repository `findWithFilters`; resolve patientName+doctorName per record via batch lookup; return `PagedResponse<AppointmentSummaryResponse>` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T039 [US2] Implement `AppointmentService.getTodayAppointments(page, size)`: delegates to `listAppointments` with `date = LocalDate.now()`, same DOCTOR scope restriction in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T040 [US2] Implement `AppointmentService.getAppointment(String appointmentId)`: `roleGuard.requireAuthenticated()`; load appointment (throw `AppointmentNotFoundException`); resolve patient/doctor names; return `AppointmentResponse` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T041 [US2] Implement `AppointmentService.getDoctorSchedule(String doctorId, LocalDate date, int page, int size)`: `requireAuthenticated()`; if DOCTOR role, guard that `doctorId == ctx.getUserId()` (throw `ForbiddenException` if not); delegate to `findByDoctorIdAndAppointmentDate`; return `PagedResponse<AppointmentSummaryResponse>` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T042 [US2] Add `GET /api/v1/appointments` endpoint to `AppointmentController` with `@RequestParam` filter params (doctorId, patientId, date, dateFrom, dateTo, status, type, page, size) in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T043 [US2] Add `GET /api/v1/appointments/today` endpoint to `AppointmentController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T044 [US2] Add `GET /api/v1/appointments/{appointmentId}` endpoint to `AppointmentController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T045 [US2] Create `DoctorScheduleController` `@RestController @RequestMapping("/api/v1/doctors")` with `GET /{doctorId}/schedule` delegating to `AppointmentService.getDoctorSchedule()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/DoctorScheduleController.java`
- [X] T046 [US2] Unit test `AppointmentServiceTest`: `listAppointments` with DOCTOR role forces doctorId filter; `getTodayAppointments` uses today's date; `getAppointment` throws on missing ID; `getDoctorSchedule` with DOCTOR viewing other doctor's schedule throws `ForbiddenException` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/AppointmentServiceTest.java`
- [X] T047 [US2] Integration test `AppointmentSearchIT`: seed 5 appointments across 2 doctors; ADMIN list returns all 5; DOCTOR list returns own 3; `today` returns today's 2; filter by `status=CONFIRMED` returns 1; filter by `doctorId` returns matching; detail endpoint returns `patientName` and `doctorName` in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentSearchIT.java`
- [X] T048 [P] [US2] Add `listAppointments`, `getTodayAppointments`, `getAppointment`, `getDoctorSchedule` functions to `appointmentApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T049 [US2] Add `useAppointments(filters)`, `useTodayAppointments()`, `useAppointment(id)` TanStack Query hooks to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T050 [P] [US2] Create `AppointmentStatusBadge.jsx` component: color-coded chip per status (SCHEDULED=blue, CONFIRMED=indigo, CHECKED_IN=yellow, IN_PROGRESS=orange, COMPLETED=green, CANCELLED=red, NO_SHOW=gray) in `frontend/src/components/appointment/AppointmentStatusBadge.jsx`
- [X] T051 [P] [US2] Create `AppointmentCard.jsx` component: displays summary row (date, time, patient name, doctor name, type, `AppointmentStatusBadge`) in `frontend/src/components/appointment/AppointmentCard.jsx`
- [X] T052 [P] [US2] Create `AppointmentListPage.jsx`: date filter input, status dropdown, doctorId filter (hidden for DOCTOR role), paginated list of `AppointmentCard`, links to detail; uses `useAppointments` and `useTodayAppointments` in `frontend/src/pages/AppointmentListPage.jsx`
- [X] T053 [P] [US2] Create `AppointmentDetailPage.jsx`: shows full `AppointmentResponse` fields (patient info, doctor info, date/time, status badge, reason, notes, audit timestamps); placeholder for status action buttons (US3) and clinical notes section (US6) in `frontend/src/pages/AppointmentDetailPage.jsx`

**Checkpoint**: US1 + US2 complete — appointments can be booked, listed, filtered, and viewed.

---

## Phase 5: User Story 3 — Appointment Status Lifecycle (Priority: P2)

**Goal**: Staff can move appointments through the state machine. Transitions validated server-side by role. Every change produces an audit log entry. Cancellation requires a reason.

**Independent Test**: Walk SCHEDULED→CONFIRMED→CHECKED_IN→IN_PROGRESS→COMPLETED as correct roles; all 5 audit entries present. Invalid transition → 409. Wrong role → 403. Cancel without reason → 400.

- [X] T054 [US3] Create `AppointmentStatusChangeRequest` Java record: `@NotNull AppointmentAction action`, `String reason` (nullable in bean, validated in service) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/AppointmentStatusChangeRequest.java`
- [X] T055 [US3] Create `AppointmentStatusChangeResponse` Java record: `(String appointmentId, AppointmentStatus previousStatus, AppointmentStatus newStatus, String message)` in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/AppointmentStatusChangeResponse.java`
- [X] T056 [US3] Implement `AppointmentService.changeStatus(String appointmentId, AppointmentStatusChangeRequest)`: load appointment (404 if missing); define `VALID_TRANSITIONS: Map<AppointmentAction, Set<AppointmentStatus>>` and `ACTION_ROLES: Map<AppointmentAction, Set<String>>` as static finals; ADMIN escape hatch: if role=ADMIN and action=CANCEL, allow any status; validate transition (throw `ConflictException` on invalid); validate role (throw `ForbiddenException`); require non-blank reason for CANCEL; set new status + cancelReason + updatedAt/By; save; write audit log (`action.name()`, fromStatus, toStatus); return `AppointmentStatusChangeResponse` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T057 [US3] Add `PATCH /api/v1/appointments/{appointmentId}/status` endpoint to `AppointmentController` delegating to `AppointmentService.changeStatus()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T058 [US3] Unit test `AppointmentServiceTest`: full state machine — each valid transition succeeds; each invalid transition throws `ConflictException`; RECEPTIONIST cannot START (→`ForbiddenException`); CANCEL without reason → `ConflictException`; ADMIN cancels COMPLETED appointment (escape hatch) in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/AppointmentServiceTest.java`
- [X] T059 [US3] Integration test `AppointmentLifecycleIT`: full happy path SCHEDULED→CONFIRMED→CHECKED_IN→IN_PROGRESS→COMPLETED; verify 5 audit entries; CANCEL with reason; CANCEL without reason → 400; NO_SHOW from CONFIRMED; ADMIN cancels COMPLETED → 200 in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentLifecycleIT.java`
- [X] T060 [US3] Integration test `AppointmentRbacIT`: for each action (CONFIRM, CHECK_IN, START, COMPLETE, CANCEL, NO_SHOW) test all 4 roles (RECEPTIONIST, DOCTOR, NURSE, ADMIN) — verify allowed roles return 200, forbidden roles return 403 in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentRbacIT.java`
- [X] T061 [P] [US3] Add `changeAppointmentStatus(appointmentId, { action, reason })` function to `appointmentApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T062 [P] [US3] Add `useChangeAppointmentStatus()` TanStack Query mutation hook (invalidates `['appointments', id]` on success) to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T063 [US3] Create `StatusActionButton.jsx` component: given current `status` and user `role`, renders the allowed action buttons (cosmetic guard mirrors server RBAC); triggers `useChangeAppointmentStatus` mutation; confirms cancel via prompt for reason input in `frontend/src/components/appointment/StatusActionButton.jsx`
- [X] T064 [US3] Update `AppointmentDetailPage.jsx` to render `<StatusActionButton>` component below the status display; refresh appointment data on successful mutation in `frontend/src/pages/AppointmentDetailPage.jsx`

**Checkpoint**: US1 + US2 + US3 complete — full appointment lifecycle operational.

---

## Phase 6: User Story 4 — Update Appointment Details (Priority: P2)

**Goal**: RECEPTIONIST/ADMIN can update mutable fields (date, time, duration, type, reason, notes) on SCHEDULED/CONFIRMED appointments. Conflict detection re-runs on reschedule. Optimistic locking via If-Match.

**Independent Test**: Reschedule from 09:00 to 10:00 succeeds (slot free). Reschedule to occupied slot → 409. Stale If-Match → 409. Update IN_PROGRESS → 400/409.

- [X] T065 [US4] Create `UpdateAppointmentRequest` Java record with all fields nullable: `LocalDate appointmentDate`, `LocalTime startTime`, `Integer durationMinutes`, `AppointmentType type`, `String reason`, `String notes` in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/UpdateAppointmentRequest.java`
- [X] T066 [US4] Implement `AppointmentService.updateAppointment(String appointmentId, UpdateAppointmentRequest, Integer version)`: `roleGuard.requireRoles("RECEPTIONIST","ADMIN")`; load appointment (404); verify `status IN (SCHEDULED, CONFIRMED)` else throw `ConflictException("Appointment cannot be updated in current status")`; verify `appointment.getVersion().equals(version)` else throw `ConflictException("Version conflict")`; if date/time/duration changing, rerun `findOverlappingAppointments` with `excludeId=appointmentId`; patch non-null fields; recompute `endTime` if start/duration changed; set `updatedAt/By`; save; write audit log (`"UPDATE"`, status, status, performer, changedFields); return `AppointmentResponse` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T067 [US4] Add `PATCH /api/v1/appointments/{appointmentId}` endpoint to `AppointmentController` with `@RequestHeader("If-Match") Integer version` and `@Valid @RequestBody UpdateAppointmentRequest` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T068 [US4] Unit test `AppointmentServiceTest`: update happy path patches fields; status guard (IN_PROGRESS) → `ConflictException`; version mismatch → `ConflictException`; reschedule to occupied slot → `ConflictException`; patientId in request body is ignored (not a field on request) in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/AppointmentServiceTest.java`
- [X] T069 [US4] Integration test `AppointmentUpdateIT`: reschedule 09:00→10:00 → 200 with new time; reschedule to occupied → 409; stale If-Match:0 on version=1 → 409; PATCH COMPLETED appointment → 409; NURSE PATCH → 403; audit log entry created for update in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentUpdateIT.java`
- [X] T070 [P] [US4] Add `updateAppointment(appointmentId, data, version)` function to `appointmentApi.js` (sets `'If-Match': version` header) in `frontend/src/api/appointmentApi.js`
- [X] T071 [P] [US4] Add `useUpdateAppointment()` TanStack Query mutation hook (invalidates appointment on success) to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T072 [US4] Add edit form/modal to `AppointmentDetailPage.jsx`: shown only for RECEPTIONIST/ADMIN on SCHEDULED/CONFIRMED appointments; pre-populates current values; passes `version` in If-Match; handles 409 (version conflict → reload) in `frontend/src/pages/AppointmentDetailPage.jsx`

**Checkpoint**: US1–US4 complete — appointments can be booked, viewed, status-managed, and updated.

---

## Phase 7: User Story 5 — Doctor Availability (Priority: P2)

**Goal**: Any staff can check a doctor's 30-minute slot grid (08:00–18:00). Slots occupied by active appointments marked OCCUPIED with appointmentId. Cancelled/no-show appointments do not block slots.

**Independent Test**: Seed 60-min appointment at 09:00 → query availability → 09:00 and 09:30 slots OCCUPIED; all others AVAILABLE. Cancel appointment → re-query → all 20 slots AVAILABLE.

- [X] T073 [US5] Create `AvailabilityResponse` Java record: `(LocalDate date, String doctorId, String doctorName, List<TimeSlotResponse> slots)` in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/AvailabilityResponse.java`
- [X] T074 [US5] Create `TimeSlotResponse` Java record: `(LocalTime startTime, LocalTime endTime, boolean available, String appointmentId)` — `appointmentId` null when available=true in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/TimeSlotResponse.java`
- [X] T075 [US5] Add `findByDoctorIdAndAppointmentDateAndStatusNotIn(String doctorId, LocalDate date, Collection<AppointmentStatus> excludedStatuses)` to `AppointmentRepository` — returns all non-cancelled/no-show appointments for overlap checking in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T076 [US5] Create `DoctorAvailabilityService` `@Service`: `getAvailability(String doctorId, LocalDate date)` — validate doctor exists via `HospitalUserRepository`; query active appointments; generate 20-slot grid `[08:00,18:00)` at 30-min intervals; for each slot check overlap with any appointment (`aptStart < slotEnd && aptEnd > slotStart`); mark OCCUPIED with `appointmentId`; return `AvailabilityResponse` in `backend/src/main/java/com/ainexus/hospital/patient/service/DoctorAvailabilityService.java`
- [X] T077 [US5] Add `GET /api/v1/doctors/{doctorId}/availability` endpoint to `DoctorScheduleController` (`@RequestParam @NotNull LocalDate date`) delegating to `DoctorAvailabilityService.getAvailability()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/DoctorScheduleController.java`
- [X] T078 [US5] Unit test `DoctorAvailabilityServiceTest`: no appointments → all 20 slots available; 60-min appointment at 09:00 → slots 09:00 and 09:30 occupied; CANCELLED appointment → does not block; 30-min at 08:00 → only 08:00 slot occupied in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/DoctorAvailabilityServiceTest.java`
- [X] T079 [US5] Integration test `DoctorAvailabilityIT`: availability with CONFIRMED appointment → correct slots occupied; cancel appointment → all slots free; non-existent doctor → 404; missing `date` param → 400 in `backend/src/test/java/com/ainexus/hospital/patient/integration/DoctorAvailabilityIT.java`
- [X] T080 [US5] Integration test `AppointmentCancellationIT`: cancel with reason → 200, status=CANCELLED, slot released; no-show → 200, status=NO_SHOW, slot released; cancel without reason → 400; double-cancel → 409 in `backend/src/test/java/com/ainexus/hospital/patient/integration/AppointmentCancellationIT.java`
- [X] T081 [P] [US5] Add `getDoctorAvailability(doctorId, date)` function to `appointmentApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T082 [P] [US5] Add `useDoctorAvailability(doctorId, date)` TanStack Query hook to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T083 [US5] Create `TimeSlotGrid.jsx` component: renders 20-slot grid; available slots = green/clickable; occupied slots = red/disabled with tooltip showing appointmentId; selected slot highlighted; fires `onSlotSelect(startTime)` callback in `frontend/src/components/appointment/TimeSlotGrid.jsx`
- [X] T084 [US5] Create `DoctorAvailabilityPage.jsx`: doctor ID input + date picker; renders `<TimeSlotGrid>` via `useDoctorAvailability`; clicking available slot navigates to `/appointments/new?doctorId=...&date=...&startTime=...` in `frontend/src/pages/DoctorAvailabilityPage.jsx`
- [X] T085 [US5] Update `AppointmentBookingPage.jsx` to: when doctorId + date are filled, auto-fetch availability via `useDoctorAvailability` and render `<TimeSlotGrid>` to pre-populate `startTime` in `frontend/src/pages/AppointmentBookingPage.jsx`

**Checkpoint**: US1–US5 complete — full P1 and P2 feature set operational.

---

## Phase 8: User Story 6 — Clinical Notes (Priority: P3)

**Goal**: Assigned DOCTOR or ADMIN can add/update encrypted clinical notes on IN_PROGRESS/COMPLETED appointments. `privateNotes` field excluded from RECEPTIONIST/NURSE responses. All note fields encrypted at rest.

**Independent Test**: POST notes as assigned DOCTOR on IN_PROGRESS appointment → 200. GET as DOCTOR → `privateNotes` present. GET as RECEPTIONIST → `privateNotes` null. POST as SCHEDULED appointment → 400. POST as unassigned DOCTOR → 403.

- [X] T086 [US6] Create `ClinicalNotes` JPA entity: `@Entity @Table("clinical_notes")`; `@Id String appointmentId`; `@OneToOne(fetch=LAZY) @MapsId @JoinColumn(name="appointment_id") Appointment appointment`; encrypted fields: `@Convert(converter=NotesEncryptionConverter.class) String chiefComplaint/diagnosis/treatment/prescription/privateNotes`; plain fields: `Boolean followUpRequired`, `Integer followUpDays`, `String createdBy`, `OffsetDateTime createdAt`, `OffsetDateTime updatedAt`; Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` in `backend/src/main/java/com/ainexus/hospital/patient/entity/ClinicalNotes.java`
- [X] T087 [US6] Create `ClinicalNotesRepository` interface extending `JpaRepository<ClinicalNotes, String>` in `backend/src/main/java/com/ainexus/hospital/patient/repository/ClinicalNotesRepository.java`
- [X] T088 [US6] Create `ClinicalNotesRequest` Java record: all text fields nullable (`String chiefComplaint`, `diagnosis`, `treatment`, `prescription`, `privateNotes`), `Boolean followUpRequired`, `Integer followUpDays`; add cross-field validation: if `followUpRequired=false` then `followUpDays` must be null in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/ClinicalNotesRequest.java`
- [X] T089 [US6] Create `ClinicalNotesResponse` Java record: `(String appointmentId, String chiefComplaint, String diagnosis, String treatment, String prescription, Boolean followUpRequired, Integer followUpDays, String privateNotes, String createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt)` — `privateNotes` will be nulled out by service for restricted roles in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/ClinicalNotesResponse.java`
- [X] T090 [US6] Create `ClinicalNotesMapper` `@Mapper(componentModel="spring")` with `toResponse(ClinicalNotes)` → `ClinicalNotesResponse` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/ClinicalNotesMapper.java`
- [X] T091 [US6] Implement `ClinicalNotesService.upsertNotes(String appointmentId, ClinicalNotesRequest)`: load appointment (404); `roleGuard.requireRoles("DOCTOR","ADMIN")`; if DOCTOR, verify `appointment.getDoctorId().equals(ctx.getUserId())` else `ForbiddenException`; verify `status IN (IN_PROGRESS, COMPLETED)` else `ConflictException`; validate `followUpDays` constraint; load existing notes or create new; patch all non-null fields; set `updatedAt`; save; write audit log (`existsAlready ? "NOTE_UPDATED" : "NOTE_ADDED"`); return `ClinicalNotesResponse` with `privateNotes` intact in `backend/src/main/java/com/ainexus/hospital/patient/service/ClinicalNotesService.java`
- [X] T092 [US6] Implement `ClinicalNotesService.getNotes(String appointmentId)`: `roleGuard.requireAuthenticated()`; load appointment (404); if DOCTOR, verify `appointment.getDoctorId().equals(ctx.getUserId())` else `ForbiddenException`; load notes (404 if absent); map to response; if `ctx.getRole()` is RECEPTIONIST or NURSE, null out `privateNotes` before returning in `backend/src/main/java/com/ainexus/hospital/patient/service/ClinicalNotesService.java`
- [X] T093 [US6] Create `ClinicalNotesController` `@RestController @RequestMapping("/api/v1/appointments/{appointmentId}/notes")` with `POST` → `upsertNotes` (200) and `GET` → `getNotes` (200) in `backend/src/main/java/com/ainexus/hospital/patient/controller/ClinicalNotesController.java`
- [X] T094 [US6] Unit test `ClinicalNotesServiceTest`: upsert on IN_PROGRESS happy path; upsert on SCHEDULED → `ConflictException`; unassigned DOCTOR → `ForbiddenException`; RECEPTIONIST write → `ForbiddenException`; getNotes DOCTOR own appointment → `privateNotes` present; getNotes RECEPTIONIST → `privateNotes` null; ADMIN → `privateNotes` present in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/ClinicalNotesServiceTest.java`
- [X] T095 [US6] Unit test `NotesEncryptionConverterTest`: encrypt then decrypt round-trip returns original; two encryptions of same string produce different ciphertext (different IV); null input returns null in `backend/src/test/java/com/ainexus/hospital/patient/unit/validation/NotesEncryptionConverterTest.java`
- [X] T096 [US6] Integration test `ClinicalNotesIT`: POST notes on IN_PROGRESS → 200; POST on SCHEDULED → 400/409; unassigned DOCTOR → 403; RECEPTIONIST POST → 403; GET as DOCTOR (assigned) → `privateNotes` present; GET as RECEPTIONIST → `privateNotes` absent/null; GET as ADMIN → `privateNotes` present; upsert update (second POST) changes fields in `backend/src/test/java/com/ainexus/hospital/patient/integration/ClinicalNotesIT.java`
- [X] T097 [P] [US6] Add `upsertClinicalNotes(appointmentId, data)` and `getClinicalNotes(appointmentId)` functions to `appointmentApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T098 [P] [US6] Add `useUpsertClinicalNotes()` mutation and `useClinicalNotes(appointmentId)` query hooks to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T099 [US6] Add clinical notes section to `AppointmentDetailPage.jsx`: show notes form for DOCTOR/ADMIN when status is IN_PROGRESS or COMPLETED; read-only display for all roles (omit `privateNotes` display for RECEPTIONIST/NURSE based on null check); fetch via `useClinicalNotes` in `frontend/src/pages/AppointmentDetailPage.jsx`

**Checkpoint**: US6 complete — clinical notes with encryption and access control working end-to-end.

---

## Phase 9: User Story 7 — Patient Appointment History (Priority: P3)

**Goal**: Any staff can view a patient's full paginated appointment history sorted by date DESC. DOCTOR sees only own appointments. Non-existent patient returns 404.

**Independent Test**: Seed 3 appointments for patient P2025001 (2 with doctor A, 1 with doctor B). ADMIN sees all 3. DOCTOR A sees 2. RECEPTIONIST sees all 3. Non-existent patient → 404.

- [X] T100 [US7] Add `findByPatientId(String patientId, Pageable pageable)` Spring Data method to `AppointmentRepository` (Pageable handles sort by date DESC) in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T101 [US7] Add `findByPatientIdAndDoctorId(String patientId, String doctorId, Pageable pageable)` Spring Data method to `AppointmentRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AppointmentRepository.java`
- [X] T102 [US7] Implement `AppointmentService.getPatientAppointmentHistory(String patientId, int page, int size)`: `roleGuard.requireAuthenticated()`; verify patient exists via `PatientRepository` (throw 404 if not); if DOCTOR role use `findByPatientIdAndDoctorId(patientId, ctx.getUserId())`; else use `findByPatientId(patientId)`; resolve names; sort by `appointmentDate DESC`; return `PagedResponse<AppointmentSummaryResponse>` in `backend/src/main/java/com/ainexus/hospital/patient/service/AppointmentService.java`
- [X] T103 [US7] Add `GET /api/v1/patients/{patientId}/appointments` endpoint to `AppointmentController` delegating to `AppointmentService.getPatientAppointmentHistory()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AppointmentController.java`
- [X] T104 [US7] Unit test `AppointmentServiceTest`: `getPatientAppointmentHistory` with DOCTOR role applies doctor filter; with ADMIN returns all; non-existent patient throws `PatientNotFoundException` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/AppointmentServiceTest.java`
- [X] T105 [US7] Integration test `PatientAppointmentHistoryIT`: 3 appointments 2 doctors — ADMIN sees 3; DOCTOR sees own 2; RECEPTIONIST sees 3; non-existent patient → 404; patient with no appointments → 200 with empty page in `backend/src/test/java/com/ainexus/hospital/patient/integration/PatientAppointmentHistoryIT.java`
- [X] T106 [P] [US7] Add `getPatientAppointments(patientId, page, size)` function to `appointmentApi.js` in `frontend/src/api/appointmentApi.js`
- [X] T107 [P] [US7] Add `usePatientAppointments(patientId, page)` TanStack Query hook to `useAppointments.js` in `frontend/src/hooks/useAppointments.js`
- [X] T108 [US7] Add appointment history tab to `PatientProfilePage.jsx` (existing page): paginated list of `AppointmentCard` for the current patient via `usePatientAppointments`; sorted by date DESC in `frontend/src/pages/PatientProfilePage.jsx`

**Checkpoint**: All 7 user stories complete — full appointment scheduling module implemented.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Wire frontend routing, add navigation, validate full stack, freeze module.

- [X] T109 Update `frontend/src/App.jsx` (or router config) to add routes: `/appointments` → `AppointmentListPage`, `/appointments/new` → `AppointmentBookingPage`, `/appointments/:appointmentId` → `AppointmentDetailPage`, `/doctors/:doctorId/availability` → `DoctorAvailabilityPage` in `frontend/src/App.jsx`
- [X] T110 [P] Add navigation links to Appointments list and Book Appointment in the main navigation component (Navbar or sidebar) in `frontend/src/components/` (locate existing nav component)
- [X] T111 [P] Update `CLAUDE.md` `Recent Changes` section with Module 3 key patterns: `NotesEncryptionConverter` (AES/GCM AttributeConverter), `AppointmentIdGeneratorService` (APT format), state machine static maps in `AppointmentService`, DOCTOR scope restriction pattern in `CLAUDE.md`
- [X] T112 Run `mvn test` to confirm all unit tests pass (target: 0 failures) in `backend/`
- [X] T113 Run `mvn verify -Pfailsafe` to confirm all integration tests pass (Testcontainers) in `backend/`
- [X] T114 [P] Run `npm test` (or `npm run test`) to confirm all frontend tests pass in `frontend/`
- [X] T115 Smoke test: `docker compose up --build`; login as admin; book an appointment; confirm it; add clinical notes; verify all steps succeed end-to-end in project root
- [X] T116 Commit and push branch `003-appointment-scheduling`: `git add` relevant files, `git commit -m "feat(appointments): implement Module 3 — Appointment Scheduling (116/116 tasks complete)"`, `git push origin 003-appointment-scheduling`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **Phase 3–9 (User Stories)**: All depend on Phase 2 completion
  - Phase 3 (US1) and Phase 4 (US2) are both P1 — can proceed in parallel after Phase 2
  - Phase 5–7 (US3, US4, US5) are P2 — can proceed after Phase 2; optionally use Phase 3 data
  - Phase 8–9 (US6, US7) are P3 — depend on Phase 3+5 (need appointments to exist in COMPLETED/IN_PROGRESS state)
- **Phase 10 (Polish)**: Depends on all user story phases

### User Story Dependencies

| Story | Depends On | Can Parallelize With |
|-------|-----------|----------------------|
| US1 (Book) | Foundational | US2 |
| US2 (Search) | Foundational | US1 |
| US3 (Lifecycle) | US1 | US4, US5 |
| US4 (Update) | US1 | US3, US5 |
| US5 (Availability) | Foundational | US3, US4 |
| US6 (Notes) | US1, US3 | US7 |
| US7 (History) | US1 | US6 |

### Within Each User Story

1. Enums / entities before repositories
2. Repositories before services
3. Services before controllers
4. Backend implementation before integration tests
5. Frontend API functions before hooks before pages

---

## Parallel Execution Examples

### Parallel: Phase 2 Foundational Tasks

```
Parallel group A (enums + entities):
  T015 Create Appointment entity
  T016 Create AppointmentIdSequence entity
  T017 Create AppointmentAuditLog entity

Parallel group B (DTOs):
  T022 Create AppointmentResponse DTO
  T023 Create AppointmentSummaryResponse DTO
```

### Parallel: US1 Book Appointment

```
Parallel (after T027 repository exists):
  T030 Unit tests for AppointmentService.bookAppointment
  T031 Integration test AppointmentBookingIT
  T032 Frontend appointmentApi.js bookAppointment
  T033 Frontend useBookAppointment hook
```

### Parallel: US2 Search

```
Parallel (after repositories exist):
  T050 AppointmentStatusBadge.jsx component
  T051 AppointmentCard.jsx component
  T052 AppointmentListPage.jsx
  T053 AppointmentDetailPage.jsx
```

---

## Implementation Strategy

### MVP First (P1 Stories — US1 + US2)

1. Complete Phase 1: Setup (T001–T010)
2. Complete Phase 2: Foundational (T011–T025)
3. Complete Phase 3: US1 Book Appointment (T026–T034)
4. **STOP AND VALIDATE**: Book an appointment via API → 201 with APT ID
5. Complete Phase 4: US2 View & Search (T035–T053)
6. **STOP AND VALIDATE**: List appointments, view detail, DOCTOR scope restriction
7. **MVP DEMO READY**: Booking + viewing operational

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → Can book appointments (MVP)
3. US2 → Can search and view appointments
4. US3 → Status lifecycle (confirm, check-in, complete)
5. US4 → Can reschedule appointments
6. US5 → Availability grid (makes booking much easier)
7. US6 → Clinical documentation
8. US7 → Patient care history

### Total Task Count by Phase

| Phase | Tasks | Story |
|-------|-------|-------|
| Phase 1: Setup | T001–T010 | 10 tasks |
| Phase 2: Foundational | T011–T025 | 15 tasks |
| Phase 3: US1 Book | T026–T034 | 9 tasks |
| Phase 4: US2 Search | T035–T053 | 19 tasks |
| Phase 5: US3 Lifecycle | T054–T064 | 11 tasks |
| Phase 6: US4 Update | T065–T072 | 8 tasks |
| Phase 7: US5 Availability | T073–T085 | 13 tasks |
| Phase 8: US6 Notes | T086–T099 | 14 tasks |
| Phase 9: US7 History | T100–T108 | 9 tasks |
| Phase 10: Polish | T109–T116 | 8 tasks |
| **Total** | | **116 tasks** |

---

## Notes

- `[P]` tasks operate on different files and have no incomplete dependencies — they can be launched in parallel
- All `@Transactional` service methods must call `AppointmentAuditService.writeAuditLog()` within the same transaction
- DOCTOR scope restriction is service-layer only — never pass unvalidated role-filtered data through controllers
- `NotesEncryptionConverter` must handle null input transparently (null → null) to avoid NullPointerException on optional fields
- `PatientRepository` and `HospitalUserRepository` are injected into `AppointmentService` for name resolution and validation — no circular dependency risk since both are read-only lookups
- Seed patients for integration tests must use year 2025 (e.g. `P2025001`) to avoid conflicts with the 2026 sequence used by registration tests
- The `appointments/today` endpoint must come BEFORE `appointments/{appointmentId}` in the controller to avoid Spring matching "today" as a path variable
