# Tasks: Patient Module (001-patient-module)

**Input**: Design documents from `/specs/001-patient-module/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/openapi.yaml ✅, quickstart.md ✅
**Branch**: `001-patient-module`
**Date**: 2026-02-19

**Tests**: TDD is **MANDATORY** per Constitution Principle III — all test tasks MUST be written first and FAIL before implementation begins.

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no blocking dependencies)
- **[Story]**: US1=Register, US2=Search, US3=Profile, US4=Update, US5=Status

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project skeleton — Docker Compose, backend Maven project, frontend Vite project, Nginx, scripts.

**No dependencies — all tasks can begin immediately.**

- [ ] T001 Create docker-compose.yml at repo root with services: db (postgres:15), backend, frontend, reverse-proxy (nginx:1.25-alpine), pgadmin (optional/dev); named volumes (db-data, db-backups, db-wal-archive); hospital-net bridge network; resource limits per spec; HEALTHCHECK + depends_on condition:service_healthy
- [ ] T002 [P] Create .env.example documenting all env vars: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, SERVER_PORT, JWT_SECRET, LOG_LEVEL, DB_POOL_MIN, DB_POOL_MAX, REACT_APP_API_BASE_URL, NGINX_SERVER_NAME, SSL_CERT_PATH, SSL_KEY_PATH
- [ ] T003 [P] Create nginx/nginx.conf: HTTPS termination (TLS 1.2/1.3), HTTP→HTTPS redirect (port 80→443), rate limiting 200 req/IP/min with 429 + Retry-After, proxy_pass /api/v1/ → backend:8080, / → frontend:3000
- [ ] T004 [P] Create backend/pom.xml: Spring Boot 3.2.x parent; dependencies for Web, Data JPA, Security, Validation, Flyway (PostgreSQL), Lombok, MapStruct + annotation processor, SpringDoc OpenAPI 2.x, Resilience4j Spring Boot Starter, Actuator, Micrometer Prometheus registry, logstash-logback-encoder, JUnit 5, Mockito, Testcontainers (PostgreSQL), failsafe plugin profile for ITs
- [ ] T005 [P] Create backend/src/main/resources/application.yml: server.port, datasource (HikariCP pool settings min=5 max=20 timeout=3000), JPA/Hibernate ddl-auto=validate, Flyway enabled, actuator endpoints exposure (health/metrics/info), logging levels, management.health liveness+readiness groups
- [ ] T006 [P] Create backend/src/main/resources/application-docker.yml: docker-profile datasource overrides using ${DB_HOST}/${DB_NAME}/${DB_USER}/${DB_PASSWORD} env vars, JSON logging appender profile switch
- [ ] T007 [P] Create backend/src/main/resources/logback-spring.xml: JSON appender (logstash-logback-encoder) for docker spring profile with MDC fields traceId/userId/operation/patientId; human-readable console appender for default/local; PHI restriction enforced by never adding name/DOB/phone/email to MDC
- [ ] T008 [P] Create backend/Dockerfile: multi-stage — Stage 1 maven:3.9-eclipse-temurin-17 builds JAR (mvn package -DskipTests); Stage 2 eclipse-temurin:17-jre-alpine runs JAR; HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health/readiness every 10s timeout 5s retries 3; STOPSIGNAL SIGTERM; expose 8080
- [ ] T009 [P] Create frontend/package.json: React 18.x, react-dom, react-router-dom v6, @tanstack/react-query v5, react-hook-form, zod, @hookform/resolvers, axios; devDependencies: vite, @vitejs/plugin-react, tailwindcss, postcss, autoprefixer, vitest, @vitest/ui, jsdom, @testing-library/react, @testing-library/user-event, @testing-library/jest-dom
- [ ] T010 [P] Create frontend/vite.config.js: React plugin, resolve aliases, test config (Vitest globals:true, environment:jsdom, setupFiles for @testing-library/jest-dom), server proxy /api → http://localhost:8080 for local dev
- [ ] T011 [P] Create frontend/Dockerfile: multi-stage — Stage 1 node:20-alpine builds Vite output (npm ci && npm run build); Stage 2 nginx:1.25-alpine serves dist/ as static files; expose 3000
- [ ] T012 [P] Create frontend/tailwind.config.js with content glob covering src/**/*.{js,jsx} and frontend/src/index.css with Tailwind @tailwind directives
- [ ] T013 [P] Create scripts/generate-certs.sh: openssl req -x509 -nodes to create nginx/ssl/cert.pem and nginx/ssl/key.pem for CN=localhost; add nginx/ssl/ to .gitignore
- [ ] T014 [P] Create scripts/backup.sh: pg_dump via docker compose exec db → backups/hospital_patients_$(date +%Y-%m-%d_%H-%M).sql; creates backups/ dir if absent
- [ ] T015 [P] Create scripts/restore.sh: accepts .sql file path argument, restores via psql docker compose exec db; validates file exists before running
- [ ] T016 [P] Create scripts/healthcheck.sh: curl checks for /actuator/health/liveness (UP), /actuator/health/readiness (UP), Nginx 443 responding; prints ✅/❌ per service; exits non-zero if any check fails

**Checkpoint**: Full project skeleton committed. Docker Compose defined with all services. Builds fail until code is added — expected.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, JPA entities, repositories, DTOs, validators, mapper, security infrastructure, exception handling, AuditService, configs, frontend routing — everything ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Database Migrations (Flyway — must run in version order)

- [ ] T017 Create backend/src/main/resources/db/migration/V1__create_patients.sql: CREATE TABLE patients with all columns per spec — patient_id VARCHAR(12) PRIMARY KEY, first_name/last_name VARCHAR(50) NOT NULL, date_of_birth DATE NOT NULL, gender VARCHAR(10) NOT NULL CHECK IN ('MALE','FEMALE','OTHER'), blood_group VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN', phone VARCHAR(20) NOT NULL, email/address/city/state/zip_code NULLABLE, emergency_contact_* NULLABLE, known_allergies/chronic_conditions TEXT NULLABLE, status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK IN ('ACTIVE','INACTIVE'), created_at/updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), created_by/updated_by VARCHAR(100) NOT NULL, version INTEGER NOT NULL DEFAULT 0
- [ ] T018 Create backend/src/main/resources/db/migration/V2__create_patient_id_sequences.sql: CREATE TABLE patient_id_sequences (year INTEGER PRIMARY KEY, last_sequence INTEGER NOT NULL DEFAULT 0)
- [ ] T019 Create backend/src/main/resources/db/migration/V3__create_patient_audit_log.sql: CREATE TABLE patient_audit_log (id BIGSERIAL PRIMARY KEY, timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(), operation VARCHAR(20) NOT NULL CHECK IN ('REGISTER','UPDATE','DEACTIVATE','ACTIVATE'), patient_id VARCHAR(12) NOT NULL, performed_by VARCHAR(100) NOT NULL, changed_fields TEXT[] NULLABLE) — no FK to patients (HIPAA retention independence)
- [ ] T020 Create backend/src/main/resources/db/migration/V4__create_indexes.sql: all 12 indexes — B-Tree on patients (status, last_name, first_name, phone, email, blood_group, gender, created_at DESC) + GIN tsvector index on (to_tsvector('english', first_name || ' ' || last_name)) + B-Tree on patient_audit_log (patient_id, performed_by, timestamp DESC)

### Backend: Enums & Entities

- [ ] T021 [P] Create backend/src/main/java/com/ainexus/hospital/patient/entity/Gender.java (MALE, FEMALE, OTHER), BloodGroup.java (A_POS…UNKNOWN with @JsonValue annotations mapping to display values A+/A-/B+/B-/AB+/AB-/O+/O-/UNKNOWN), PatientStatus.java (ACTIVE, INACTIVE) in entity package
- [ ] T022 [P] Create backend/src/main/java/com/ainexus/hospital/patient/entity/Patient.java: @Entity @Table(name="patients") with all fields, @Id patientId, @Version version, @Enumerated(STRING) for gender/bloodGroup/status, @Column(updatable=false) createdAt, Lombok @Getter @Setter @NoArgsConstructor @Builder; no @OneToMany to PatientAuditLog
- [ ] T023 [P] Create backend/src/main/java/com/ainexus/hospital/patient/entity/PatientIdSequence.java: @Entity @Table(name="patient_id_sequences"), @Id year Integer, lastSequence Integer; Lombok @Getter @Setter @NoArgsConstructor @AllArgsConstructor
- [ ] T024 [P] Create backend/src/main/java/com/ainexus/hospital/patient/entity/PatientAuditLog.java: @Entity @Table(name="patient_audit_log"), @Id @GeneratedValue(strategy=IDENTITY) Long id, OffsetDateTime timestamp, String operation, String patientId, String performedBy, String[] changedFields; Lombok @Builder @NoArgsConstructor @AllArgsConstructor

### Backend: Repositories

- [ ] T025 [P] Create backend/src/main/java/com/ainexus/hospital/patient/repository/PatientRepository.java: JpaRepository<Patient, String>; @Query methods for: searchPatients (LOWER ILIKE on firstName/lastName/phone/email; patient_id LIKE prefix; AND filters for status/gender/bloodGroup; Pageable; default sort createdAt DESC); findFirstByPhone(String phone); count query counterpart for pagination
- [ ] T026 [P] Create backend/src/main/java/com/ainexus/hospital/patient/repository/PatientIdSequenceRepository.java: JpaRepository<PatientIdSequence, Integer>; @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PatientIdSequence> findByYear(Integer year)
- [ ] T027 [P] Create backend/src/main/java/com/ainexus/hospital/patient/repository/PatientAuditLogRepository.java: JpaRepository<PatientAuditLog, Long>; List<PatientAuditLog> findByPatientId(String patientId); List<PatientAuditLog> findByPerformedBy(String performedBy); NO delete/update methods exposed — append-only

### Backend: Request & Response DTOs

- [ ] T028 [P] Create backend/src/main/java/com/ainexus/hospital/patient/dto/request/PatientRegistrationRequest.java: Java record with all 16 fields; @NotBlank @Size(max=50) firstName/lastName; @NotNull @ValidDateOfBirth LocalDate dateOfBirth; @NotNull Gender gender; @NotNull @PhoneNumber String phone; @Email @Size(max=100) String email; size limits on all optional fields; BloodGroup bloodGroup (defaults UNKNOWN if null); @EmergencyContactPairing at class level
- [ ] T029 [P] Create backend/src/main/java/com/ainexus/hospital/patient/dto/request/PatientUpdateRequest.java: identical to PatientRegistrationRequest — same fields, same validators; no patientId/createdAt/createdBy/version fields
- [ ] T030 [P] Create backend/src/main/java/com/ainexus/hospital/patient/dto/response/PatientResponse.java, PatientSummaryResponse.java, PagedResponse.java (generic record), PatientRegistrationResponse.java (patientId + message), DuplicatePhoneResponse.java (duplicate boolean, patientId, patientName), PatientStatusChangeRequest.java (action StatusAction enum), PatientStatusChangeResponse.java (patientId, status, message) — all as Java records in dto/response and dto/request packages

### Backend: Custom Validators

- [ ] T031 [P] Create backend/src/main/java/com/ainexus/hospital/patient/validation/PhoneNumber.java (@interface constraint annotation with message) and PhoneNumberValidator.java (ConstraintValidator<PhoneNumber, String>): validates regex `^(\+1-\d{3}-\d{3}-\d{4}|\(\d{3}\) \d{3}-\d{4}|\d{3}-\d{3}-\d{4})$`; returns true for null (use @NotNull separately for mandatory fields)
- [ ] T032 [P] Create backend/src/main/java/com/ainexus/hospital/patient/validation/ValidDateOfBirth.java annotation and ValidDateOfBirthValidator.java: rejects today's date ("Date of birth cannot be today."), future dates ("Date of birth cannot be a future date."), dates > 150 years in past ("Date of birth must be within the last 150 years.")
- [ ] T033 [P] Create backend/src/main/java/com/ainexus/hospital/patient/validation/EmergencyContactPairing.java annotation and EmergencyContactPairingValidator.java: class-level ConstraintValidator applied to PatientRegistrationRequest/PatientUpdateRequest; validates emergencyContactName and emergencyContactPhone are both non-blank or both blank/null; separate error messages per field (name without phone, phone without name)

### Backend: MapStruct Mapper

- [ ] T034 [P] Create backend/src/main/java/com/ainexus/hospital/patient/mapper/PatientMapper.java: @Mapper(componentModel="spring") interface; PatientSummaryResponse toSummary(Patient patient); PatientResponse toResponse(Patient patient); Patient toEntity(PatientRegistrationRequest request); void updateEntity(PatientUpdateRequest request, @MappingTarget Patient patient); default int toAge(LocalDate dateOfBirth) { return Period.between(dateOfBirth, LocalDate.now()).getYears(); } — age is never stored, always computed

### Backend: Exception Handling

- [ ] T035 [P] Create backend/src/main/java/com/ainexus/hospital/patient/exception/PatientNotFoundException.java (extends RuntimeException), ConflictException.java (extends RuntimeException — used for HTTP 409: optimistic lock conflicts and invalid status transitions), ForbiddenException.java (HTTP 403)
- [ ] T036 [P] Create backend/src/main/java/com/ainexus/hospital/patient/exception/GlobalExceptionHandler.java: @RestControllerAdvice; handle MethodArgumentNotValidException → 400 with fieldErrors[]; PatientNotFoundException → 404; ConflictException → 409; ForbiddenException → 403; ObjectOptimisticLockingFailureException → 409 "Patient record was modified by another user. Please reload and try again."; generic Exception → 500; all responses include timestamp (UTC), status, error, message, traceId (from MDC), fieldErrors (nullable)

### Backend: Security

- [ ] T037 [P] Create backend/src/main/java/com/ainexus/hospital/patient/security/AuthContext.java: ThreadLocal<AuthContext> holder with userId, username, role; static get/set/clear methods; used across the request lifecycle
- [ ] T038 [P] Create backend/src/main/java/com/ainexus/hospital/patient/security/RoleGuard.java: requireRoles(Set<String> allowedRoles) checks AuthContext.get().getRole() — throws ForbiddenException (403) if not in set; requireAuthenticated() throws UnauthorizedException (401) if AuthContext empty
- [ ] T039 Create backend/src/main/java/com/ainexus/hospital/patient/security/JwtAuthFilter.java: OncePerRequestFilter; extracts Bearer JWT from Authorization header; parses claims (userId, username, role) — for now validate signature with JWT_SECRET; populates AuthContext; annotated with @CircuitBreaker(name="authModule", fallbackMethod) from Resilience4j; fallback method returns 503 "Authentication service unavailable. Please try again shortly."; clears AuthContext in finally block; skips /actuator/health** paths
- [ ] T040 [P] Create backend/src/main/java/com/ainexus/hospital/patient/config/SecurityConfig.java: @Configuration @EnableWebSecurity; disable CSRF (stateless JWT); sessionManagement STATELESS; permit /actuator/health/** and /actuator/health/liveness and /actuator/health/readiness without auth; all other paths require authentication; add JwtAuthFilter before UsernamePasswordAuthenticationFilter
- [ ] T041 [P] Create backend/src/main/java/com/ainexus/hospital/patient/config/HikariConfig.java or configure HikariCP in application.yml: minimumIdle=5, maximumPoolSize=20, connectionTimeout=3000, idleTimeout=600000, maxLifetime=1800000, connectionTestQuery=SELECT 1
- [ ] T042 [P] Create backend/src/main/java/com/ainexus/hospital/patient/config/CircuitBreakerConfig.java: Resilience4j @Bean CircuitBreakerConfig for "authModule" — failureRateThreshold=50, slidingWindowSize=10, minimumNumberOfCalls=5, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=1; register with CircuitBreakerRegistry
- [ ] T043 [P] Create backend/src/main/java/com/ainexus/hospital/patient/config/OpenApiConfig.java: @OpenAPIDefinition with title "Hospital Management System — Patient Module API" version "1.0.0"; @SecurityScheme BearerAuth (JWT); servers: https://localhost/api/v1

### Backend: AuditService & Application Entry Point

- [ ] T044 [P] Create backend/src/main/java/com/ainexus/hospital/patient/audit/AuditService.java: @Service; writeAuditLog(String operation, String patientId, String performedBy, List<String> changedFields): builds PatientAuditLog and calls PatientAuditLogRepository.save(); MUST be called within the same @Transactional scope as the patient write — no separate transaction; changedFields is null for non-UPDATE operations; audit log contains NO PHI beyond patientId
- [ ] T045 [P] Create backend/src/main/java/com/ainexus/hospital/patient/PatientModuleApplication.java: @SpringBootApplication; main() entry point; no additional configuration needed

### Backend: Test Infrastructure

- [ ] T046 [P] Create backend/src/test/java/com/ainexus/hospital/patient/integration/BaseIntegrationTest.java: @SpringBootTest(webEnvironment=RANDOM_PORT), @AutoConfigureTestDatabase(replace=NONE), @Testcontainers; static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15"); @DynamicPropertySource to wire spring.datasource.url/username/password from container; @BeforeEach to clean tables via JdbcTemplate; shared container for all ITs (reuse=true)

### Frontend: Core Infrastructure

- [ ] T047 [P] Create frontend/src/main.jsx: React 18 createRoot, QueryClientProvider with QueryClient (defaultOptions staleTime:30000 gcTime:300000), BrowserRouter wrapping App
- [ ] T048 [P] Create frontend/src/App.jsx: React Router v6 Routes — "/" → PatientListPage, "/patients/new" → PatientRegistrationPage, "/patients/:patientId" → PatientProfilePage (profile view), "/patients/:patientId/edit" → PatientProfilePage with editMode prop
- [ ] T049 [P] Create frontend/src/api/patientApi.js: Axios instance with baseURL from import.meta.env.VITE_API_BASE_URL; request interceptor adds Authorization: Bearer ${token} from sessionStorage; response error interceptor surfaces HTTP errors; base structure only — story-specific functions added per story phase
- [ ] T050 [P] Create frontend/src/components/common/InlineError.jsx (renders field error message in red below field), LoadingSpinner.jsx (centered spinner for loading states), Pagination.jsx (shows "Showing X–Y of Z patients", Previous/Next buttons disabled at boundaries, zero-based page param)

**Checkpoint**: Foundation complete. All entities/repos/DTOs/validators/security/mapper/audit/error-handling in place. Frontend routing and API client established. User story implementation can now begin independently.

---

## Phase 3: User Story 1 — Register a New Patient (Priority: P1) 🎯 MVP

**Goal**: A receptionist or admin fills the registration form and receives a unique Patient ID (e.g., `P2026001`). The system atomically generates IDs under concurrent load.

**Independent Test**: Submit only the 5 mandatory fields (firstName, lastName, dateOfBirth, gender, phone) → HTTP 201 with a Patient ID — with no other story implemented.

### Tests for User Story 1 *(TDD — write first, verify FAIL before implementation)*

- [ ] T051 [P] [US1] Write backend/src/test/java/com/ainexus/hospital/patient/unit/service/PatientIdGeneratorServiceTest.java: first registration of year → P2026001; second → P2026002; year boundary reset (mock year 2027) → P2027001; sequence >999 → P20261000 (4 digits, no padding); concurrent 10-thread test → 10 unique IDs via @RepeatedTest; all tests use Mockito mock of PatientIdSequenceRepository
- [ ] T052 [P] [US1] Write backend/src/test/java/com/ainexus/hospital/patient/unit/validation/PhoneNumberValidatorTest.java: valid +1-555-123-4567 ✅; valid (555) 123-4567 ✅; valid 555-123-4567 ✅; invalid 12345 ❌; invalid 555-12-456 ❌; invalid abc-def-ghij ❌; null → valid (null check is separate @NotNull concern)
- [ ] T053 [P] [US1] Write backend/src/test/java/com/ainexus/hospital/patient/unit/mapper/PatientMapperTest.java: toResponse() age computed for past DOB (1990-03-15 → 35 in 2026-02); toResponse() age for same-day birthday (1990-02-19 → 36 on 2026-02-19); toSummary() maps subset fields; toEntity() sets bloodGroup=UNKNOWN when null; updateEntity() merges changed fields onto existing entity
- [ ] T054 [P] [US1] Write backend/src/test/java/com/ainexus/hospital/patient/unit/service/PatientServiceTest.java (registration section): registerPatient success → PatientRegistrationResponse with patientId; RECEPTIONIST role → allowed; ADMIN role → allowed; DOCTOR role → ForbiddenException; NURSE role → ForbiddenException; unauthenticated → UnauthorizedException; emergency contact name without phone → validation exception; duplicate phone → DuplicatePhoneResponse.duplicate=true with existing patient data
- [ ] T055 [P] [US1] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientRegistrationIT.java: extends BaseIntegrationTest; POST /patients with valid minimal payload → 201 + patientId in body; POST with invalid phone → 400 + fieldErrors; POST with future DOB → 400; POST with today's DOB → 400; POST as DOCTOR token → 403; POST unauthenticated → 401; POST name+phone emergencyContact but no phone → 400; sequential 5 registrations → 5 unique sequential IDs; audit log has REGISTER entry for each
- [ ] T056 [P] [US1] Write frontend/src/test/PatientRegistrationForm.test.jsx: renders 4 labeled sections; mandatory fields marked; blur invalid phone → error below field; blur valid phone → no error; invalid email on blur → error; emergencyContactName without phone on submit → pairing error; DOB blur → age displayed; submit disabled when loading; success shows Patient ID message; DOCTOR role → authorization error shown, form not rendered

### Implementation for User Story 1

- [ ] T057 [US1] Implement backend/src/main/java/com/ainexus/hospital/patient/service/PatientIdGeneratorService.java: @Service @Transactional; generatePatientId(): query PatientIdSequenceRepository.findByYear(currentYear) with PESSIMISTIC_WRITE lock; if absent INSERT (year, lastSequence=1); if present UPDATE lastSequence+1; format "P" + year + LPAD(seq, 3, '0') if seq ≤ 999, else "P" + year + seq (no padding beyond 999)
- [ ] T058 [US1] Implement PatientService.registerPatient() in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: @Transactional; requireRoles(RECEPTIONIST, ADMIN); generate patientId; set status=ACTIVE, createdAt=now(UTC), createdBy=authContext.getUsername(), updatedAt=now(UTC), updatedBy=authContext.getUsername(); save Patient; auditService.writeAuditLog("REGISTER", patientId, performedBy, null); set MDC (traceId, userId, operation=REGISTER_PATIENT, patientId); return PatientRegistrationResponse; also implement checkDuplicatePhone(phone, excludePatientId) — findFirstByPhone, return DuplicatePhoneResponse
- [ ] T059 [US1] Implement backend/src/main/java/com/ainexus/hospital/patient/controller/PatientController.java: @RestController @RequestMapping("/api/v1/patients"); POST / → registerPatient (@Valid @RequestBody, all roles RECEPTIONIST/ADMIN) returns 201; GET /check-phone → checkDuplicatePhone (query param phone, optional excludePatientId); generate traceId UUID, set MDC on entry, clear in finally; @Validated on controller
- [ ] T060 [P] [US1] Add to frontend/src/api/patientApi.js: registerPatient(data) → POST /patients; checkDuplicatePhone(phone, excludePatientId) → GET /patients/check-phone
- [ ] T061 [P] [US1] Implement frontend/src/hooks/usePatients.js: useMutation({ mutationFn: registerPatient, onSuccess: () => queryClient.invalidateQueries(['patients']) }); export useRegisterPatient hook; placeholder useSearchPatients(params) for US2
- [ ] T062 [US1] Create frontend/src/components/patient/PatientRegistrationForm.jsx: React Hook Form useForm with Zod resolver (schema mirroring all backend validation rules), mode:'onBlur'; 4 sections (Personal Demographics, Contact Information, Emergency Contact, Medical Background); mandatory field asterisk markers; age display on DOB blur (Period calculation in JS); on phone blur: call checkDuplicatePhone, show non-blocking ⚠ warning with existing patient ID/name; InlineError below each field; submit button disabled isSubmitting; calls useRegisterPatient mutation on submit
- [ ] T063 [US1] Create frontend/src/pages/PatientRegistrationPage.jsx: reads auth context role; if DOCTOR or NURSE → render "You do not have permission to register patients." (form NOT rendered); else render PatientRegistrationForm; on registration success show banner "Patient registered successfully. Patient ID: P2026001" and navigate to /patients/:patientId

**Checkpoint**: US1 fully functional. `cd backend && mvn test` passes PatientIdGeneratorServiceTest + PhoneNumberValidatorTest + PatientMapperTest + PatientServiceTest. `mvn verify -Pfailsafe` passes PatientRegistrationIT. Frontend registration form works end-to-end.

---

## Phase 4: User Story 2 — Search and Filter Patients (Priority: P2)

**Goal**: Any authenticated staff member searches the patient list by name, ID, phone, or email, filters by status/gender/blood group, paginates results, and navigates to a profile preserving list state.

**Independent Test**: Type a partial last name → debounced case-insensitive results in ≤ 2 seconds. Paginate to page 2. Navigate to profile. Back to List → page 2 restored.

### Tests for User Story 2 *(TDD — write first, verify FAIL before implementation)*

- [ ] T064 [P] [US2] Write PatientServiceTest.java (search section) in backend/src/test/java/com/ainexus/hospital/patient/unit/service/PatientServiceTest.java: default search → ACTIVE patients sorted createdAt DESC; query "smith" → case-insensitive lastName match; query "P2026" → patient_id prefix match; status=ALL → includes INACTIVE; gender=FEMALE filter; bloodGroup=A_POS filter; combined AND query+status+gender → all conditions required; empty results → empty page; all 4 roles (RECEPTIONIST, DOCTOR, NURSE, ADMIN) → allowed; page 2 size 20 returns correct slice
- [ ] T065 [P] [US2] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientSearchIT.java: extends BaseIntegrationTest; seed 25 patients via direct repository save; GET /patients → 200 page 0 size 20 first=true last=false; GET /patients?page=1 → remaining 5 patients last=true; GET /patients?query=smith → only matching; GET /patients?status=ALL → includes INACTIVE count; GET /patients?gender=FEMALE → only females; GET /patients?query=P2026 → prefix match; GET /patients?query=xyz → empty content no error; all 4 roles → 200; unauthenticated → 401
- [ ] T066 [P] [US2] Write frontend/src/test/PatientList.test.jsx: renders table with 6 columns (ID, Name, Age, Gender, Phone, Status); shows "Showing 1–20 of 25 patients"; Previous disabled on page 0, Next enabled; empty results → "No patients found matching your search."; no patients ever → "No patients registered yet." with register prompt (RECEPTIONIST only); typing in SearchBox triggers debounced query after 300ms; pressing Enter triggers immediate; clearing search restores default; clicking row navigates to /patients/:id

### Implementation for User Story 2

- [ ] T067 [US2] Implement PatientRepository search query in backend/src/main/java/com/ainexus/hospital/patient/repository/PatientRepository.java: @Query JPQL/native with: LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR p.phone LIKE CONCAT('%', :query, '%') OR LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')) OR p.patientId LIKE CONCAT(:query, '%'); AND p.status = :status (skip filter if null/ALL); AND p.gender = :gender (skip if null/ALL); AND p.bloodGroup = :bloodGroup (skip if null/ALL); Pageable parameter; countQuery for totalElements
- [ ] T068 [US2] Implement PatientService.searchPatients() in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: all 4 roles allowed; resolve status/gender/bloodGroup filter enums (ALL → null → skip filter); call repository search; map Page<Patient> → PagedResponse<PatientSummaryResponse>; set MDC operation=SEARCH_PATIENTS
- [ ] T069 [US2] Implement GET /patients in backend/src/main/java/com/ainexus/hospital/patient/controller/PatientController.java: @RequestParams (query, status default ACTIVE, gender default ALL, bloodGroup default ALL, page default 0, size default 20, sort default "createdAt,desc"); all roles; return 200 PagedResponse<PatientSummaryResponse>
- [ ] T070 [P] [US2] Add to frontend/src/api/patientApi.js: searchPatients({query, status, gender, bloodGroup, page, size}) → GET /patients with query params; returns PagedResponse<PatientSummaryResponse>
- [ ] T071 [P] [US2] Implement frontend/src/hooks/usePatients.js: useSearchPatients(params) → useQuery(['patients', params], () => searchPatients(params), {staleTime: 30_000, gcTime: 300_000, keepPreviousData: true})
- [ ] T072 [P] [US2] Create frontend/src/hooks/useListState.js: useState + useEffect persisting {query:'', status:'ACTIVE', gender:'ALL', bloodGroup:'ALL', page:0} to sessionStorage under key 'patientListState'; provides state + setters + reset(); restores on mount from sessionStorage
- [ ] T073 [P] [US2] Create frontend/src/components/common/SearchBox.jsx: controlled input with 300ms debounce on change, immediate trigger on Enter keydown, clear button that calls onClear prop; aria-label="Search patients"
- [ ] T074 [P] [US2] Create frontend/src/components/common/FilterBar.jsx: Status select (Active=ACTIVE/Inactive=INACTIVE/All=ALL default Active), Gender select (All/Male/Female/Other), Blood Group select (All/A+/A-/B+/B-/AB+/AB-/O+/O-/Unknown); each onChange calls prop callback; accessible labels
- [ ] T075 [US2] Create frontend/src/components/patient/PatientListRow.jsx: <tr> with cells for patientId, `${firstName} ${lastName}`, age, gender, phone, PatientStatusBadge; cursor-pointer; onClick → navigate('/patients/${patientId}') — useListState is preserved automatically in sessionStorage
- [ ] T076 [US2] Create frontend/src/components/patient/PatientList.jsx: renders <table> with header row + PatientListRow per patient in content[]; empty state conditional (no query+filters → "No patients registered yet." + Register button for RECEPTIONIST/ADMIN; with query/filters → "No patients found matching your search."); Pagination component below table
- [ ] T077 [US2] Create frontend/src/pages/PatientListPage.jsx: uses useListState + useSearchPatients(listState); renders SearchBox (bound to query), FilterBar (bound to status/gender/bloodGroup), PatientList (passes patients + pagination data); loading state via LoadingSpinner; "Register New Patient" button (RECEPTIONIST/ADMIN only) → navigate('/patients/new')

**Checkpoint**: US2 fully functional. Search, filter, pagination, and state persistence all work independently. mvn verify -Pfailsafe passes PatientSearchIT.

---

## Phase 5: User Story 3 — View Patient Profile (Priority: P3)

**Goal**: Any authenticated staff member views the complete patient record in 4 sections with status badge, audit metadata, and role-appropriate action buttons.

**Independent Test**: GET /patients/P2026001 → 200 with all fields. Frontend renders all 4 sections, correct status badge color+text, and shows/hides Edit/Deactivate buttons by role.

### Tests for User Story 3 *(TDD — write first, verify FAIL before implementation)*

- [ ] T078 [P] [US3] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientProfileIT.java: extends BaseIntegrationTest; GET /patients/P2026001 → 200 with all fields (patientId, firstName, lastName, dateOfBirth, age, gender, bloodGroup, phone, email, emergencyContact*, allergies, conditions, status=ACTIVE, createdAt, createdBy, updatedAt, updatedBy, version); GET /patients/NOTEXIST → 404 with error body; RECEPTIONIST → 200; DOCTOR → 200; NURSE → 200; ADMIN → 200; unauthenticated → 401; optional null fields returned as null (not absent); age computed correctly
- [ ] T079 [P] [US3] Write frontend/src/test/PatientProfile.test.jsx: renders 4 labeled sections; ACTIVE patient → green "Active" badge; INACTIVE patient → red "Inactive" badge; RECEPTIONIST → "Edit Patient" button visible; DOCTOR → no "Edit Patient" button (not rendered, not hidden); ADMIN + ACTIVE → "Deactivate Patient" visible, no "Activate Patient"; ADMIN + INACTIVE → "Activate Patient" visible, no "Deactivate Patient"; no emergency contact → "No emergency contact on file."; null optional field → "Not provided" / "None recorded"; audit section shows registeredAt and createdBy; "Back to List" link present

### Implementation for User Story 3

- [ ] T080 [US3] Implement PatientService.getPatient(String patientId, AuthContext auth) in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: all 4 roles allowed; PatientRepository.findById(patientId).orElseThrow(PatientNotFoundException); mapper.toResponse(patient); set MDC operation=GET_PATIENT, patientId; return PatientResponse
- [ ] T081 [US3] Implement GET /patients/{patientId} in backend/src/main/java/com/ainexus/hospital/patient/controller/PatientController.java: @PathVariable patientId; all roles; return 200 PatientResponse; 404 handled by GlobalExceptionHandler
- [ ] T082 [P] [US3] Add to frontend/src/api/patientApi.js: getPatient(patientId) → GET /patients/:patientId returning PatientResponse
- [ ] T083 [P] [US3] Create frontend/src/hooks/usePatient.js: useQuery(['patient', patientId], () => getPatient(patientId), {staleTime: 10_000, enabled: !!patientId})
- [ ] T084 [P] [US3] Create frontend/src/components/patient/PatientStatusBadge.jsx: renders green badge "Active" for ACTIVE, red badge "Inactive" for INACTIVE; uses BOTH color AND text label (WCAG AA accessible); Tailwind bg-green-100 text-green-800 / bg-red-100 text-red-800 rounded pill
- [ ] T085 [US3] Create frontend/src/components/patient/PatientProfile.jsx: renders 4 sections — Personal Demographics (patientId, fullName, DOB, age, gender, bloodGroup), Contact Information (phone, email or "Not provided", address, city, state, zipCode), Emergency Contact (name+phone+relationship or "No emergency contact on file."), Medical Background (knownAllergies or "None recorded", chronicConditions or "None recorded"); PatientStatusBadge prominently shown; audit section "Registered [date] by [user] | Last Updated [date] by [user]" ("Never updated" if updatedAt=createdAt); conditionally renders Edit Patient button (RECEPTIONIST/ADMIN), Deactivate Patient button (ADMIN + ACTIVE), Activate Patient button (ADMIN + INACTIVE); "Back to List" → navigate('/') preserving useListState
- [ ] T086 [US3] Create frontend/src/pages/PatientProfilePage.jsx: reads :patientId route param; uses usePatient(patientId); loading → LoadingSpinner; 404 → "Patient not found." with Back to List link; error → generic error message; success → render PatientProfile; if editMode prop → render PatientEditForm (US4)

**Checkpoint**: US3 fully functional. All roles can view any patient profile. Correct action buttons rendered by role. mvn verify -Pfailsafe passes PatientProfileIT.

---

## Phase 6: User Story 4 — Update Patient Information (Priority: P4)

**Goal**: A receptionist or admin edits and saves patient demographics. Optimistic locking detects concurrent conflicts with a clear user message.

**Independent Test**: PUT /patients/P2026001 with correct If-Match version header → 200 updated PatientResponse; audit log UPDATE entry with changedFields; stale version → 409 with user-friendly message.

### Tests for User Story 4 *(TDD — write first, verify FAIL before implementation)*

- [ ] T087 [P] [US4] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientUpdateIT.java: extends BaseIntegrationTest; PUT valid payload + correct version → 200 all fields updated; updatedAt changed; updatedBy = authenticated user; audit log has UPDATE operation with changedFields list; PUT with stale version → 409 "Patient record was modified by another user. Please reload and try again."; PUT invalid phone → 400; PUT DOCTOR → 403; PUT unauthenticated → 401; PUT clear optional field (email=null) → saved as null; PUT identical data → updatedAt/updatedBy still updated; PUT /patients/NOTEXIST → 404

### Implementation for User Story 4

- [ ] T088 [US4] Implement PatientService.updatePatient(String patientId, PatientUpdateRequest request, Integer version, AuthContext auth) in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: @Transactional; requireRoles(RECEPTIONIST, ADMIN); fetch patient or 404; compute changedFields list by comparing request fields to current patient values; mapper.updateEntity(request, patient); set updatedAt=now(UTC), updatedBy=auth.getUsername(); catch ObjectOptimisticLockingFailureException → throw ConflictException("Patient record was modified by another user. Please reload and try again."); auditService.writeAuditLog("UPDATE", patientId, performedBy, changedFields); return mapper.toResponse(saved patient)
- [ ] T089 [US4] Implement PUT /patients/{patientId} in backend/src/main/java/com/ainexus/hospital/patient/controller/PatientController.java: @RequestHeader("If-Match") Integer version; @Valid @RequestBody PatientUpdateRequest; RECEPTIONIST/ADMIN only; call patientService.updatePatient(); return 200 PatientResponse
- [ ] T090 [P] [US4] Add to frontend/src/api/patientApi.js: updatePatient(patientId, data, version) → PUT /patients/:patientId with If-Match: version header in request headers; returns PatientResponse
- [ ] T091 [US4] Create frontend/src/components/patient/PatientEditForm.jsx: React Hook Form pre-populated with current patient data (all fields); patientId and registrationDate shown as read-only text (visually distinct — gray background, no border); same Zod schema as registration (identical validation rules); mode:'onBlur'; same emergency contact pairing, duplicate phone check on blur; Cancel → navigate back to profile with no write; Submit → updatePatient mutation; on 409 → show "Patient record was modified by another user. Please reload and try again." inline error; on success → show "Patient updated successfully." banner and navigate to profile
- [ ] T092 [US4] Update frontend/src/pages/PatientProfilePage.jsx: when on edit route (/patients/:patientId/edit) or editMode active → render PatientEditForm with patient data and version; on save success: invalidate ['patient', patientId] query, navigate to /patients/:patientId, show success banner; on cancel: navigate to /patients/:patientId

**Checkpoint**: US4 fully functional. Updates validated, audited, and optimistic conflicts handled. mvn verify -Pfailsafe passes PatientUpdateIT.

---

## Phase 7: User Story 5 — Manage Patient Status (Priority: P5)

**Goal**: An admin deactivates (with modal confirmation) or activates (no confirmation) a patient. Soft delete only — hard deletes are FORBIDDEN.

**Independent Test**: PATCH /patients/P2026001/status {action:"DEACTIVATE"} → 200 INACTIVE; audit DEACTIVATE entry. PATCH {action:"ACTIVATE"} → 200 ACTIVE.

### Tests for User Story 5 *(TDD — write first, verify FAIL before implementation)*

- [ ] T093 [P] [US5] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientStatusIT.java: extends BaseIntegrationTest; PATCH ACTIVE patient with action=DEACTIVATE → 200 status=INACTIVE; audit log has DEACTIVATE entry; PATCH INACTIVE patient with action=ACTIVATE → 200 status=ACTIVE; audit log has ACTIVATE entry; PATCH already INACTIVE with action=DEACTIVATE → 409; PATCH already ACTIVE with action=ACTIVATE → 409; RECEPTIONIST → 403; DOCTOR → 403; NURSE → 403; ADMIN → allowed; unauthenticated → 401; PATCH /patients/NOTEXIST/status → 404; response body has patientId, status, message

### Implementation for User Story 5

- [ ] T094 [US5] Implement PatientService.changePatientStatus(String patientId, StatusAction action, AuthContext auth) in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: @Transactional; requireRoles(ADMIN); fetch patient or 404; validate transition: DEACTIVATE on INACTIVE → 409 "Patient is already inactive."; ACTIVATE on ACTIVE → 409 "Patient is already active."; update patient.setStatus(); set updatedAt/updatedBy; save; auditService.writeAuditLog(action.name(), patientId, auth.getUsername(), null); return PatientStatusChangeResponse(patientId, newStatus, "Patient [fullName] has been [activated/deactivated].")
- [ ] T095 [US5] Implement PATCH /patients/{patientId}/status in backend/src/main/java/com/ainexus/hospital/patient/controller/PatientController.java: @Valid @RequestBody PatientStatusChangeRequest; ADMIN only; return 200 PatientStatusChangeResponse
- [ ] T096 [P] [US5] Add to frontend/src/api/patientApi.js: changePatientStatus(patientId, action) → PATCH /patients/:patientId/status {action}; on success invalidate ['patient', patientId] and ['patients'] queries via queryClient
- [ ] T097 [US5] Create frontend/src/components/patient/DeactivateConfirmModal.jsx: modal overlay with title "Deactivate Patient"; body "Are you sure you want to deactivate [firstName] [lastName] (Patient ID: [patientId])? This patient will no longer appear in active searches."; "Confirm Deactivation" button (red Tailwind bg-red-600) → calls changePatientStatus(DEACTIVATE) mutation; "Cancel" button → closes modal, no API call; isOpen/onClose/patient props; blocks background interaction; focus trap for accessibility
- [ ] T098 [US5] Update frontend/src/components/patient/PatientProfile.jsx: ADMIN + ACTIVE patient → "Deactivate Patient" button opens DeactivateConfirmModal; on deactivation success: close modal, show "Patient has been deactivated." success message, React Query cache invalidation auto-refetches profile with new INACTIVE status and red badge; ADMIN + INACTIVE patient → "Activate Patient" button calls changePatientStatus(ACTIVATE) immediately (no dialog); on activation success: show "Patient has been activated." and profile refreshes with ACTIVE status; on 409 from optimistic lock: show "Patient record was modified by another user. Please reload and try again."

**Checkpoint**: US5 fully functional. Status management is ADMIN-only, soft-delete only, full audit trail. mvn verify -Pfailsafe passes PatientStatusIT.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: RBAC integration coverage, observability metrics, responsive/accessible UI, production hardening, quickstart validation.

- [ ] T099 [P] Write backend/src/test/java/com/ainexus/hospital/patient/integration/PatientRbacIT.java: extends BaseIntegrationTest; full role matrix — POST /patients (RECEPTIONIST ✅ ADMIN ✅ DOCTOR 403 NURSE 403 unauth 401); GET /patients (all 4 roles ✅ unauth 401); GET /patients/{id} (all 4 roles ✅ unauth 401); GET /patients/check-phone (RECEPTIONIST ✅ ADMIN ✅ DOCTOR 403 NURSE 403); PUT /patients/{id} (RECEPTIONIST ✅ ADMIN ✅ DOCTOR 403 NURSE 403 unauth 401); PATCH /patients/{id}/status (ADMIN ✅ RECEPTIONIST 403 DOCTOR 403 NURSE 403 unauth 401); verifies 403 response body format matches ErrorResponse schema
- [ ] T100 [P] Add Micrometer @Counted instrumentation to PatientService methods in backend/src/main/java/com/ainexus/hospital/patient/service/PatientService.java: patient.registrations.total on registerPatient(); patient.searches.total on searchPatients(); patient.updates.total on updatePatient(); patient.status_changes.total on changePatientStatus(); verify /actuator/metrics exposes these + db.pool.active/idle/pending from HikariCP; verify /actuator/health returns UP with liveness and readiness sub-checks
- [ ] T101 [P] Add postgresql.conf tuning file at nginx/postgresql.conf (or backend/src/main/resources/postgresql-docker.conf) with settings: max_connections=100, shared_buffers=256MB, effective_cache_size=768MB, work_mem=4MB, maintenance_work_mem=64MB, wal_buffers=16MB, checkpoint_completion_target=0.9, random_page_cost=1.1, effective_io_concurrency=200, autovacuum=on, log_min_duration_statement=1000, timezone=UTC; mount into db service in docker-compose.yml via volume
- [ ] T102 [P] Apply Tailwind responsive classes to all pages in frontend/src/pages/ and frontend/src/components/patient/: PatientListPage (responsive table or card layout on mobile), PatientRegistrationPage (single column on mobile, 2-col on desktop), PatientProfilePage (stacked sections on mobile); verify form completable on tablet (768px); status badge uses color AND text everywhere
- [ ] T103 [P] Add ARIA attributes and keyboard navigation to frontend components: PatientRegistrationForm (aria-required, aria-describedby for errors, aria-live for duplicate warning), PatientList table (role=table, aria-label, tabIndex on rows), DeactivateConfirmModal (role=dialog, aria-modal, focus trap on open), FilterBar selects (aria-label per dropdown), SearchBox (aria-label, aria-live for result count); WCAG 2.1 AA target
- [ ] T104 [P] Configure graceful shutdown in backend/src/main/resources/application.yml: server.shutdown: graceful, spring.lifecycle.timeout-per-shutdown-phase: 30s; set stop_grace_period: 35s in docker-compose.yml backend service; verify SIGTERM handling drains in-flight requests
- [ ] T105 Run end-to-end quickstart.md validation: bash scripts/generate-certs.sh; docker compose up --build; verify https://localhost (UI loads); verify https://localhost/api/v1/actuator/health → {"status":"UP"}; verify https://localhost/api/v1/actuator/health/readiness → {"status":"UP"}; execute curl registration and search from quickstart.md Step 5; bash scripts/healthcheck.sh → all ✅; docker compose down
- [ ] T106 [P] Commit all implementation files to branch 001-patient-module: group commits by phase (Setup, Foundational, US1, US2, US3, US4, US5, Polish); each commit message follows "feat(patient): [description]" convention; verify git log shows clean history; push to origin/001-patient-module

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately; T002–T016 all parallelizable
- **Foundational (Phase 2)**: Depends on Phase 1 completion — **BLOCKS all user stories**; migrations T017→T018→T019→T020 sequential (Flyway versioning); T021–T050 largely parallelizable
- **User Stories (Phases 3–7)**: All depend on Phase 2 completion; priority order P1→P2→P3→P4→P5 or parallel if team capacity allows
- **Polish (Phase 8)**: Depends on all 5 user stories complete

### User Story Dependencies

| Story | Depends On | Backend | Frontend |
|---|---|---|---|
| US1 Register (P1) | Phase 2 only | POST /patients, GET /check-phone | PatientRegistrationForm |
| US2 Search (P2) | Phase 2 only | GET /patients | PatientListPage, SearchBox, FilterBar |
| US3 Profile (P3) | Phase 2 only | GET /patients/{id} | PatientProfile, PatientStatusBadge |
| US4 Update (P4) | Phase 2 + US3 (PatientProfilePage) | PUT /patients/{id} | PatientEditForm |
| US5 Status (P5) | Phase 2 + US3 (PatientProfile buttons) | PATCH /patients/{id}/status | DeactivateConfirmModal |

### Within Each User Story

1. **Tests FIRST** — write all test tasks, compile, confirm they FAIL
2. **Service layer** — implement business logic
3. **Controller** — implement endpoint(s)
4. **Frontend API** — add function to patientApi.js
5. **Frontend hooks** — add React Query hooks
6. **Frontend components** — build UI
7. **Frontend page** — assemble full page

### Parallel Opportunities (Within Phases)

- **Phase 1**: T002–T016 all [P] — run in parallel
- **Phase 2 migrations**: T017→T018→T019→T020 sequential (version order); T021–T050 all [P]
- **Each US phase**: all [P] test tasks can be written in parallel; backend service→controller sequential; frontend api/hooks/components [P] with each other

---

## Parallel Example: User Story 1

```bash
# Step 1 — Write all US1 tests in parallel (all FAIL initially):
Task: "Write PatientIdGeneratorServiceTest.java [T051]"
Task: "Write PhoneNumberValidatorTest.java [T052]"
Task: "Write PatientMapperTest.java [T053]"
Task: "Write PatientServiceTest.java registration section [T054]"
Task: "Write PatientRegistrationIT.java [T055]"
Task: "Write PatientRegistrationForm.test.jsx [T056]"

# Step 2 — Implement backend (sequential — each depends on previous):
Task: "Implement PatientIdGeneratorService.java [T057]"
Task: "Implement PatientService.registerPatient() [T058]"
Task: "Implement PatientController.registerPatient() [T059]"

# Step 3 — Implement frontend (parallel):
Task: "Add registerPatient to patientApi.js [T060]"
Task: "Add useRegisterPatient to usePatients.js [T061]"
Task: "Create PatientRegistrationForm.jsx [T062]"
Task: "Create PatientRegistrationPage.jsx [T063]"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (**CRITICAL** — blocks everything)
3. Complete Phase 3: User Story 1 (tests first, then implementation)
4. **STOP and VALIDATE**: `cd backend && mvn test` + `mvn verify -Pfailsafe`; open UI, register a patient manually
5. **MVP READY**: Staff can register patients with unique IDs

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → Test independently → **MVP: registration works**
3. US2 → Test independently → **Staff can find patients**
4. US3 → Test independently → **Full profile view**
5. US4 → Test independently → **Demographics can be corrected**
6. US5 → Test independently → **HIPAA soft-delete in place**
7. Polish → Production hardening complete

---

## Summary

| Phase | Tasks | Key Outputs |
|---|---|---|
| Phase 1: Setup | T001–T016 (16 tasks) | docker-compose.yml, pom.xml, package.json, Dockerfiles, scripts |
| Phase 2: Foundational | T017–T050 (34 tasks) | Flyway migrations, entities, repos, DTOs, validators, mapper, security, audit, configs |
| Phase 3: US1 Register | T051–T063 (13 tasks) | PatientIdGeneratorService, POST /patients, PatientRegistrationForm |
| Phase 4: US2 Search | T064–T077 (14 tasks) | GET /patients with filters, PatientListPage, SearchBox, FilterBar, Pagination |
| Phase 5: US3 Profile | T078–T086 (9 tasks) | GET /patients/{id}, PatientProfile, PatientStatusBadge |
| Phase 6: US4 Update | T087–T092 (6 tasks) | PUT /patients/{id}, PatientEditForm, optimistic locking |
| Phase 7: US5 Status | T093–T098 (6 tasks) | PATCH /patients/{id}/status, DeactivateConfirmModal |
| Phase 8: Polish | T099–T106 (8 tasks) | RBAC IT, metrics, responsive UI, WCAG, graceful shutdown, quickstart validation |
| **Total** | **106 tasks** | **Full patient module — register, search, profile, update, status** |

---

## Notes

- **[P]** = different files, no blocking deps — can run in parallel with other [P] tasks in same phase
- **[US#]** = user story label — every implementation task is traceable to its story
- **TDD is mandatory** (Constitution III): every test task must compile and FAIL before its implementation task starts
- **HIPAA**: PHI (name, DOB, phone, email, address) MUST NOT appear in logs — only `patientId` in MDC; AuditService called in same `@Transactional` as patient write
- **No hard deletes**: PatientRepository MUST NOT expose `deleteById()` or `delete()` — omit entirely
- **Optimistic locking**: `@Version` on Patient entity; `ObjectOptimisticLockingFailureException` → HTTP 409 user-friendly message
- **No PHI in audit log**: `changedFields` lists field NAMES only (e.g., "phone", "email") — not their values
- **Commit strategy**: commit after each phase or logical group on branch `001-patient-module`; push to origin after each phase
- **Suggested MVP**: Phases 1 + 2 + 3 (US1 only) delivers a working patient registration system
