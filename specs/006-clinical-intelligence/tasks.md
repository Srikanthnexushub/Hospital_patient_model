# Tasks: Clinical Intelligence & Safety Module

**Input**: Design documents from `/specs/006-clinical-intelligence/`
**Branch**: `006-clinical-intelligence`
**Prerequisites**: plan.md ✓ spec.md ✓ research.md ✓ data-model.md ✓ contracts/ ✓ quickstart.md ✓

**Tests**: Included per Constitution §III (TDD is NON-NEGOTIABLE — tests written and failing BEFORE implementation)

**Organization**: Tasks grouped by user story. Foundation (Phase 2) is a hard prerequisite for all stories.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on other in-progress tasks)
- **[Story]**: Which user story this task belongs to (US1–US5)
- All file paths are relative to repository root

---

## Phase 1: Setup (Baseline Verification)

**Purpose**: Confirm the existing codebase is green before any Module 6 work begins

- [ ] T001 Verify baseline tests pass by running `mvn test` and `mvn verify -Pfailsafe` in `backend/` — all 108 existing tests must pass before any new code is written

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Flyway migrations, JPA entities, repositories, shared intelligence components, and the ClinicalAlertService that all 5 user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Flyway Migrations (apply in order — each migration must succeed before the next)

- [ ] T002 Create Flyway migration `backend/src/main/resources/db/migration/V22__create_lab_orders.sql` — table `lab_orders` with columns: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `patient_id VARCHAR(14) NOT NULL REFERENCES patients(patient_id)`, `appointment_id VARCHAR(14) REFERENCES appointments(appointment_id)`, `test_name VARCHAR(200) NOT NULL`, `test_code VARCHAR(50)`, `category VARCHAR(30) NOT NULL`, `priority VARCHAR(20) NOT NULL DEFAULT 'ROUTINE'`, `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`, `ordered_by VARCHAR(100) NOT NULL`, `ordered_at TIMESTAMPTZ NOT NULL`, `notes TEXT`, `cancelled_reason TEXT`; plus indexes `idx_lab_orders_patient_id ON (patient_id)` and `idx_lab_orders_patient_status ON (patient_id, status)`

- [ ] T003 Create Flyway migration `backend/src/main/resources/db/migration/V23__create_lab_results.sql` — table `lab_results` with columns: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `order_id UUID NOT NULL UNIQUE REFERENCES lab_orders(id)`, `patient_id VARCHAR(14) NOT NULL REFERENCES patients(patient_id)`, `value TEXT NOT NULL`, `unit VARCHAR(50)`, `reference_range_low NUMERIC(10,3)`, `reference_range_high NUMERIC(10,3)`, `interpretation VARCHAR(30) NOT NULL`, `result_notes TEXT`, `resulted_by VARCHAR(100) NOT NULL`, `resulted_at TIMESTAMPTZ NOT NULL`; plus index `idx_lab_results_patient_id ON (patient_id, resulted_at DESC)`

- [ ] T004 Create Flyway migration `backend/src/main/resources/db/migration/V24__create_clinical_alerts.sql` — table `clinical_alerts` with columns: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `patient_id VARCHAR(14) NOT NULL REFERENCES patients(patient_id)`, `alert_type VARCHAR(40) NOT NULL`, `severity VARCHAR(20) NOT NULL`, `title VARCHAR(200) NOT NULL`, `description TEXT NOT NULL`, `source VARCHAR(200) NOT NULL`, `trigger_value VARCHAR(200)`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `created_at TIMESTAMPTZ NOT NULL`, `acknowledged_at TIMESTAMPTZ`, `acknowledged_by VARCHAR(100)`, `dismissed_at TIMESTAMPTZ`, `dismiss_reason TEXT`; plus indexes `idx_clinical_alerts_patient_id ON (patient_id)`, `idx_clinical_alerts_patient_type_status ON (patient_id, alert_type, status)`, `idx_clinical_alerts_status_severity ON (status, severity)`

### Enums (parallel — all in `backend/src/main/java/com/ainexus/hospital/patient/entity/`)

- [ ] T005 [P] Create enums `LabOrderStatus.java` (PENDING, IN_PROGRESS, RESULTED, CANCELLED), `LabOrderPriority.java` (ROUTINE, URGENT, STAT), `LabOrderCategory.java` (HEMATOLOGY, CHEMISTRY, MICROBIOLOGY, IMMUNOLOGY, URINALYSIS, OTHER) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`

- [ ] T006 [P] Create enum `LabResultInterpretation.java` (NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH, ABNORMAL) in `backend/src/main/java/com/ainexus/hospital/patient/entity/` — add helper method `boolean isCritical()` returning true for CRITICAL_LOW/CRITICAL_HIGH and `boolean isAbnormal()` returning true for LOW/HIGH

- [ ] T007 [P] Create enums `AlertType.java` (LAB_CRITICAL, LAB_ABNORMAL, NEWS2_HIGH, NEWS2_CRITICAL, DRUG_INTERACTION, ALLERGY_CONTRAINDICATION) with helper `boolean isNews2Type()`, `AlertSeverity.java` (INFO, WARNING, CRITICAL), `AlertStatus.java` (ACTIVE, ACKNOWLEDGED, DISMISSED) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`

- [ ] T008 [P] Create enum `InteractionSeverity.java` (MINOR, MODERATE, MAJOR, CONTRAINDICATED) with helper `boolean triggersAlert()` returning true for MAJOR/CONTRAINDICATED in `backend/src/main/java/com/ainexus/hospital/patient/entity/`

### JPA Entities (parallel — depend on T005–T008 enums)

- [ ] T009 [P] Create `LabOrder.java` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/` — `@Entity @Table(name="lab_orders")`, `@Id @GeneratedValue(strategy=GenerationType.UUID) @Column(columnDefinition="uuid") UUID id`, all columns mapped with proper Lombok annotations (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`), `@Enumerated(EnumType.STRING)` for category/priority/status, `@PrePersist` sets `orderedAt = OffsetDateTime.now()` if null

- [ ] T010 [P] Create `LabResult.java` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/` — `@Entity @Table(name="lab_results")`, UUID PK, `orderId UUID`, `patientId String`, `value String`, `unit String`, `referenceRangeLow BigDecimal`, `referenceRangeHigh BigDecimal`, `@Enumerated interpretation LabResultInterpretation`, `resultNotes String`, `resultedBy String`, `resultedAt OffsetDateTime`, `@PrePersist` sets `resultedAt`; same Lombok pattern as T009

- [ ] T011 [P] Create `ClinicalAlert.java` entity in `backend/src/main/java/com/ainexus/hospital/patient/entity/` — `@Entity @Table(name="clinical_alerts")`, UUID PK, `patientId String`, `@Enumerated alertType AlertType`, `@Enumerated severity AlertSeverity`, `title String`, `description String`, `source String`, `triggerValue String`, `@Enumerated status AlertStatus @Builder.Default = AlertStatus.ACTIVE`, `createdAt OffsetDateTime`, `acknowledgedAt OffsetDateTime`, `acknowledgedBy String`, `dismissedAt OffsetDateTime`, `dismissReason String`, `@PrePersist` sets `createdAt`

### Repositories (parallel — depend on T009–T011 entities)

- [ ] T012 [P] Create `LabOrderRepository.java` in `backend/src/main/java/com/ainexus/hospital/patient/repository/` — `JpaRepository<LabOrder, UUID>` with methods: `Page<LabOrder> findByPatientIdOrderByOrderedAtDesc(String patientId, Pageable pageable)`, `Page<LabOrder> findByPatientIdAndStatusOrderByOrderedAtDesc(String patientId, LabOrderStatus status, Pageable pageable)`, `Optional<LabOrder> findByIdAndPatientId(UUID id, String patientId)`

- [ ] T013 [P] Create `LabResultRepository.java` in `backend/src/main/java/com/ainexus/hospital/patient/repository/` — `JpaRepository<LabResult, UUID>` with methods: `Optional<LabResult> findByOrderId(UUID orderId)`, `Page<LabResult> findByPatientIdOrderByResultedAtDesc(String patientId, Pageable pageable)`

- [ ] T014 [P] Create `ClinicalAlertRepository.java` in `backend/src/main/java/com/ainexus/hospital/patient/repository/` — `JpaRepository<ClinicalAlert, UUID>` with methods: `List<ClinicalAlert> findByPatientId(String patientId)`, `Optional<ClinicalAlert> findByPatientIdAndAlertTypeAndStatus(String patientId, AlertType alertType, AlertStatus status)` (for dedup), `Page<ClinicalAlert> findByPatientIdOrderByCreatedAtDesc(String patientId, Pageable pageable)`, native query for global feed with optional status/severity filter and doctor-scoped patient set

### Shared Intelligence Components

- [ ] T015 Create `News2Calculator.java` in `backend/src/main/java/com/ainexus/hospital/patient/intelligence/` — `@Component` stateless bean with `public News2Result compute(PatientVitals vitals)` method implementing the full NHS NEWS2 algorithm: score each of 6 parameters (respiratoryRate, oxygenSaturation, bloodPressureSystolic, heartRate, temperature, consciousness defaults to 0), sum total, track `anyParameterScoredThree` boolean, apply risk classification logic (total=0→LOW; total 1-4 no single 3→LOW_MEDIUM; total 1-4 with any 3→MEDIUM; total 5-6→MEDIUM; total≥7→HIGH), build component list including defaulted parameters, return `News2Result` record with totalScore/riskLevel/riskColour/recommendation/components/basedOnVitalsId/computedAt; also create `News2Result.java` and `News2ComponentScore.java` records in the intelligence package

### Clinical Alert Service (shared by US1, US2, US3, US4)

- [ ] T016 Create `ClinicalAlertService.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/` with these `@Transactional` methods: (1) `createAlert(String patientId, AlertType alertType, AlertSeverity severity, String title, String description, String source, String triggerValue)` — for NEWS2 alert types calls `clinicalAlertRepository.findByPatientIdAndAlertTypeAndStatus()` and auto-dismisses any existing ACTIVE alert before saving new one; (2) `acknowledge(UUID alertId)` — sets `acknowledgedAt=now, acknowledgedBy=ctx.username`; (3) `dismiss(UUID alertId, String reason)` — sets `status=DISMISSED, dismissedAt=now, dismissReason`; (4) `getPatientAlerts(String patientId, AlertStatus status, AlertSeverity severity, Pageable pageable)` — paginated query; (5) `getGlobalAlerts(AlertStatus status, AlertSeverity severity, Pageable pageable)` — DOCTOR-scoped (subquery on appointments.doctor_id), NURSE/ADMIN see all; RBAC via RoleGuard at start of each method; audit log for mutations via `EmrAuditService`; also create `ClinicalAlertResponse.java` record, `DismissAlertRequest.java` request DTO, and `ClinicalAlertMapper.java` MapStruct mapper in their respective packages

### Unit Tests Written First (TDD — these must FAIL before T015 and T016 are implemented)

- [ ] T017 Write `ClinicalAlertServiceTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/` covering: (1) createAlert with NEWS2 type auto-dismisses existing ACTIVE alert, (2) createAlert with non-NEWS2 type does NOT auto-dismiss, (3) acknowledge sets correct fields from AuthContext, (4) dismiss sets DISMISSED status + reason, (5) getGlobalAlerts returns only DOCTOR's patients when role=DOCTOR, (6) getGlobalAlerts returns all patients when role=ADMIN — run `mvn test` to confirm all 6 fail (RED phase)

- [ ] T018 Write `News2CalculatorTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/intelligence/` covering all NHS scoring branches: (1) RR≤8→3, 9-11→1, 12-20→0, 21-24→2, ≥25→3 (2) SpO2≤91→3, 92-93→2, 94-95→1, ≥96→0 (3) SBP≤90→3, 91-100→2, 101-110→1, 111-219→0, ≥220→3 (4) HR≤40→3, 41-50→1, 51-90→0, 91-110→1, 111-130→2, ≥131→3 (5) Temp≤35.0→3, 35.1-36.0→1, 36.1-38.0→0, 38.1-39.0→1, ≥39.1→2 (6) all-normal→LOW (7) 1-4 no-single-3→LOW_MEDIUM (8) 1-4 with-single-3→MEDIUM (9) total-5→MEDIUM (10) total≥7→HIGH (11) null vitals field defaults score=0 and sets defaulted=true in component (12) full high-risk scenario returns correct riskColour=red — run `mvn test` to confirm all fail (RED phase)

**Checkpoint**: T002–T018 complete — foundation is ready. All 6+12 unit tests are failing (RED). Implementation in Phase 3+ will make them GREEN.

---

## Phase 3: User Story 1 — Lab Orders & Results (Priority: P1) 🎯 MVP

**Goal**: Doctors order lab tests; nurses record results; CRITICAL/HIGH interpretations auto-create ClinicalAlerts.

**Independent Test**: Run `LabOrderIT.java` — POST lab order → POST critical result → verify CRITICAL ClinicalAlert created in same transaction. No other Module 6 feature needed.

### Unit and Integration Tests (write first — must FAIL before T024)

- [ ] T019 [P] [US1] Write `LabOrderServiceTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/` covering: (1) createOrder — saves with status=PENDING and orderedBy from AuthContext, (2) recordResult with CRITICAL_HIGH — calls ClinicalAlertService.createAlert with severity=CRITICAL type=LAB_CRITICAL, (3) recordResult with HIGH — calls createAlert with severity=WARNING type=LAB_ABNORMAL, (4) recordResult with NORMAL — does NOT call createAlert, (5) recordResult on already-RESULTED order throws ConflictException, (6) listOrders with status filter returns only matching orders, (7) RECEPTIONIST role throws ForbiddenException, (8) patient not found throws ResourceNotFoundException — run `mvn test` to confirm all fail

- [ ] T020 [P] [US1] Write `LabOrderIT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` extending `BaseIntegrationTest` covering: (1) POST /patients/{id}/lab-orders as DOCTOR returns 201 with PENDING status, (2) POST /lab-orders/{id}/result as NURSE with CRITICAL_HIGH returns 201 and GET /patients/{id}/alerts shows CRITICAL alert, (3) POST /lab-orders/{id}/result with NORMAL returns 201 and no alert created, (4) GET /patients/{id}/lab-orders?status=PENDING returns only pending orders, (5) GET /patients/{id}/lab-results returns paginated results sorted by resultedAt DESC, (6) RECEPTIONIST returns 403 on all endpoints, (7) duplicate result on RESULTED order returns 409 — run `mvn verify -Pfailsafe` to confirm all fail

### DTOs and Mappers (parallel — no service dependency)

- [ ] T021 [P] [US1] Create request/response DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `CreateLabOrderRequest.java` record with fields (testName String @NotBlank @Size(max=200), testCode String, category LabOrderCategory @NotNull, appointmentId String, priority LabOrderPriority, notes String); `LabOrderResponse.java` record (id UUID, patientId, testName, testCode, category, priority, status, orderedBy, orderedAt, appointmentId, notes); `LabOrderSummaryResponse.java` record (id, patientId, testName, category, priority, status, orderedBy, orderedAt, hasResult boolean)

- [ ] T022 [P] [US1] Create request/response DTOs: `RecordLabResultRequest.java` record with fields (value String @NotBlank, unit String, referenceRangeLow BigDecimal, referenceRangeHigh BigDecimal, interpretation LabResultInterpretation @NotNull, resultNotes String); `LabResultResponse.java` record (id UUID, orderId UUID, patientId, testName, category, value, unit, referenceRangeLow, referenceRangeHigh, interpretation, resultNotes, resultedBy, resultedAt, alertCreated boolean, alertId UUID) in `backend/src/main/java/com/ainexus/hospital/patient/dto/`

- [ ] T023 [P] [US1] Create `LabOrderMapper.java` and `LabResultMapper.java` MapStruct mappers in `backend/src/main/java/com/ainexus/hospital/patient/mapper/` — `@Mapper(componentModel="spring")` annotation; LabOrderMapper maps LabOrder→LabOrderResponse and LabOrder→LabOrderSummaryResponse (hasResult=false, populated by service); LabResultMapper maps LabResult→LabResultResponse

### Service (depends on T021–T023)

- [ ] T024 [US1] Create `LabOrderService.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/` with `@Transactional` methods: (1) `createLabOrder(String patientId, CreateLabOrderRequest request)` — roleGuard.requireRoles(DOCTOR,ADMIN), verify patient exists, build and save LabOrder entity, call emrAuditService, return LabOrderResponse; (2) `recordLabResult(UUID orderId, RecordLabResultRequest request)` — roleGuard.requireRoles(NURSE,DOCTOR,ADMIN), load LabOrder or throw ResourceNotFoundException, throw ConflictException if status=RESULTED/CANCELLED, save LabResult, advance order status to RESULTED, if interpretation.isCritical() call clinicalAlertService.createAlert(CRITICAL,LAB_CRITICAL), else if interpretation.isAbnormal() call createAlert(WARNING,LAB_ABNORMAL), call emrAuditService for both entities, return LabResultResponse with alertCreated/alertId; (3) `getLabOrders(String patientId, LabOrderStatus status, Pageable pageable)` — roleGuard.requireRoles(DOCTOR,NURSE,ADMIN); (4) `getLabResults(String patientId, Pageable pageable)` — roleGuard.requireRoles(DOCTOR,NURSE,ADMIN)

### Controller (depends on T024)

- [ ] T025 [US1] Create `LabOrderController.java` in `backend/src/main/java/com/ainexus/hospital/patient/controller/` — `@RestController @RequestMapping("/api/v1")`: (1) `POST /patients/{patientId}/lab-orders` → `labOrderService.createLabOrder()` returns 201; (2) `GET /patients/{patientId}/lab-orders` with optional `status` and pagination params → 200; (3) `POST /lab-orders/{orderId}/result` → `labOrderService.recordLabResult()` returns 201; (4) `GET /patients/{patientId}/lab-results` with pagination → 200; inject LabOrderService, use `@Valid` on request bodies

**Checkpoint**: US1 complete — `LabOrderIT` passes, CRITICAL lab result auto-creates a ClinicalAlert. MVP is deliverable.

---

## Phase 4: User Story 2 — NEWS2 Early Warning Score (Priority: P1)

**Goal**: GET /patients/{id}/news2 computes NHS NEWS2 score from latest vitals and auto-creates CRITICAL/WARNING alerts with deduplication.

**Independent Test**: Run `News2IT.java` — record elevated vitals via existing EMR endpoint → GET /news2 → verify score ≥ 7 and riskLevel=HIGH and ClinicalAlert with type=NEWS2_CRITICAL created. Then re-run with normal vitals and verify old alert auto-dismissed.

### Tests (write first — must FAIL before T029)

- [ ] T026 [P] [US2] Write `News2ServiceTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/` covering: (1) patient with HIGH vitals → totalScore ≥ 7, createAlert called with NEWS2_CRITICAL, (2) patient with MEDIUM vitals (total 5, no single 3) → createAlert called with NEWS2_HIGH WARNING, (3) patient with MEDIUM vitals (total 3, one param scored 3) → createAlert called with NEWS2_HIGH WARNING, (4) patient with LOW vitals → no alert created, existing NEWS2 alerts dismissed, (5) patient with no vitals → returns NO_DATA response, no alert, (6) RECEPTIONIST throws ForbiddenException, (7) News2Calculator called with correct PatientVitals object, (8) deduplication: verify clinicalAlertService called, not raw repository — use mocks for News2Calculator and ClinicalAlertService; run `mvn test` to confirm all fail

- [ ] T027 [P] [US2] Write `News2IT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` covering: (1) record HIGH vitals → GET /news2 → 200 with riskLevel=HIGH riskColour=red → alert exists in DB, (2) record NORMAL vitals → GET /news2 → riskLevel=LOW no new alert, prior HIGH alert dismissed, (3) no vitals → GET /news2 → totalScore null riskLevel=NO_DATA, (4) DOCTOR and NURSE both return 200, (5) RECEPTIONIST returns 403; run `mvn verify -Pfailsafe` to confirm all fail

### DTOs (parallel)

- [ ] T028 [P] [US2] Create `News2Response.java` record in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/` with fields (Integer totalScore, String riskLevel, String riskColour, String recommendation, List<News2ComponentScoreResponse> components, Long basedOnVitalsId, OffsetDateTime computedAt, String message, boolean alertCreated, UUID alertId); create `News2ComponentScoreResponse.java` record (String parameter, Number value, Integer score, String unit, boolean defaulted) in same package

### Service and Controller

- [ ] T029 [US2] Create `News2Service.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/` with `@Transactional` method `computeNews2(String patientId)`: roleGuard.requireRoles(DOCTOR,NURSE,ADMIN), verify patient exists via patientRepository.existsById, load latest vitals via `vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId).stream().findFirst()`, if empty return NO_DATA response, else call `news2Calculator.compute(vitals)`, if riskLevel=MEDIUM call `clinicalAlertService.createAlert(patientId, NEWS2_HIGH, WARNING, ...)`, if riskLevel=HIGH call `clinicalAlertService.createAlert(patientId, NEWS2_CRITICAL, CRITICAL, ...)`, if riskLevel=LOW/LOW_MEDIUM call internal method to dismiss any active NEWS2 alerts for this patient, map News2Result to News2Response and return; note: createAlert in ClinicalAlertService already handles dedup via auto-dismiss of old alert

- [ ] T030 [US2] Create `News2Controller.java` in `backend/src/main/java/com/ainexus/hospital/patient/controller/` — `@RestController @RequestMapping("/api/v1")`: single endpoint `GET /patients/{patientId}/news2` → calls `news2Service.computeNews2(patientId)` → returns 200 News2Response

**Checkpoint**: US2 complete — NEWS2 score computed and returns correct risk levels with colour, recommendation, and auto-created deduped alerts.

---

## Phase 5: User Story 3 — Drug Interaction & Allergy Contraindication Checker (Priority: P2)

**Goal**: Doctors check a drug against active medications and allergies before prescribing. MAJOR/CONTRAINDICATED interactions auto-create CRITICAL alerts.

**Independent Test**: Run `DrugInteractionIT.java` — seed patient with Aspirin medication and Penicillin allergy → POST /interaction-check with {drugName:"Warfarin"} → verify MAJOR interaction found and DRUG_INTERACTION alert created → POST with {drugName:"Amoxicillin"} → verify ALLERGY_CONTRAINDICATION alert.

### Drug Interaction Database (needed before tests and service)

- [ ] T031 [US3] Create `DrugInteractionDatabase.java` in `backend/src/main/java/com/ainexus/hospital/patient/intelligence/` — `@Component` with `Map<String, List<DrugInteractionEntry>> index = new HashMap<>()` and inner record `DrugInteractionEntry(String drug1, String drug2, InteractionSeverity severity, String mechanism, String clinicalEffect, String recommendation)`; `@PostConstruct void init()` registers ALL 40+ pairs listed below via private `register(d1, d2, severity, mechanism, effect, recommendation)` helper that normalizes names to lowercase and indexes both directions; `public List<DrugInteractionEntry> findInteractionsFor(String drugName)` returns `index.getOrDefault(drugName.toLowerCase().trim(), List.of())`; register ALL pairs from contracts/us3-drug-interactions.md: Anticoagulants (8: Warfarin+Aspirin MAJOR, Warfarin+Ibuprofen MAJOR, Warfarin+Naproxen MAJOR, Warfarin+Clopidogrel MAJOR, Warfarin+Metronidazole MAJOR, Warfarin+Fluconazole MAJOR, Warfarin+Amiodarone MAJOR, Heparin+Aspirin MAJOR); Cardiac (6: Digoxin+Amiodarone MAJOR, Digoxin+Clarithromycin MAJOR, ACE inhibitor+Spironolactone MAJOR, ACE inhibitor+Potassium-sparing diuretic MAJOR, Metoprolol+Verapamil MAJOR, Amlodipine+Simvastatin MODERATE); CNS (6: SSRI+MAOI CONTRAINDICATED, SSRI+Tramadol MAJOR, SSRI+Triptans MODERATE, Benzodiazepine+Opioid MAJOR, Lithium+NSAIDs MAJOR, Valproate+Aspirin MODERATE); Antibiotics (7: Ciprofloxacin+Theophylline MAJOR, Ciprofloxacin+Antacids MODERATE, Metronidazole+Alcohol MAJOR, Clarithromycin+Statins MAJOR, Fluconazole+Midazolam CONTRAINDICATED, Rifampicin+Oral contraceptives MAJOR, Tetracycline+Antacids MODERATE); Diabetes (4: Metformin+Contrast media MAJOR, Metformin+Alcohol MODERATE, Insulin+Beta-blockers MODERATE, Sulfonylurea+Fluconazole MAJOR); Respiratory (3: Theophylline+Ciprofloxacin MAJOR, Theophylline+Erythromycin MAJOR, Beta-agonist+Non-selective beta-blocker MAJOR); OTC/Others (10: Ibuprofen+ACE inhibitor MODERATE, Aspirin+Methotrexate MAJOR, Aspirin+Corticosteroids MODERATE, Simvastatin+Amiodarone MAJOR, Clopidogrel+Omeprazole MODERATE, Phenytoin+Valproate MODERATE, Carbamazepine+Oral contraceptives MAJOR, Lithium+Thiazide diuretics MAJOR, Haloperidol+Lithium MAJOR, Levodopa+Antipsychotics MAJOR)

### Tests (write first — must FAIL before T036)

- [ ] T032 [P] [US3] Write `DrugInteractionDatabaseTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/intelligence/` covering: (1) database contains at least 40 entries total, (2) findInteractionsFor("warfarin") returns entries including Warfarin+Aspirin MAJOR, (3) findInteractionsFor("aspirin") also returns Warfarin+Aspirin (bidirectional), (4) findInteractionsFor("WARFARIN") works (case-insensitive), (5) findInteractionsFor("unknown") returns empty list, (6) SSRI+MAOI entry has severity=CONTRAINDICATED, (7) all 6 categories have at least one entry — run `mvn test` to confirm all fail

- [ ] T033 [P] [US3] Write `DrugInteractionServiceTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/` covering: (1) MAJOR interaction found → safe=false, createAlert called with DRUG_INTERACTION CRITICAL, (2) CONTRAINDICATED found → createAlert called, (3) MODERATE interaction found → safe=false but createAlert NOT called, (4) no interaction → safe=true, no alert, (5) allergy match (substring: Penicillin → Amoxicillin) → allergyContraindications non-empty, safe=false, alert created with ALLERGY_CONTRAINDICATION, (6) case-insensitive allergy match works, (7) NURSE calling POST /interaction-check service throws ForbiddenException, (8) getInteractionSummary returns all pairwise interactions across active medications — run `mvn test` to confirm all fail

- [ ] T034 [P] [US3] Write `DrugInteractionIT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` covering: (1) seed Aspirin medication → POST interaction-check with Warfarin → 200 safe=false MAJOR interaction → DB has DRUG_INTERACTION CRITICAL alert, (2) seed Penicillin allergy → POST interaction-check with Amoxicillin → 200 allergyContraindications non-empty → DB has ALLERGY_CONTRAINDICATION alert, (3) POST interaction-check with unknown drug → 200 safe=true no alert, (4) GET /interaction-summary with 2 active meds returns known interactions, (5) NURSE POST returns 403, NURSE GET /interaction-summary returns 200, (6) RECEPTIONIST returns 403 on both endpoints — run `mvn verify -Pfailsafe` to confirm all fail

### DTOs (parallel)

- [ ] T035 [P] [US3] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/`: `DrugInteractionCheckRequest.java` record (drugName String @NotBlank @Size(max=200)); `DrugInteractionEntryResponse.java` record (drug1, drug2, severity InteractionSeverity, mechanism, clinicalEffect, recommendation — all String); `AllergyContraindicationResponse.java` record (allergyId UUID, substance, matchedDrug, severity AllergySeverity, reaction, recommendation — all String); `DrugInteractionCheckResponse.java` record (drugName, List<DrugInteractionEntryResponse> interactions, List<AllergyContraindicationResponse> allergyContraindications, boolean safe, OffsetDateTime checkedAt, boolean alertCreated, UUID alertId); `InteractionSummaryResponse.java` record (patientId, activeMedicationCount int, List<DrugInteractionEntryResponse> interactions, int interactionCount, int highSeverityCount, OffsetDateTime checkedAt)

### Service and Controller

- [ ] T036 [US3] Create `DrugInteractionService.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/` with: (1) `checkInteractions(String patientId, DrugInteractionCheckRequest request)` — roleGuard.requireRoles(DOCTOR,ADMIN), verify patient, load `activeMedications = medicationRepository.findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE)`, load `activeAllergies = allergyRepository.findByPatientIdAndActiveTrue(patientId)`, for each active medication lookup `drugInteractionDatabase.findInteractionsFor(request.drugName())` and filter for entries where drug2 matches the medication name (normalized), collect all interactions, check for allergy contraindications using case-insensitive substring match (`allergySubstance.toLowerCase().contains(drugName.toLowerCase()) || drugName.toLowerCase().contains(allergySubstance.toLowerCase())`), if any interaction.severity().triggersAlert() call `clinicalAlertService.createAlert(DRUG_INTERACTION, CRITICAL, ...)`, if any allergy contraindication found call `clinicalAlertService.createAlert(ALLERGY_CONTRAINDICATION, CRITICAL, ...)`, return DrugInteractionCheckResponse; (2) `getInteractionSummary(String patientId)` — roleGuard.requireRoles(DOCTOR,NURSE,ADMIN), load active meds, run all pairwise lookups, aggregate results

- [ ] T037 [US3] Create `DrugInteractionController.java` in `backend/src/main/java/com/ainexus/hospital/patient/controller/` — `@RestController @RequestMapping("/api/v1")`: (1) `POST /patients/{patientId}/interaction-check` → `drugInteractionService.checkInteractions()` → 200; (2) `GET /patients/{patientId}/interaction-summary` → `drugInteractionService.getInteractionSummary()` → 200

**Checkpoint**: US3 complete — drug interaction and allergy checking works, MAJOR/CONTRAINDICATED interactions and allergy matches auto-create alerts.

---

## Phase 6: User Story 4 — Clinical Alerts Feed (Priority: P2)

**Goal**: REST API for the unified alert feed — per-patient view, global feed with role-scoping, acknowledge, dismiss actions. ClinicalAlertService already implemented in Phase 2; this phase adds only the controller and integration tests.

**Independent Test**: Seed a ClinicalAlert directly in DB → call GET /patients/{id}/alerts → verify it appears → PATCH /acknowledge → verify acknowledgedAt set → PATCH /dismiss with reason → verify status=DISMISSED.

### Integration Tests (write first — must FAIL before T039)

- [ ] T038 [US4] Write `ClinicalAlertIT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` covering: (1) GET /patients/{id}/alerts returns all alerts for patient, (2) GET /patients/{id}/alerts?status=ACTIVE returns only ACTIVE, (3) GET /alerts as ADMIN returns all patients' alerts, (4) GET /alerts as DOCTOR returns only own-appointment patients' alerts, (5) GET /alerts as NURSE returns all patients' alerts, (6) PATCH /alerts/{id}/acknowledge sets acknowledgedAt+acknowledgedBy from JWT, status stays ACTIVE, (7) PATCH /alerts/{id}/dismiss with {reason} sets status=DISMISSED dismissReason set, (8) PATCH /dismiss with blank reason returns 400, (9) RECEPTIONIST on all endpoints returns 403, (10) GET /alerts?severity=CRITICAL returns only CRITICAL severity alerts — run `mvn verify -Pfailsafe` to confirm all fail

### Controller

- [ ] T039 [US4] Create `ClinicalAlertController.java` in `backend/src/main/java/com/ainexus/hospital/patient/controller/` — `@RestController @RequestMapping("/api/v1")`: (1) `GET /patients/{patientId}/alerts` with optional status/severity query params and pagination → `clinicalAlertService.getPatientAlerts()` → 200 PagedResponse<ClinicalAlertResponse>; (2) `GET /alerts` with optional status/severity/alertType query params and pagination → `clinicalAlertService.getGlobalAlerts()` → 200 PagedResponse<ClinicalAlertResponse>; (3) `PATCH /alerts/{alertId}/acknowledge` → `clinicalAlertService.acknowledge(alertId)` → 200 ClinicalAlertResponse; (4) `PATCH /alerts/{alertId}/dismiss` with `@Valid @RequestBody DismissAlertRequest` → `clinicalAlertService.dismiss(alertId, reason)` → 200 ClinicalAlertResponse

**Checkpoint**: US4 complete — full alert lifecycle works via REST; DOCTOR sees own patients' alerts, NURSE/ADMIN sees all.

---

## Phase 7: User Story 5 — Patient Risk Dashboard (Priority: P2)

**Goal**: Risk-ranked paginated patient list sorted by criticalAlertCount DESC → news2Score DESC NULLS LAST → warningAlertCount DESC; system-wide stats snapshot.

**Independent Test**: Run `PatientRiskDashboardIT.java` — seed patients with known alert counts → GET /dashboard/patient-risk → verify sort order → GET /dashboard/stats → verify aggregate counts.

### Tests (write first — must FAIL before T043)

- [ ] T040 [P] [US5] Write `PatientRiskDashboardServiceTest.java` in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/` covering: (1) getRiskRankedPatients with ADMIN role returns all patients, (2) getRiskRankedPatients with DOCTOR role passes doctorId to scoped query, (3) patient with no vitals has news2Score=null and riskLevel=NO_DATA in result, (4) getStats returns correct totalActiveAlerts and alertsByType breakdown, (5) RECEPTIONIST throws ForbiddenException; mock ClinicalAlertRepository, PatientRepository, VitalsRepository, News2Calculator — run `mvn test` to confirm all fail

- [ ] T041 [P] [US5] Write `PatientRiskDashboardIT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` covering: (1) seed 3 patients with different alert/vitals profiles → GET /dashboard/patient-risk → verify sort order (highest criticalAlertCount first), (2) DOCTOR sees only own-appointment patients, (3) ADMIN sees all, (4) GET /dashboard/stats shows correct counts, (5) RECEPTIONIST returns 403 on both endpoints, (6) pagination works (page=0, size=1 returns 1 item) — run `mvn verify -Pfailsafe` to confirm all fail

### DTOs (parallel)

- [ ] T042 [P] [US5] Create DTOs in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`: `PatientRiskRow.java` record (patientId, patientName, bloodGroup, Integer news2Score, String news2RiskLevel, String news2RiskColour, long criticalAlertCount, long warningAlertCount, long activeMedicationCount, long activeProblemCount, long activeAllergyCount, OffsetDateTime lastVitalsAt, LocalDate lastVisitDate); `DashboardStatsResponse.java` record (long totalActivePatients, long patientsWithCriticalAlerts, long patientsWithHighNews2, long totalActiveAlerts, long totalCriticalAlerts, long totalWarningAlerts, List<AlertTypeCount> alertsByType, OffsetDateTime generatedAt); `AlertTypeCount.java` record (String alertType, long count)

### Service and Controller

- [ ] T043 [US5] Create `PatientRiskDashboardService.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/` with: (1) `getRiskRankedPatients(Pageable pageable)` — roleGuard.requireRoles(DOCTOR,ADMIN), determine doctorId (null for ADMIN, AuthContext userId for DOCTOR), run native SQL query via ClinicalAlertRepository or a new DashboardRepository that JOINs patients LEFT JOIN clinical_alerts grouping by patient to get criticalAlertCount/warningAlertCount/activeMedicationCount/activeProblemCount/activeAllergyCount/lastVitalsAt, for each row in result load latest PatientVitals via `vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId).stream().findFirst()`, compute NEWS2 via `news2Calculator.compute(vitals)` or set NO_DATA, sort in-memory or via SQL ORDER BY `criticalAlertCount DESC, news2Score DESC NULLS LAST, warningAlertCount DESC`, build PatientRiskRow list, return as Page; (2) `getStats()` — roleGuard.requireRoles(DOCTOR,ADMIN), query counts using ClinicalAlertRepository methods and PatientRepository, build DashboardStatsResponse with alertsByType breakdown via `GROUP BY alert_type` query

- [ ] T044 [US5] Create `PatientRiskDashboardController.java` in `backend/src/main/java/com/ainexus/hospital/patient/controller/` — `@RestController @RequestMapping("/api/v1/dashboard")`: (1) `GET /patient-risk` with pagination params → `dashboardService.getRiskRankedPatients()` → 200; (2) `GET /stats` → `dashboardService.getStats()` → 200 DashboardStatsResponse

**Checkpoint**: US5 complete — risk dashboard shows patients ranked by danger level; DOCTOR scoped; ADMIN sees all.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: RBAC role matrix verification across all 13 endpoints; frontend integration; final validation.

- [ ] T045 [P] Write `ClinicalIntelligenceRbacIT.java` in `backend/src/test/java/com/ainexus/hospital/patient/integration/` — comprehensive role matrix test for all 4 roles × all 13 endpoints: RECEPTIONIST must get 403 on all 13; NURSE must get 403 on `POST /lab-orders`, `POST /interaction-check`, `PATCH /acknowledge`, `PATCH /dismiss`, `GET /dashboard/*`; NURSE must get 200 on `GET /lab-orders`, `GET /lab-results`, `GET /news2`, `GET /interaction-summary`, `GET /alerts`; DOCTOR must get 200 on all endpoints; ADMIN must get 200 on all endpoints — run `mvn verify -Pfailsafe` to confirm all pass

- [ ] T046 [P] Create frontend API hooks `frontend/src/api/labOrders.js` — exports: `useLabOrders(patientId, status, page)` (GET with `refetchInterval: 30000`), `useLabResults(patientId, page)`, `useCreateLabOrder(patientId)` (mutation), `useRecordLabResult(orderId)` (mutation); all using existing `apiClient` Axios instance and TanStack Query v5 pattern from other api/ files

- [ ] T047 [P] Create frontend API hooks `frontend/src/api/news2.js` (useNews2(patientId) with refetchInterval:30000), `frontend/src/api/clinicalAlerts.js` (usePatientAlerts, useGlobalAlerts with status/severity filters, useAcknowledgeAlert, useDismissAlert mutations), `frontend/src/api/drugInteractions.js` (useInteractionCheck mutation, useInteractionSummary), `frontend/src/api/dashboard.js` (useRiskDashboard(page) with refetchInterval:30000, useDashboardStats with refetchInterval:30000)

- [ ] T048 [P] Create `frontend/src/pages/ClinicalAlertsFeedPage.jsx` — global alerts view: fetch `useGlobalAlerts` with status/severity filter dropdowns, display alerts sorted by severity (CRITICAL first), each alert row shows patientName/alertType/title/triggerValue/createdAt, ACTIVE alerts show Acknowledge and Dismiss buttons (DOCTOR/ADMIN only), Dismiss opens modal with reason textarea, auto-refresh every 30s; responsive table layout using Tailwind

- [ ] T049 [P] Create `frontend/src/pages/PatientRiskDashboardPage.jsx` — risk dashboard: fetch `useRiskDashboard` and `useDashboardStats`, show 4 stat cards (totalActivePatients, patientsWithCriticalAlerts, patientsWithHighNews2, totalActiveAlerts), show paginated risk table with colour-coded NEWS2 score (red=HIGH orange=MEDIUM yellow=LOW_MEDIUM green=LOW grey=NO_DATA), sort indicators, alert count badges; DOCTOR and ADMIN only (hide from NURSE in navigation)

- [ ] T050 [P] Create frontend components in `frontend/src/components/`: `lab/LabOrderForm.jsx` (create order form with category/priority dropdowns, DOCTOR/ADMIN only), `lab/LabOrderList.jsx` (list with status filter tabs, hasResult indicator), `lab/RecordResultForm.jsx` (modal form for nurse/doctor to record result with interpretation dropdown and reference range fields), `news2/News2ScoreCard.jsx` (score badge with colour ring, component breakdown expandable list), `alerts/AlertFeed.jsx` (filterable list with acknowledge/dismiss inline actions), `alerts/AlertBadge.jsx` (severity-coloured chip component), `dashboard/RiskDashboardTable.jsx` (paginated sortable table), `dashboard/DashboardStatsCard.jsx` (stat card with icon and count)

- [ ] T051 Integrate Module 6 features into `frontend/src/pages/PatientProfilePage.jsx` — add a new "Labs" tab to the existing tab bar showing LabOrderList + LabOrderForm (for DOCTOR/ADMIN); add News2ScoreCard to the header section alongside existing patient stats (visible to DOCTOR/NURSE/ADMIN); add "Check Interaction" button near the Medications tab header (DOCTOR/ADMIN only) that opens a modal with DrugInteractionCheckRequest form; add AlertBadge count to the page header showing active alert count for this patient; update API imports

- [ ] T052 Add routing for new pages in `frontend/src/App.jsx` (or router config file) — add protected routes: `/alerts` → ClinicalAlertsFeedPage (DOCTOR, NURSE, ADMIN), `/dashboard` → PatientRiskDashboardPage (DOCTOR, ADMIN); add navigation links to sidebar/navbar; add `refetchInterval` options to existing `useAlerts` calls where appropriate; run `npm test` to verify existing frontend tests still pass

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational: T002–T018)  ← BLOCKS everything below
    ↓
Phase 3 (US1) ──┐
Phase 4 (US2) ──┤ All start after Phase 2. Can run in parallel if staffed.
Phase 5 (US3) ──┤
Phase 6 (US4) ──┤ (ClinicalAlertService already in Phase 2)
Phase 7 (US5) ──┘
    ↓
Phase 8 (Polish)
```

### User Story Dependencies

| Story | Depends On | Notes |
|---|---|---|
| US1 (P1) | Phase 2 only | ClinicalAlertService available from Phase 2 |
| US2 (P1) | Phase 2 only | News2Calculator available from Phase 2 |
| US3 (P2) | Phase 2 only | DrugInteractionDatabase built in US3 itself |
| US4 (P2) | Phase 2 only | ClinicalAlertService already built; only controller needed |
| US5 (P2) | Phase 2 + US2 ideally | Uses News2Calculator from Phase 2; benefits from US2 being tested first |

### Within Each User Story (TDD Order)
```
Write tests → confirm FAIL → implement DTOs + Mappers (parallel) → implement Service → implement Controller → run tests → confirm PASS
```

### Parallel Opportunities

- **Phase 2 Enums**: T005, T006, T007, T008 all parallel
- **Phase 2 Entities**: T009, T010, T011 all parallel (after enums)
- **Phase 2 Repositories**: T012, T013, T014 all parallel (after entities)
- **Phase 2 Tests**: T017, T018 parallel (written before T015/T016)
- **Each US phase**: tests + DTOs + mapper all parallel; service and controller sequential after
- **Phase 8**: T045–T050 all parallel (different files)

---

## Parallel Execution Examples

### Phase 2 — Foundation Parallel Blocks

```
# Enums (all parallel after T002–T004 migrations):
T005: LabOrderStatus + LabOrderPriority + LabOrderCategory
T006: LabResultInterpretation
T007: AlertType + AlertSeverity + AlertStatus
T008: InteractionSeverity

# Entities (all parallel after T005–T008):
T009: LabOrder entity
T010: LabResult entity
T011: ClinicalAlert entity

# Repositories (all parallel after T009–T011):
T012: LabOrderRepository
T013: LabResultRepository
T014: ClinicalAlertRepository

# Tests (parallel with each other, written before T015/T016):
T017: ClinicalAlertServiceTest
T018: News2CalculatorTest
```

### Phase 3 — US1 Parallel Block

```
# Simultaneously after T018:
T019: LabOrderServiceTest (write first)
T020: LabOrderIT (write first)
T021: Request/Response DTOs
T022: RecordResult DTOs
T023: MapStruct Mappers
# Then sequential:
T024: LabOrderService
T025: LabOrderController
```

---

## Implementation Strategy

### MVP First (US1 + US2 only — both P1)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002–T018) — CRITICAL
3. Complete Phase 3: US1 — Lab Orders (T019–T025)
4. **STOP and VALIDATE**: `mvn verify -Pfailsafe` → `LabOrderIT` passes, CRITICAL result creates alert
5. Complete Phase 4: US2 — NEWS2 (T026–T030)
6. **STOP and VALIDATE**: `News2IT` passes, score correct, dedup works
7. **Deploy/Demo MVP** — life-safety alert generation is functional

### Incremental Delivery

1. MVP (US1 + US2) → Demo to clinical team
2. US3 (Drug Interactions) → Demo "safe prescribing" workflow
3. US4 (Alerts Feed) → Demo triage dashboard for nurses
4. US5 (Risk Dashboard) → Demo shift handover view
5. Phase 8 (Frontend + RBAC) → Full production-ready module

### Single-Developer Sequential Order

`T001 → T002–T004 (sequential migrations) → T005–T008 (parallel enums) → T009–T011 (parallel entities) → T012–T014 (parallel repos) → T015 → T016 → T017–T018 (write tests) → T019–T023 (US1 tests+DTOs parallel) → T024 → T025 → T026–T028 (US2 tests+DTOs parallel) → T029 → T030 → T031 → T032–T035 (US3 tests+DTOs parallel) → T036 → T037 → T038 → T039 → T040–T042 (US5 tests+DTOs parallel) → T043 → T044 → T045–T052 (polish parallel)`

---

## Task Summary

| Phase | Tasks | Count |
|---|---|---|
| Phase 1: Setup | T001 | 1 |
| Phase 2: Foundational | T002–T018 | 17 |
| Phase 3: US1 — Lab Orders (P1) 🎯 | T019–T025 | 7 |
| Phase 4: US2 — NEWS2 (P1) | T026–T030 | 5 |
| Phase 5: US3 — Drug Interactions (P2) | T031–T037 | 7 |
| Phase 6: US4 — Alerts Feed (P2) | T038–T039 | 2 |
| Phase 7: US5 — Risk Dashboard (P2) | T040–T044 | 5 |
| Phase 8: Polish & RBAC | T045–T052 | 8 |
| **TOTAL** | **T001–T052** | **52** |

### Parallel Opportunities
- Phase 2: 3 parallel blocks (enums, entities, repositories) + 2 parallel test writes
- Phase 3: 5 parallel tasks (tests + 3 DTO/mapper tasks)
- Phase 4: 3 parallel tasks (2 tests + DTOs)
- Phase 5: 4 parallel tasks (3 tests + DTOs)
- Phase 8: 8 parallel tasks

### Independent Test Criteria per Story
| Story | How to Test Independently |
|---|---|
| US1 | `LabOrderIT`: POST order → POST CRITICAL result → verify CRITICAL alert in DB |
| US2 | `News2IT`: record HIGH vitals → GET /news2 → verify score≥7 riskLevel=HIGH + alert |
| US3 | `DrugInteractionIT`: seed Aspirin med + Penicillin allergy → check Warfarin → verify MAJOR interaction + alert |
| US4 | `ClinicalAlertIT`: seed alert → GET → acknowledge → dismiss; verify doctor scoping |
| US5 | `PatientRiskDashboardIT`: seed patients with different risk profiles → verify sort order |

### Suggested MVP Scope
**US1 + US2** (both P1): Lab result auto-alerts + NEWS2 early warning. Together they cover the two highest-impact patient safety use cases. Deliverable after Phase 4 completes.
