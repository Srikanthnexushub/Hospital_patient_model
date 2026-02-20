# Tasks: Electronic Medical Records (EMR) Module

**Input**: Design documents from `/specs/005-emr-module/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓

**Total Tasks**: 68 | **User Stories**: 5 | **Backend**: 48 | **Frontend**: 17 | **Polish**: 3

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with adjacent [P] tasks (different files, no mutual dependency)
- **[Story]**: US1=Vitals, US2=Problems, US3=Medications, US4=Allergies, US5=Medical Summary
- Base package: `backend/src/main/java/com/ainexus/hospital/patient/`

---

## Phase 1: Setup — Flyway Migrations (V17–V21)

**Purpose**: Create all EMR database tables before any entity work begins.
All 5 migrations are independent — execute in parallel.

- [X] T001 [P] Create Flyway migration `backend/src/main/resources/db/migration/V17__create_patient_vitals.sql` — `patient_vitals` table with columns: `id BIGSERIAL PK`, `appointment_id VARCHAR(14) NOT NULL UNIQUE`, `patient_id VARCHAR(14) NOT NULL`, `blood_pressure_systolic INT`, `blood_pressure_diastolic INT`, `heart_rate INT`, `temperature NUMERIC(4,1)`, `weight NUMERIC(5,2)`, `height NUMERIC(5,1)`, `oxygen_saturation INT`, `respiratory_rate INT`, `recorded_by VARCHAR(100) NOT NULL`, `recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`; FK `appointment_id` references `appointments(appointment_id)`; index `idx_patient_vitals_patient_id` on `(patient_id, recorded_at DESC)`
- [X] T002 [P] Create Flyway migration `backend/src/main/resources/db/migration/V18__create_patient_problems.sql` — `patient_problems` table with columns: `id UUID PK DEFAULT gen_random_uuid()`, `patient_id VARCHAR(14) NOT NULL`, `title VARCHAR(200) NOT NULL`, `description TEXT`, `icd_code VARCHAR(20)`, `severity VARCHAR(20) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `onset_date DATE`, `notes TEXT`, `created_by VARCHAR(100) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_by VARCHAR(100)`, `updated_at TIMESTAMPTZ`; index `idx_patient_problems_patient_id` on `(patient_id, status)`
- [X] T003 [P] Create Flyway migration `backend/src/main/resources/db/migration/V19__create_patient_medications.sql` — `patient_medications` table with columns: `id UUID PK DEFAULT gen_random_uuid()`, `patient_id VARCHAR(14) NOT NULL`, `medication_name VARCHAR(200) NOT NULL`, `generic_name VARCHAR(200)`, `dosage VARCHAR(100) NOT NULL`, `frequency VARCHAR(100) NOT NULL`, `route VARCHAR(20) NOT NULL`, `start_date DATE NOT NULL`, `end_date DATE`, `indication TEXT`, `prescribed_by VARCHAR(100) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `notes TEXT`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ`; index `idx_patient_medications_patient_id` on `(patient_id, status)`
- [X] T004 [P] Create Flyway migration `backend/src/main/resources/db/migration/V20__create_patient_allergies.sql` — `patient_allergies` table with columns: `id UUID PK DEFAULT gen_random_uuid()`, `patient_id VARCHAR(14) NOT NULL`, `substance VARCHAR(200) NOT NULL`, `type VARCHAR(20) NOT NULL`, `severity VARCHAR(20) NOT NULL`, `reaction TEXT NOT NULL`, `onset_date DATE`, `notes TEXT`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_by VARCHAR(100) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_by VARCHAR(100)`, `updated_at TIMESTAMPTZ`; index `idx_patient_allergies_patient_id` on `(patient_id, active)`
- [X] T005 [P] Create Flyway migration `backend/src/main/resources/db/migration/V21__create_emr_audit_log.sql` — `emr_audit_log` table with columns: `id BIGSERIAL PK`, `entity_type VARCHAR(20) NOT NULL` (values: VITAL, PROBLEM, MEDICATION, ALLERGY), `entity_id VARCHAR(50) NOT NULL`, `patient_id VARCHAR(14) NOT NULL`, `action VARCHAR(30) NOT NULL`, `performed_by VARCHAR(100) NOT NULL`, `performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `details TEXT`; indexes `idx_emr_audit_patient_id` on `(patient_id, performed_at DESC)` and `idx_emr_audit_entity` on `(entity_type, entity_id, performed_at DESC)`

---

## Phase 2: Foundational — Shared EMR Infrastructure

**Purpose**: Audit service and enums that ALL user story phases depend on.

⚠️ **CRITICAL**: Complete T006–T008 before any service task. Complete T009–T012 before any entity task.

- [X] T006 Create `EmrAuditLog` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/EmrAuditLog.java` — `@Entity @Table(name="emr_audit_log")` with `@Id @GeneratedValue(strategy=IDENTITY) Long id`, `String entityType`, `String entityId`, `String patientId`, `String action`, `String performedBy`, `OffsetDateTime performedAt`, `String details`; `@PrePersist` sets `performedAt = OffsetDateTime.now()`
- [X] T007 Create `EmrAuditLogRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/EmrAuditLogRepository.java` — `extends JpaRepository<EmrAuditLog, Long>`
- [X] T008 Create `EmrAuditService` in `backend/src/main/java/com/ainexus/hospital/patient/audit/EmrAuditService.java` — `@Service`; single method `writeAuditLog(String entityType, String entityId, String patientId, String action, String performedBy, String details)` annotated `@Transactional(propagation = Propagation.MANDATORY)`; constructs and saves `EmrAuditLog` via `EmrAuditLogRepository`; constructor-injected
- [X] T009 [P] Create problem enums in `backend/src/main/java/com/ainexus/hospital/patient/entity/`: `ProblemSeverity.java` (MILD, MODERATE, SEVERE with `@JsonValue` display names) and `ProblemStatus.java` (ACTIVE, RESOLVED, INACTIVE with `@JsonValue` display names)
- [X] T010 [P] Create medication enums in `backend/src/main/java/com/ainexus/hospital/patient/entity/`: `MedicationRoute.java` (ORAL, IV, IM, TOPICAL, INHALED, OTHER with `@JsonValue`) and `MedicationStatus.java` (ACTIVE, DISCONTINUED, COMPLETED with `@JsonValue`)
- [X] T011 [P] Create allergy enums in `backend/src/main/java/com/ainexus/hospital/patient/entity/`: `AllergyType.java` (DRUG, FOOD, ENVIRONMENTAL, OTHER with `@JsonValue`) and `AllergySeverity.java` (MILD, MODERATE, SEVERE, LIFE_THREATENING with `@JsonValue`)
- [X] T012 Create custom validator `AtLeastOneVitalPresent` in `backend/src/main/java/com/ainexus/hospital/patient/validation/AtLeastOneVitalPresent.java` — annotation `@AtLeastOneVitalPresent` + `AtLeastOneVitalPresentValidator implements ConstraintValidator`; validates that `RecordVitalsRequest` has at least one non-null measurement field (bloodPressureSystolic, bloodPressureDiastolic, heartRate, temperature, weight, height, oxygenSaturation, respiratoryRate)

**Checkpoint**: Foundation ready — user story phases can now proceed.

---

## Phase 3: User Story 1 — Record & View Vitals (Priority: P1) 🎯 MVP

**Goal**: Nurse/Doctor records vitals per appointment (upsert). Doctor/Nurse retrieves vitals history.

**Independent Test**: POST `/api/v1/appointments/{id}/vitals` as NURSE → 200 with values confirmed; GET `/api/v1/patients/{id}/vitals` as DOCTOR → 200 paginated list sorted by `recordedAt DESC`; POST with no fields → 400; RECEPTIONIST GET → 403.

- [X] T013 [US1] Create `PatientVitals` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/PatientVitals.java` — `@Entity @Table(name="patient_vitals")` with `@Id @GeneratedValue(strategy=IDENTITY) Long id`; `String appointmentId`; `String patientId`; nullable Integer/BigDecimal fields for all 8 measurements; `String recordedBy`; `OffsetDateTime recordedAt`; Lombok `@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor`
- [X] T014 [P] [US1] Create `VitalsRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/VitalsRepository.java` — `extends JpaRepository<PatientVitals, Long>`; method `Optional<PatientVitals> findByAppointmentId(String appointmentId)`; method `Page<PatientVitals> findByPatientIdOrderByRecordedAtDesc(String patientId, Pageable pageable)`
- [X] T015 [P] [US1] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `RecordVitalsRequest.java` (Java record with 8 nullable Integer/BigDecimal fields, class-level `@AtLeastOneVitalPresent`; field `bloodPressureDiastolic` validated ≤ `bloodPressureSystolic` when both non-null; `oxygenSaturation` validated 0–100 via `@Min(0) @Max(100)`); `VitalsResponse.java` (record: id, appointmentId, patientId, all 8 measurements nullable, recordedBy, recordedAt)
- [X] T016 [US1] Create `VitalsMapper` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/VitalsMapper.java` — MapStruct `@Mapper(componentModel="spring")`; `VitalsResponse toResponse(PatientVitals entity)`; `void updateEntity(RecordVitalsRequest request, @MappingTarget PatientVitals entity)` to map all 8 measurement fields
- [X] T017 [US1] Create `VitalsService` in `backend/src/main/java/com/ainexus/hospital/patient/service/VitalsService.java` — constructor injection of `VitalsRepository`, `AppointmentRepository`, `PatientRepository`, `VitalsMapper`, `EmrAuditService`, `RoleGuard`, `MeterRegistry`; implement `recordVitals(String appointmentId, RecordVitalsRequest)`: `roleGuard.requireRoles("NURSE","DOCTOR","ADMIN")`, verify appointment exists (404 if not), look up `patientId` from appointment, `findByAppointmentId` upsert logic (existing → update, absent → new), `recordedBy`/`recordedAt` from auth context, `vitalsRepository.save()`, `emrAuditService.writeAuditLog("VITAL", id, patientId, action, username, null)`, return `VitalsResponse`; implement `getVitalsByAppointment(String appointmentId)`: verify exists, return single response or 404; implement `getVitalsByPatient(String patientId, Pageable)`: verify patient exists (404 if not), return paged list
- [X] T018 [US1] Create `VitalsController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/VitalsController.java` — `@RestController`; `POST /api/v1/appointments/{appointmentId}/vitals` → `recordVitals()` → `ResponseEntity.ok()`; `GET /api/v1/appointments/{appointmentId}/vitals` → `getVitalsByAppointment()` → `ResponseEntity.ok()`; `GET /api/v1/patients/{patientId}/vitals` with `@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size` → `getVitalsByPatient()` → `ResponseEntity.ok()`
- [X] T019 [P] [US1] Create `VitalsServiceTest` in `backend/src/test/java/com/ainexus/hospital/patient/service/VitalsServiceTest.java` — Mockito unit tests: upsert creates new record when none exists; upsert replaces existing when found; throws 403 for RECEPTIONIST; throws 404 for unknown appointment; throws 400 when all vitals fields null; throws 400 when diastolic > systolic; audit service called with correct entityType "VITAL"
- [X] T020 [US1] Create `VitalsIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/VitalsIT.java` — Testcontainers integration test extending `BaseIntegrationTest`; seed a patient (P2025001) and appointment; test: NURSE POST → 200 with values; re-POST same appointment → 200 replaces; empty body → 400; RECEPTIONIST GET → 403; DOCTOR GET patient history → 200 paginated; unknown appointmentId → 404
- [X] T021 [P] [US1] Create `frontend/src/api/emrApi.js` — axios wrappers using existing `api` instance from `patientApi.js`; export: `recordVitals(appointmentId, data)`, `getVitalsByAppointment(appointmentId)`, `getPatientVitals(patientId, page, size)`
- [X] T022 [P] [US1] Create `frontend/src/hooks/useEmr.js` — TanStack Query hooks; export: `useRecordVitals(appointmentId)` (useMutation, invalidates `['vitals', appointmentId]` and `['patient-vitals', patientId]`), `useVitalsByAppointment(appointmentId)` (useQuery), `usePatientVitals(patientId, page)` (useQuery, enabled when patientId defined)
- [X] T023 [US1] Add `VitalsSection` component inside `frontend/src/pages/AppointmentDetailPage.jsx` (below `ClinicalNotesSection`) — shown when `role` is NURSE, DOCTOR, or ADMIN; form with 8 optional measurement inputs; calls `useRecordVitals`; displays existing vitals if present; shows 400 validation errors inline
- [X] T024 [P] [US1] Add Vitals history tab to `frontend/src/pages/PatientProfilePage.jsx` — new "Vitals" tab showing paginated table (recordedAt, BP, HR, temp, weight, O2); uses `usePatientVitals`; tab hidden for RECEPTIONIST role

---

## Phase 4: User Story 2 — Problem List (Priority: P1)

**Goal**: Doctor maintains a patient's persistent problem list with status lifecycle.

**Independent Test**: POST `/api/v1/patients/{id}/problems` as DOCTOR → 201 with UUID; PATCH status to RESOLVED → 200; GET `?status=ACTIVE` as NURSE → RESOLVED problem absent; NURSE POST → 403; RECEPTIONIST GET → 403.

- [X] T025 [US2] Create `PatientProblem` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/PatientProblem.java` — `@Entity @Table(name="patient_problems")`; `@Id @GeneratedValue(strategy=GenerationType.UUID) @Column(columnDefinition="uuid") UUID id`; `String patientId`; `String title`; `String description`; `String icdCode`; `@Enumerated(EnumType.STRING) ProblemSeverity severity`; `@Enumerated(EnumType.STRING) ProblemStatus status`; `LocalDate onsetDate`; `String notes`; `String createdBy`; `OffsetDateTime createdAt`; `String updatedBy`; `OffsetDateTime updatedAt`; Lombok annotations; `@PrePersist` sets `createdAt = now()`
- [X] T026 [P] [US2] Create `ProblemRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/ProblemRepository.java` — `extends JpaRepository<PatientProblem, UUID>`; `List<PatientProblem> findByPatientIdAndStatus(String patientId, ProblemStatus status)`; `List<PatientProblem> findByPatientId(String patientId)` (for status=ALL); `boolean existsByIdAndPatientId(UUID id, String patientId)`
- [X] T027 [P] [US2] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `CreateProblemRequest.java` (record: `@NotBlank String title`, `@NotNull ProblemSeverity severity`, `@NotNull ProblemStatus status`, nullable description/icdCode/onsetDate/notes; onsetDate validated `@PastOrPresent`); `UpdateProblemRequest.java` (record: all nullable fields for partial update); `ProblemResponse.java` (record: UUID id, patientId, title, description, icdCode, severity, status, onsetDate, notes, createdBy, createdAt, updatedBy, updatedAt)
- [X] T028 [US2] Create `ProblemMapper` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/ProblemMapper.java` — MapStruct `@Mapper(componentModel="spring")`; `ProblemResponse toResponse(PatientProblem)`; `PatientProblem toEntity(CreateProblemRequest)` (ignores id, createdBy, createdAt, updatedBy, updatedAt); `void updateEntity(UpdateProblemRequest, @MappingTarget PatientProblem)` (ignores null fields via `@BeanMapping(nullValuePropertyMappingStrategy=IGNORE)`)
- [X] T029 [US2] Create `ProblemService` in `backend/src/main/java/com/ainexus/hospital/patient/service/ProblemService.java` — constructor injection of `ProblemRepository`, `PatientRepository`, `ProblemMapper`, `EmrAuditService`, `RoleGuard`, `MeterRegistry`; `createProblem(String patientId, CreateProblemRequest)`: `requireRoles("DOCTOR","ADMIN")`, verify patient (404), validate onsetDate not future, set `createdBy` from auth, save, audit "CREATE"; `listProblems(String patientId, String statusParam)`: `requireRoles("DOCTOR","NURSE","ADMIN")`, verify patient, return all if status=ALL else filter by status (default ACTIVE); `updateProblem(String patientId, UUID problemId, UpdateProblemRequest)`: `requireRoles("DOCTOR","ADMIN")`, verify patient, verify problem belongs to patient (404), partial-update via mapper, set `updatedBy`/`updatedAt`, save, audit with action derived from new status (RESOLVE if RESOLVED, else UPDATE)
- [X] T030 [US2] Create `ProblemController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/ProblemController.java` — `@RestController @RequestMapping("/api/v1/patients/{patientId}/problems")`; `POST /` → 201 Created; `GET /` with `@RequestParam(defaultValue="ACTIVE") String status` → 200; `PATCH /{problemId}` → 200
- [X] T031 [P] [US2] Create `ProblemServiceTest` in `backend/src/test/java/com/ainexus/hospital/patient/service/ProblemServiceTest.java` — Mockito unit tests: create succeeds (DOCTOR); create fails 403 (NURSE); create fails 400 (future onsetDate); update status to RESOLVED; listProblems default returns ACTIVE only; listProblems status=ALL returns all; RECEPTIONIST listProblems → 403; audit called with "PROBLEM" entityType
- [X] T032 [US2] Create `ProblemIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/ProblemIT.java` — Testcontainers integration test; seed patient P2025001; DOCTOR POST → 201 with UUID id; PATCH status → RESOLVED → 200; GET ?status=ACTIVE → resolved problem absent; NURSE POST → 403; RECEPTIONIST GET → 403; unknown patientId → 404; future onsetDate → 400
- [X] T033 [US2] Add problem API functions to `frontend/src/api/emrApi.js` and problem hooks to `frontend/src/hooks/useEmr.js` — `createProblem(patientId, data)`, `getProblems(patientId, status)`, `updateProblem(patientId, problemId, data)`; hooks: `useCreateProblem(patientId)`, `useProblems(patientId, status)`, `useUpdateProblem(patientId)`; all invalidate `['problems', patientId]` on mutation
- [X] T034 [US2] Add Problems tab to `frontend/src/pages/PatientProfilePage.jsx` — list of problem cards showing title, severity badge, status, onsetDate; "Add Problem" button for DOCTOR/ADMIN (inline form with title, severity select, status select, onsetDate, notes); "Mark Resolved" quick action on ACTIVE problems (DOCTOR/ADMIN only); tab hidden for RECEPTIONIST

---

## Phase 5: User Story 3 — Medication List (Priority: P1)

**Goal**: Doctor prescribes medications; nurse views active list; doctor discontinues.

**Independent Test**: POST as DOCTOR → 201 with `prescribedBy` auto-set; PATCH to DISCONTINUED → 200; GET `?status=ALL` as NURSE → includes DISCONTINUED; NURSE POST → 403; RECEPTIONIST GET → 403.

- [X] T035 [US3] Create `PatientMedication` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/PatientMedication.java` — `@Entity @Table(name="patient_medications")`; `@Id @GeneratedValue(strategy=GenerationType.UUID) @Column(columnDefinition="uuid") UUID id`; `String patientId`; `String medicationName`; `String genericName`; `String dosage`; `String frequency`; `@Enumerated(EnumType.STRING) MedicationRoute route`; `LocalDate startDate`; `LocalDate endDate`; `String indication`; `String prescribedBy`; `@Enumerated(EnumType.STRING) MedicationStatus status`; `String notes`; `OffsetDateTime createdAt`; `OffsetDateTime updatedAt`; `@PrePersist` sets `createdAt`, `status=ACTIVE` if null
- [X] T036 [P] [US3] Create `MedicationRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/MedicationRepository.java` — `extends JpaRepository<PatientMedication, UUID>`; `List<PatientMedication> findByPatientIdAndStatus(String patientId, MedicationStatus status)`; `List<PatientMedication> findByPatientId(String patientId)` (for ALL); `boolean existsByIdAndPatientId(UUID id, String patientId)`
- [X] T037 [P] [US3] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `PrescribeMedicationRequest.java` (record: `@NotBlank medicationName, dosage, frequency`; `@NotNull route (MedicationRoute), startDate`; nullable genericName, endDate, indication, notes; endDate must be ≥ startDate validated in service); `UpdateMedicationRequest.java` (record: nullable status, endDate, indication, notes); `MedicationResponse.java` (record: UUID id, patientId, medicationName, genericName, dosage, frequency, route, startDate, endDate, indication, prescribedBy, status, notes, createdAt, updatedAt)
- [X] T038 [US3] Create `MedicationMapper` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/MedicationMapper.java` — MapStruct `@Mapper(componentModel="spring")`; `MedicationResponse toResponse(PatientMedication)`; `PatientMedication toEntity(PrescribeMedicationRequest)` (ignores id, prescribedBy, status, createdAt, updatedAt); `void updateEntity(UpdateMedicationRequest, @MappingTarget PatientMedication)` with `@BeanMapping(nullValuePropertyMappingStrategy=IGNORE)`
- [X] T039 [US3] Create `MedicationService` in `backend/src/main/java/com/ainexus/hospital/patient/service/MedicationService.java` — constructor injection of `MedicationRepository`, `PatientRepository`, `MedicationMapper`, `EmrAuditService`, `RoleGuard`, `MeterRegistry`; `prescribeMedication(String patientId, PrescribeMedicationRequest)`: `requireRoles("DOCTOR","ADMIN")`, verify patient (404), validate endDate ≥ startDate (400 if violated), set `prescribedBy` from auth context (IGNORE any value from request), set `status=ACTIVE`, save, audit "CREATE"; `listMedications(String patientId, String statusParam)`: `requireRoles("DOCTOR","NURSE","ADMIN")`, verify patient, return all if status=ALL else return ACTIVE only; `updateMedication(String patientId, UUID medId, UpdateMedicationRequest)`: `requireRoles("DOCTOR","ADMIN")`, verify patient and ownership (404), partial-update, set `updatedAt`, save, audit action "DISCONTINUE" or "UPDATE"
- [X] T040 [US3] Create `MedicationController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/MedicationController.java` — `@RestController @RequestMapping("/api/v1/patients/{patientId}/medications")`; `POST /` → 201; `GET /` with `@RequestParam(defaultValue="ACTIVE") String status` → 200; `PATCH /{medicationId}` → 200
- [X] T041 [P] [US3] Create `MedicationServiceTest` in `backend/src/test/java/com/ainexus/hospital/patient/service/MedicationServiceTest.java` — Mockito unit tests: prescribe succeeds (DOCTOR); `prescribedBy` always from auth context not request body; NURSE POST → 403; endDate before startDate → 400; discontinue sets status DISCONTINUED; GET active-only default; GET status=ALL includes DISCONTINUED; RECEPTIONIST GET → 403; audit called with "MEDICATION"
- [X] T042 [US3] Create `MedicationIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/MedicationIT.java` — Testcontainers integration test; DOCTOR POST → 201, `prescribedBy` matches doctor username; PATCH DISCONTINUED → 200; GET default → no discontinued; GET ?status=ALL → includes discontinued; NURSE POST → 403; RECEPTIONIST GET → 403; endDate < startDate → 400
- [X] T043 [US3] Add medication API functions to `frontend/src/api/emrApi.js` and hooks to `frontend/src/hooks/useEmr.js` — `prescribeMedication(patientId, data)`, `getMedications(patientId, status)`, `updateMedication(patientId, medicationId, data)`; hooks: `usePrescribeMedication(patientId)`, `useMedications(patientId, status)`, `useUpdateMedication(patientId)`; mutations invalidate `['medications', patientId]`
- [X] T044 [US3] Add Medications tab to `frontend/src/pages/PatientProfilePage.jsx` — list of medication cards showing name, dosage, frequency, route, status badge; "Prescribe Medication" button for DOCTOR/ADMIN (inline form with all required fields); "Discontinue" quick action on ACTIVE medications (DOCTOR/ADMIN only); tab hidden for RECEPTIONIST

---

## Phase 6: User Story 4 — Allergy Registry (Priority: P2)

**Goal**: Nurse/Doctor records structured allergies; soft-delete; RECEPTIONIST read-only.

**Independent Test**: NURSE POST → 201 with `active=true`; RECEPTIONIST GET → 200; DOCTOR DELETE → 204 soft-deleted; re-DELETE → 404; RECEPTIONIST DELETE → 403.

- [X] T045 [US4] Create `PatientAllergy` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/PatientAllergy.java` — `@Entity @Table(name="patient_allergies")`; `@Id @GeneratedValue(strategy=GenerationType.UUID) @Column(columnDefinition="uuid") UUID id`; `String patientId`; `String substance`; `@Enumerated(EnumType.STRING) AllergyType type`; `@Enumerated(EnumType.STRING) AllergySeverity severity`; `String reaction`; `LocalDate onsetDate`; `String notes`; `boolean active = true`; `String createdBy`; `OffsetDateTime createdAt`; `String updatedBy`; `OffsetDateTime updatedAt`; `@PrePersist` sets `createdAt`
- [X] T046 [P] [US4] Create `AllergyRepository` in `backend/src/main/java/com/ainexus/hospital/patient/repository/AllergyRepository.java` — `extends JpaRepository<PatientAllergy, UUID>`; `List<PatientAllergy> findByPatientIdAndActiveTrue(String patientId)`; `Optional<PatientAllergy> findByIdAndPatientIdAndActiveTrue(UUID id, String patientId)` (for soft-delete lookup)
- [X] T047 [P] [US4] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `RecordAllergyRequest.java` (record: `@NotBlank substance`, `@NotNull AllergyType type`, `@NotNull AllergySeverity severity`, `@NotBlank String reaction`; nullable onsetDate, notes); `AllergyResponse.java` (record: UUID id, patientId, substance, type, severity, reaction, onsetDate, notes, active, createdBy, createdAt, updatedBy, updatedAt)
- [X] T048 [US4] Create `AllergyMapper` in `backend/src/main/java/com/ainexus/hospital/patient/mapper/AllergyMapper.java` — MapStruct `@Mapper(componentModel="spring")`; `AllergyResponse toResponse(PatientAllergy)`; `PatientAllergy toEntity(RecordAllergyRequest)` (ignores id, active, createdBy, createdAt, updatedBy, updatedAt)
- [X] T049 [US4] Create `AllergyService` in `backend/src/main/java/com/ainexus/hospital/patient/service/AllergyService.java` — constructor injection of `AllergyRepository`, `PatientRepository`, `AllergyMapper`, `EmrAuditService`, `RoleGuard`, `MeterRegistry`; `recordAllergy(String patientId, RecordAllergyRequest)`: `requireRoles("DOCTOR","NURSE","ADMIN")`, verify patient (404), set `createdBy` from auth, `active=true`, save, audit "CREATE"; `listAllergies(String patientId)`: `requireRoles("DOCTOR","NURSE","ADMIN","RECEPTIONIST")`, verify patient, return only `active=true` allergies; `deleteAllergy(String patientId, UUID allergyId)`: `requireRoles("DOCTOR","NURSE","ADMIN")`, fetch by id AND patientId AND active=true (404 if not found or already inactive), set `active=false`, set `updatedBy`/`updatedAt`, save, audit "DELETE"
- [X] T050 [US4] Create `AllergyController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/AllergyController.java` — `@RestController @RequestMapping("/api/v1/patients/{patientId}/allergies")`; `POST /` → 201; `GET /` → 200; `DELETE /{allergyId}` → 204 No Content
- [X] T051 [P] [US4] Create `AllergyServiceTest` in `backend/src/test/java/com/ainexus/hospital/patient/service/AllergyServiceTest.java` — Mockito unit tests: NURSE POST → succeeds, active=true; RECEPTIONIST POST → 403; RECEPTIONIST GET → 200; soft-delete sets active=false; re-delete same (already inactive) → 404; missing substance → validation error; audit called with "ALLERGY"
- [X] T052 [US4] Create `AllergyIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/AllergyIT.java` — Testcontainers integration test; NURSE POST → 201, active=true; RECEPTIONIST GET → 200 list; DOCTOR DELETE → 204; GET after delete → empty list; DELETE again → 404; RECEPTIONIST DELETE → 403
- [X] T053 [US4] Add allergy API functions to `frontend/src/api/emrApi.js` and hooks to `frontend/src/hooks/useEmr.js` — `recordAllergy(patientId, data)`, `getAllergies(patientId)`, `deleteAllergy(patientId, allergyId)`; hooks: `useRecordAllergy(patientId)`, `useAllergies(patientId)`, `useDeleteAllergy(patientId)`; mutations invalidate `['allergies', patientId]`
- [X] T054 [US4] Add Allergies tab to `frontend/src/pages/PatientProfilePage.jsx` — list of allergy cards showing substance, type badge, severity badge (LIFE_THREATENING in red), reaction; "Record Allergy" button for DOCTOR/NURSE/ADMIN; "Remove" soft-delete button for DOCTOR/NURSE/ADMIN; RECEPTIONIST sees list but no action buttons

---

## Phase 7: User Story 5 — Medical Summary (Priority: P2)

**Goal**: Doctor/Admin retrieves complete clinical snapshot in one call.

**Independent Test**: DOCTOR GET `/api/v1/patients/{id}/medical-summary` with patient having all 4 data types → 200 with all four sections + lastVisitDate + totalVisits; patient with no data → 200 with empty lists and `totalVisits=0`; NURSE GET → 403; RECEPTIONIST GET → 403.

- [X] T055 [US5] Create `MedicalSummaryResponse` DTO in `backend/src/main/java/com/ainexus/hospital/patient/dto/MedicalSummaryResponse.java` — record with fields: `String patientId`, `List<ProblemResponse> activeProblems`, `List<MedicationResponse> activeMedications`, `List<AllergyResponse> allergies`, `List<VitalsResponse> recentVitals` (max 5), `LocalDate lastVisitDate` (nullable), `long totalVisits`
- [X] T056 [US5] Create `MedicalSummaryService` in `backend/src/main/java/com/ainexus/hospital/patient/service/MedicalSummaryService.java` — constructor injection of `PatientRepository`, `ProblemRepository`, `MedicationRepository`, `AllergyRepository`, `VitalsRepository`, `AppointmentRepository`, `ProblemMapper`, `MedicationMapper`, `AllergyMapper`, `VitalsMapper`, `RoleGuard`; `getMedicalSummary(String patientId)` annotated `@Transactional(readOnly=true)`: `requireRoles("DOCTOR","ADMIN")`, verify patient (404), query `problemRepository.findByPatientIdAndStatus(patientId, ACTIVE)`, query `medicationRepository.findByPatientIdAndStatus(patientId, ACTIVE)`, query `allergyRepository.findByPatientIdAndActiveTrue(patientId)`, query `vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId)`, count COMPLETED appointments for `totalVisits`, find max `appointmentDate` for `lastVisitDate`; assemble and return `MedicalSummaryResponse` — all lists empty (not null) when no data
- [X] T057 [US5] Add `findTop5ByPatientIdOrderByRecordedAtDesc(String patientId)` to `VitalsRepository` and `countByPatientIdAndStatus(String patientId, String status)` + `findTopByPatientIdAndStatusOrderByAppointmentDateDesc(String patientId, String status)` to `AppointmentRepository` (existing file — additive only)
- [X] T058 [US5] Create `MedicalSummaryController` in `backend/src/main/java/com/ainexus/hospital/patient/controller/MedicalSummaryController.java` — `@RestController`; `GET /api/v1/patients/{patientId}/medical-summary` → `ResponseEntity.ok(medicalSummaryService.getMedicalSummary(patientId))`
- [X] T059 [P] [US5] Create `MedicalSummaryServiceTest` in `backend/src/test/java/com/ainexus/hospital/patient/service/MedicalSummaryServiceTest.java` — Mockito unit tests: patient with all data → all 4 sections populated; new patient → all empty lists and totalVisits=0; recentVitals capped at 5 when >5 exist; NURSE GET → 403; RECEPTIONIST GET → 403
- [X] T060 [US5] Create `MedicalSummaryIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/MedicalSummaryIT.java` — Testcontainers integration test; seed patient with 1 problem, 1 medication, 1 allergy, 6 vitals, 2 COMPLETED appointments; DOCTOR GET → 200, recentVitals has exactly 5, totalVisits=2; patient with no EMR data → 200 all empty; NURSE GET → 403; RECEPTIONIST GET → 403
- [X] T061 [US5] Add medical summary API function to `frontend/src/api/emrApi.js` and hook to `frontend/src/hooks/useEmr.js` — `getMedicalSummary(patientId)`; hook: `useMedicalSummary(patientId)` (enabled only when patientId defined and role is DOCTOR or ADMIN)
- [X] T062 [US5] Create `frontend/src/pages/MedicalSummaryPage.jsx` — route `/patients/:patientId/medical-summary`; uses `useMedicalSummary(patientId)`; displays 4 sections: active problems list, active medications list, allergies list (severity badge), recent vitals mini-table; shows `lastVisitDate` and `totalVisits` in header; handles empty state gracefully per section; loading spinner while fetching
- [X] T063 [US5] Add medical summary route to `frontend/src/App.jsx` — `<Route path="/patients/:patientId/medical-summary" element={<RoleRoute allowedRoles={['DOCTOR','ADMIN']}><MedicalSummaryPage /></RoleRoute>} />`; add "Medical Summary" link/button in `PatientProfilePage.jsx` visible to DOCTOR/ADMIN only (navigates to `/patients/:patientId/medical-summary`)

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Full RBAC validation across all 13 endpoints + build verification.

- [X] T064 Create `EmrRbacIT` in `backend/src/test/java/com/ainexus/hospital/patient/integration/EmrRbacIT.java` — Testcontainers integration test covering the complete RBAC matrix from quickstart.md: for all 13 endpoints, verify: ADMIN → allowed; DOCTOR → allowed (all); NURSE → allowed for vitals+allergies+GET problems+GET medications, denied for POST/PATCH problems and medications, denied for medical-summary; RECEPTIONIST → allowed only GET allergies, denied all 12 others with 403
- [X] T065 Run full backend test suite `cd backend && mvn verify -Pfailsafe` and fix any compilation or test failures — all existing 108 tests must still pass; new EMR tests must pass
- [X] T066 Build frontend and verify Docker stack — `docker compose build frontend` must succeed with 0 errors; run `docker compose up -d` and verify all containers healthy; smoke test the UI: vitals section in appointment detail, profile tabs (Vitals/Problems/Medications/Allergies), Medical Summary page

---

## Dependency Graph

```
Phase 1 (Migrations)
    └─> Phase 2 (Foundational: T006-T012)
            └─> Phase 3 (US1: Vitals)  ────────────────┐
            └─> Phase 4 (US2: Problems) ─────────────── │ independent
            └─> Phase 5 (US3: Medications) ─────────── │ (share foundation
            └─> Phase 6 (US4: Allergies) ──────────── │  not each other)
            └─> Phase 7 (US5: Medical Summary)* ─────┘
                    * depends on US1-US4 repositories/mappers
Phase 3-7 each independently testable
    └─> Phase 8 (Polish: RBAC + build)
```

**US5 note**: `MedicalSummaryService` depends on all 4 EMR repositories and mappers being created (T014-T016, T026-T027, T036-T037, T046-T047). Implement US5 after US1–US4 entities and repositories exist, but the service/controller can be implemented in parallel with US1-US4 frontend work.

---

## Parallel Execution Examples

### Within US1 (after T012 complete):
- T014 (VitalsRepository) + T015 (DTOs) in parallel → then T016 (Mapper) → T017 (Service) → T018 (Controller)
- T019 (VitalsServiceTest) parallel with T018 (Controller)
- T021 (emrApi.js) + T022 (useEmr.js) can start in parallel with T013 (entity)

### Within US2–US4 (after Phase 2):
- Entity + DTOs + Repository in parallel within each story
- Unit test parallel with controller
- Frontend (api+hooks+tab) can be developed in parallel with backend integration test

### Cross-story parallelism:
- US2, US3, US4 backend are fully independent — can be developed in parallel if multiple contributors

---

## Implementation Strategy

**MVP** (deployable increment 1): **Phase 1 + Phase 2 + Phase 3 (US1)**
- Vitals recording in appointment flow + vitals history in patient profile
- Verifiable by: NURSE records vitals → DOCTOR views history → test passes

**Increment 2**: **Phase 4 (US2) + Phase 5 (US3)**
- Problem list + Medication list — core clinical data
- All P1 user stories complete

**Increment 3**: **Phase 6 (US4) + Phase 7 (US5)**
- Allergy registry + Medical summary — completes all P2 stories

**Full release**: **Phase 8** — RBAC validation + build verification + Docker smoke test
