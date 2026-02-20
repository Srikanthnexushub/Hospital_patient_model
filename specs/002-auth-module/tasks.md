# Tasks: Auth Module

**Input**: Design documents from `specs/002-auth-module/`
**Branch**: `002-auth-module`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Tests**: TDD is **NON-NEGOTIABLE** per Constitution Principle III. Every user story phase
includes test tasks that must be written and confirmed FAILING before implementation begins.

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5)
- TDD: Test tasks precede their implementation tasks within every story phase

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Environment configuration, Flyway migrations, and foundational exception class.
These tasks have no dependencies on each other and unblock all subsequent phases.

- [ ] T001 Add `app.auth` config block to `backend/src/main/resources/application.yml` — `app.auth.jwt.expiration-hours: ${APP_JWT_EXPIRATION_HOURS:8}`, `app.auth.jwt.refresh-expiration-hours: ${APP_JWT_REFRESH_EXPIRATION_HOURS:24}`, `app.auth.lockout.max-attempts: ${AUTH_LOCKOUT_MAX_ATTEMPTS:5}`, `app.auth.lockout.duration-minutes: ${AUTH_LOCKOUT_DURATION_MINUTES:15}`, `app.auth.admin.username: ${ADMIN_USERNAME:admin}`, `app.auth.admin.initial-password: ${ADMIN_INITIAL_PASSWORD}` (no default — startup fails if absent)
- [ ] T002 [P] Add new auth env vars to `.env.example` — `ADMIN_USERNAME`, `ADMIN_INITIAL_PASSWORD` (marked REQUIRED with no default), `APP_JWT_EXPIRATION_HOURS`, `APP_JWT_REFRESH_EXPIRATION_HOURS`, `AUTH_LOCKOUT_MAX_ATTEMPTS`, `AUTH_LOCKOUT_DURATION_MINUTES`
- [ ] T003 [P] Write Flyway migration `backend/src/main/resources/db/migration/V5__create_hospital_users.sql` — `hospital_users` table (all columns from data-model.md), `staff_id_sequences` table, CHECK constraints on `role` and `status`, all indexes (`uq_hospital_users_username`, `idx_hospital_users_status`, `idx_hospital_users_role`, `idx_hospital_users_locked_until` partial WHERE NOT NULL)
- [ ] T004 [P] Write Flyway migration `backend/src/main/resources/db/migration/V6__create_token_blacklist.sql` — `token_blacklist` table (jti VARCHAR(36) PK, user_id VARCHAR(12), expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ DEFAULT NOW()), indexes `idx_token_blacklist_expires_at` and `idx_token_blacklist_user_id`
- [ ] T005 [P] Write Flyway migration `backend/src/main/resources/db/migration/V7__create_auth_audit_log.sql` — `auth_audit_log` table (id BIGSERIAL PK, timestamp TIMESTAMPTZ DEFAULT NOW(), event_type VARCHAR(30) with CHECK constraint for all 8 event types, actor_user_id VARCHAR(12), target_user_id VARCHAR(12) nullable, outcome VARCHAR(10) CHECK (SUCCESS, FAILURE), ip_address VARCHAR(45) nullable, details TEXT nullable), indexes `idx_auth_audit_actor`, `idx_auth_audit_timestamp DESC`, `idx_auth_audit_event_type`
- [ ] T006 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/exception/AccountLockedException.java` — extends `RuntimeException`, single String message constructor; maps to HTTP 423 in `GlobalExceptionHandler`
- [ ] T007 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/config/PasswordConfig.java` — `@Configuration` class exposing `@Bean PasswordEncoder passwordEncoder()` returning `new BCryptPasswordEncoder(12)`

**Checkpoint**: All migrations written, exception class and PasswordConfig created — foundation ready for entity layer.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entities, repositories, DTOs, mappers, and shared services that ALL user story
phases depend on. Must be 100% complete before any US phase begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Entities

- [ ] T008 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/entity/HospitalUser.java` — `@Entity @Table("hospital_users")` with all fields from data-model.md (`userId`, `username`, `passwordHash`, `role`, `email`, `department`, `status`, `failedAttempts`, `lockedUntil`, `lastLoginAt`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`), `@Version Integer version`, Lombok `@Getter @Setter @NoArgsConstructor @Builder`, `isActive()` and `isLocked()` helper methods
- [ ] T009 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/entity/StaffIdSequence.java` — `@Entity @Table("staff_id_sequences")`, fields `year` (INTEGER PK), `lastSequence` (INTEGER), mirrors `PatientIdSequence.java` structure
- [ ] T010 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/entity/TokenBlacklist.java` — `@Entity @Table("token_blacklist")`, fields `jti` (VARCHAR(36) @Id), `userId` (VARCHAR(12)), `expiresAt` (OffsetDateTime), `revokedAt` (OffsetDateTime DEFAULT NOW())
- [ ] T011 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/entity/AuthAuditLog.java` — `@Entity @Table("auth_audit_log")`, BIGSERIAL `@Id @GeneratedValue`, all columns from data-model.md; immutable (no setters, `@Builder` only)

### Repositories

- [ ] T012 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/repository/HospitalUserRepository.java` — `JpaRepository<HospitalUser, String>`, methods: `Optional<HospitalUser> findByUsernameIgnoreCase(String username)`, `boolean existsByUsernameIgnoreCase(String username)`, `Page<HospitalUser> findByStatusAndRole(String status, String role, Pageable pageable)`, `Page<HospitalUser> findByStatus(String status, Pageable pageable)`, `Page<HospitalUser> findByRole(String role, Pageable pageable)`
- [ ] T013 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/repository/StaffIdSequenceRepository.java` — `JpaRepository<StaffIdSequence, Integer>`, method `Optional<StaffIdSequence> findByYearForUpdate(int year)` with `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT s FROM StaffIdSequence s WHERE s.year = :year")` — mirrors `PatientIdSequenceRepository` exactly
- [ ] T014 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/repository/TokenBlacklistRepository.java` — `JpaRepository<TokenBlacklist, String>`, method `void deleteByExpiresAtBefore(OffsetDateTime cutoff)`
- [ ] T015 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/repository/AuthAuditLogRepository.java` — `JpaRepository<AuthAuditLog, Long>`, method `List<AuthAuditLog> findByActorUserIdOrderByTimestampDesc(String actorUserId)`

### DTOs

- [ ] T016 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/LoginRequest.java` — record with `@NotBlank String username`, `@NotBlank String password`
- [ ] T017 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/TokenResponse.java` — record with `String token`, `String userId`, `String username`, `String role`, `Instant expiresAt`
- [ ] T018 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/UserProfileResponse.java` — record with `String userId`, `String username`, `String role`, `String email`, `String department`, `Instant lastLoginAt`
- [ ] T019 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/CreateUserRequest.java` — record with `@NotBlank @Size(3,50) @Pattern username`, `@NotBlank @Size(min=8) String password` (validated for uppercase/lowercase/digit), `@NotNull String role` (must be valid role), `@Email String email` (optional), `@Size(max=100) String department` (optional)
- [ ] T020 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/UpdateUserRequest.java` — record with all nullable/optional fields: `String email`, `String department`, `String role`
- [ ] T021 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/dto/UserSummaryResponse.java` and `UserDetailResponse.java` — UserSummaryResponse: `userId, username, role, department, status, lastLoginAt`; UserDetailResponse extends with `email, createdAt, createdBy, failedAttempts`

### Mappers & Shared Services

- [ ] T022 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/mapper/StaffMapper.java` — `@Mapper(componentModel = "spring")` with methods: `UserDetailResponse toDetailResponse(HospitalUser user)`, `UserSummaryResponse toSummaryResponse(HospitalUser user)`, `UserProfileResponse toProfileResponse(HospitalUser user)`; explicitly ignore `passwordHash` in all mappings
- [ ] T023 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/service/StaffIdGeneratorService.java` — `@Service @Transactional` mirrors `PatientIdGeneratorService` exactly: `generateStaffId()` calls `generateStaffId(Year.now().getValue())`, inner method uses `StaffIdSequenceRepository.findByYearForUpdate(year)`, increments `lastSequence`, formats as `"U" + year + String.format("%03d", seq)` (expands past 999)
- [ ] T024 [P] Create `backend/src/main/java/com/ainexus/hospital/patient/audit/AuthAuditService.java` — `@Service`, method `writeAuthLog(String eventType, String actorUserId, String targetUserId, String outcome, String ipAddress, String details)` persists `AuthAuditLog` entity; NEVER logs passwords or PHI in any field; `details` param is nullable
- [ ] T025 Create `backend/src/main/java/com/ainexus/hospital/patient/service/AdminSeeder.java` — `@Component implements ApplicationRunner`, `@Value("${app.auth.admin.initial-password}")` (no default — forces startup failure if absent), `@Value("${app.auth.admin.username:admin}")`, checks `hospitalUserRepository.count() == 0` before seeding, calls `passwordEncoder.encode(adminInitialPassword)`, saves `HospitalUser` with role `ADMIN` status `ACTIVE` createdBy `SYSTEM`, logs `INFO` with username only (never logs password)

**Checkpoint**: All entities, repositories, DTOs, mapper, ID generator, audit service, and seeder are in place. Ready to build user story features.

---

## Phase 3: User Story 1 — Staff Login (Priority: P1) 🎯 MVP

**Goal**: Staff members can log in with username/password and receive a JWT token accepted by the Patient Module. Account lockout after 5 failures. DevAuthController is replaced.

**Independent Test**: `POST /api/v1/auth/login` with valid credentials returns a token that successfully authenticates against `GET /api/v1/patients`.

### Tests for US1 — Write First, Confirm FAILING ⚠️

- [ ] T026 [P] [US1] Write `backend/src/test/java/com/ainexus/hospital/patient/service/AuthServiceTest.java` — unit tests with Mockito: (a) successful login returns `TokenResponse` with correct claims, (b) wrong password increments `failedAttempts`, (c) 5th failure sets `lockedUntil` to `NOW() + 15 min`, (d) login while locked throws `AccountLockedException`, (e) deactivated account throws `AuthenticationException`, (f) lockout auto-clears after duration passes; use `SimpleMeterRegistry`, real `RoleGuard`
- [ ] T027 [P] [US1] Write `backend/src/test/java/com/ainexus/hospital/patient/integration/AuthIT.java` — integration test class extending `BaseIntegrationTest`; test cases: (a) valid credentials → 200 + token with correct claims, (b) wrong password → 401 with standard error body, (c) 5 consecutive failures → 6th attempt returns 423, (d) locked account with correct password → 423 not 401, (e) deactivated account → 401, (f) missing username → 400 with fieldErrors, (g) missing password → 400 with fieldErrors; verify returned token is accepted by `GET /api/v1/patients` (Patient Module compatibility)

### Implementation for US1

- [ ] T028 [US1] Implement `AuthService.java` in `backend/src/main/java/com/ainexus/hospital/patient/service/AuthService.java` — `@Service @Transactional`, inject `HospitalUserRepository`, `PasswordEncoder`, `TokenBlacklistRepository`, `AuthAuditService`, `StaffIdGeneratorService`; method `login(LoginRequest req, String ipAddress)`: (1) findByUsernameIgnoreCase or throw generic `AuthenticationException` (do NOT reveal "user not found"), (2) check `isActive()` or throw `AuthenticationException`, (3) check `isLocked()` — if locked throw `AccountLockedException`, (4) `passwordEncoder.matches()` — on failure increment counter, if counter ≥ `lockoutMaxAttempts` set `lockedUntil`, save user, write `LOGIN_FAILURE` audit, throw `AuthenticationException`, (5) on success: reset counter + lockedUntil, set `lastLoginAt`, save, write `LOGIN_SUCCESS` audit, return `issueToken(user)`
- [ ] T029 [US1] Implement `AuthService.issueToken(HospitalUser user)` private method — generates `jti = UUID.randomUUID().toString()`, builds JWT with jjwt `.id(jti).subject(user.getUserId()).claim("username", user.getUsername()).claim("role", user.getRole()).issuedAt(new Date()).expiration(new Date(now + expirationMs)).signWith(Keys.hmacShaKeyFor(secretBytes)).compact()`; reads `app.jwt.secret` and `app.auth.jwt.expiration-hours` via `@Value`; returns `TokenResponse`
- [ ] T030 [US1] Create `backend/src/main/java/com/ainexus/hospital/patient/controller/AuthController.java` — `@RestController @RequestMapping("/api/v1/auth")`; `POST /login` calls `authService.login(req, extractIp(request))`; method is annotated with `@Operation` for OpenAPI; extract client IP from `X-Forwarded-For` header (falls back to `request.getRemoteAddr()`)
- [ ] T031 [US1] Add `AccountLockedException` handler to `backend/src/main/java/com/ainexus/hospital/patient/exception/GlobalExceptionHandler.java` — `@ExceptionHandler(AccountLockedException.class)` returns HTTP 423, standard error body format (timestamp, status, error, message, traceId, fieldErrors: null); also confirm `AuthenticationException` already maps to 401
- [ ] T032 [US1] Create `backend/src/main/java/com/ainexus/hospital/patient/security/BlacklistCheckFilter.java` — `@Component extends OncePerRequestFilter`; skip list: `/actuator/health`, `/api/v1/auth/login`, `/swagger-ui`, `/api-docs`; extract Bearer token from `Authorization` header; parse ONLY the body (base64 decode middle segment) to read `jti` claim WITHOUT re-verifying signature (signature is verified by the downstream `JwtAuthFilter`); check `tokenBlacklistRepository.existsById(jti)`; if found, write HTTP 401 JSON response using the standard error format and `return` without calling `filterChain.doFilter()`
- [ ] T033 [US1] Register `BlacklistCheckFilter` in `backend/src/main/java/com/ainexus/hospital/patient/config/SecurityConfig.java` — add `.addFilterBefore(blacklistCheckFilter, JwtAuthFilter.class)` to the existing `filterChain` bean; this is a purely additive change — all existing lines remain unchanged

**Checkpoint**: `POST /api/v1/auth/login` works end-to-end. All T026/T027 tests pass. Returned token accepted by Patient Module endpoints. DevAuthController still present but superseded.

---

## Phase 4: User Story 2 — Token Refresh (Priority: P2)

**Goal**: Authenticated staff can refresh their token before expiry to maintain uninterrupted access across a hospital shift without re-entering credentials.

**Independent Test**: Obtain token via login → call `POST /api/v1/auth/refresh` → new token accepted by Patient Module. Original token still valid until natural expiry.

### Tests for US2 — Write First, Confirm FAILING ⚠️

- [ ] T034 [P] [US2] Extend `AuthServiceTest.java` with refresh scenarios — (a) valid non-revoked token → new `TokenResponse` with fresh expiry and different jti, (b) revoked token (jti in blacklist) → test that `BlacklistCheckFilter` blocks BEFORE service is called (filter-level test), (c) expired token parsing → verify service returns error
- [ ] T035 [P] [US2] Add `POST /auth/refresh` tests to `AuthIT.java` — (a) valid token → 200 new token, verify new token has later `expiresAt` than original, verify new token accepted by Patient Module, (b) call refresh with token obtained from step (a) of logout test → 401, (c) no Authorization header → 401

### Implementation for US2

- [ ] T036 [US2] Implement `AuthService.refresh()` in `AuthService.java` — reads `AuthContext.Holder.get()` (already populated by `JwtAuthFilter`), loads `HospitalUser` by `ctx.getUserId()`, checks `user.isActive()`, calls `issueToken(user)`, writes `TOKEN_REFRESH` audit event; no blacklisting of old token (spec AD-006)
- [ ] T037 [US2] Add `POST /auth/refresh` to `AuthController.java` — requires valid Bearer token (protected by `BlacklistCheckFilter` + `JwtAuthFilter`), calls `authService.refresh()`, returns `TokenResponse`

**Checkpoint**: Token refresh works. Both original and new token accepted by Patient Module until original expires.

---

## Phase 5: User Story 3 — Logout (Priority: P2)

**Goal**: Staff can explicitly log out, immediately invalidating their token so it cannot be used again even before its natural expiry.

**Independent Test**: Login → logout → attempt to use old token → 401 from `BlacklistCheckFilter`.

### Tests for US3 — Write First, Confirm FAILING ⚠️

- [ ] T038 [P] [US3] Add `POST /auth/logout` tests to `AuthIT.java` — (a) valid token → 204 No Content, (b) use the same token immediately after logout → 401 from blacklist filter (verify it's the filter rejecting, not JwtAuthFilter, by checking the response is identical to the blacklist rejection format), (c) second logout with same revoked token → 401 (idempotent), (d) after logout, verify token cannot refresh (`POST /auth/refresh` → 401)
- [ ] T039 [P] [US3] Extend `AuthServiceTest.java` with logout scenarios — (a) logout saves `TokenBlacklist` entry with correct jti + expiresAt, (b) `BlacklistCleanupService.purgeExpiredEntries()` removes entries where `expiresAt < now` and retains entries where `expiresAt >= now`

### Implementation for US3

- [ ] T040 [US3] Implement `AuthService.logout(String rawToken)` — parse jti and expiration from token body (base64 decode only, no signature re-verification); create `TokenBlacklist(jti, ctx.getUserId(), expiresAt, OffsetDateTime.now())`; persist via `TokenBlacklistRepository`; write `LOGOUT` audit event
- [ ] T041 [US3] Add `POST /auth/logout` to `AuthController.java` — extract raw Bearer token from `Authorization` header, call `authService.logout(rawToken)`, return `ResponseEntity.noContent().build()` (HTTP 204)
- [ ] T042 [US3] Create `backend/src/main/java/com/ainexus/hospital/patient/service/BlacklistCleanupService.java` — `@Service @EnableScheduling`-compatible; method `@Scheduled(cron = "0 */15 * * * *") @Transactional purgeExpiredBlacklistEntries()` calls `tokenBlacklistRepository.deleteByExpiresAtBefore(OffsetDateTime.now())`; add `@EnableScheduling` to a new `@Configuration` class `SchedulingConfig.java`

**Checkpoint**: Logout immediately revokes token. Cleanup service bounds blacklist table size. `BlacklistCheckFilter` correctly intercepts revoked tokens before `JwtAuthFilter` runs.

---

## Phase 6: User Story 4 — Current User Profile (Priority: P3)

**Goal**: Authenticated staff can retrieve their own profile for display in the navigation bar (name, role badge, department) without re-parsing the JWT on the frontend.

**Independent Test**: Login → `GET /api/v1/auth/me` → response contains correct userId, username, role, email, department, lastLoginAt.

### Tests for US4 — Write First, Confirm FAILING ⚠️

- [ ] T043 [P] [US4] Create `backend/src/test/java/com/ainexus/hospital/patient/integration/SessionIT.java` — integration tests: (a) valid token → 200 `UserProfileResponse` with all fields, (b) verify `passwordHash` is NOT present in response body at any nesting level, (c) revoked token → 401, (d) expired token → 401, (e) no token → 401
- [ ] T044 [P] [US4] Add `getCurrentUser` unit test to `AuthServiceTest.java` — loads `HospitalUser` by userId from `AuthContext`, maps to `UserProfileResponse`, `passwordHash` excluded by `StaffMapper`

### Implementation for US4

- [ ] T045 [US4] Implement `AuthService.getCurrentUser()` — reads `AuthContext.Holder.get().getUserId()`, loads `HospitalUser` from `HospitalUserRepository.findById()` (throws `AuthenticationException` if not found — unlikely but defensive), maps via `StaffMapper.toProfileResponse(user)`, returns `UserProfileResponse`
- [ ] T046 [US4] Add `GET /auth/me` to `AuthController.java` — requires valid Bearer token; calls `authService.getCurrentUser()`; returns `UserProfileResponse`; response must never include `passwordHash` (enforced by `StaffMapper` explicit exclusion)

**Checkpoint**: `/auth/me` returns complete profile. Verified `passwordHash` is absent from all API responses.

---

## Phase 7: User Story 5 — Staff Account Management (Priority: P3)

**Goal**: ADMIN staff can create new accounts, list existing staff, update details, and deactivate accounts — enabling the hospital to onboard and offboard staff without any manual database access.

**Independent Test**: ADMIN creates a RECEPTIONIST account → RECEPTIONIST logs in → searches patients successfully.

### Tests for US5 — Write First, Confirm FAILING ⚠️

- [ ] T047 [P] [US5] Create `backend/src/test/java/com/ainexus/hospital/patient/service/StaffServiceTest.java` — unit tests: (a) `createUser()` hashes password, generates userId, sets status ACTIVE, saves and returns `UserDetailResponse`, (b) `createUser()` with duplicate username throws `UsernameConflictException` (409), (c) `updateUser()` with matching version updates fields + writes audit, (d) `updateUser()` with wrong version throws `OptimisticLockingFailureException`, (e) `deactivateUser()` sets status INACTIVE + writes audit, (f) ADMIN self-deactivation throws `ForbiddenException` (403)
- [ ] T048 [P] [US5] Create `backend/src/test/java/com/ainexus/hospital/patient/integration/StaffManagementIT.java` — integration tests for all 5 `/admin/users` operations: (a) POST creates user + 201 + Location header, (b) POST duplicate username → 409, (c) GET list → paginated response with all fields, (d) GET list with `?role=DOCTOR` → filtered, (e) GET `/{userId}` → `UserDetailResponse`, (f) GET `/{userId}` not found → 404, (g) PATCH updates department + ETag header on response, (h) PATCH wrong If-Match → 409, (i) DELETE deactivates → 204, (j) verify deactivated user cannot login → 401
- [ ] T049 [P] [US5] Create `backend/src/test/java/com/ainexus/hospital/patient/integration/AuthRbacIT.java` — full role matrix for all 9 auth endpoints: (a) all roles can `POST /auth/login`, (b) all roles can `POST /auth/refresh`, (c) all roles can `POST /auth/logout`, (d) all roles can `GET /auth/me`, (e) only ADMIN can `POST /admin/users` (RECEPTIONIST/DOCTOR/NURSE → 403), (f) only ADMIN can `GET /admin/users` (others → 403), (g) only ADMIN can `GET /admin/users/{id}` (others → 403), (h) only ADMIN can `PATCH /admin/users/{id}` (others → 403), (i) only ADMIN can `DELETE /admin/users/{id}` (others → 403)

### Implementation for US5

- [ ] T050 [US5] Create `backend/src/main/java/com/ainexus/hospital/patient/exception/UsernameConflictException.java` — maps to HTTP 409; message includes the conflicting username
- [ ] T051 [US5] Implement `StaffService.createUser(CreateUserRequest req, String createdByUsername)` in `backend/src/main/java/com/ainexus/hospital/patient/service/StaffService.java` — `@Service @Transactional`; enforce ADMIN role via `roleGuard.requireRoles("ADMIN")`; check `existsByUsernameIgnoreCase()` → throw `UsernameConflictException`; validate password policy (min 8, upper + lower + digit); `passwordEncoder.encode(rawPassword)`; `staffIdGeneratorService.generateStaffId()`; build and save `HospitalUser`; write `USER_CREATED` audit; map to `UserDetailResponse` via `StaffMapper`
- [ ] T052 [US5] Implement `StaffService.listUsers(String role, String status, Pageable pageable)` — enforce ADMIN role; delegate to `HospitalUserRepository` with dynamic filter (both/role-only/status-only/neither); map page content to `UserSummaryResponse`; return `Page<UserSummaryResponse>`
- [ ] T053 [US5] Implement `StaffService.getUserById(String userId)` — enforce ADMIN role; `findById()` or throw `ResourceNotFoundException` (404); map to `UserDetailResponse`
- [ ] T054 [US5] Implement `StaffService.updateUser(String userId, UpdateUserRequest req, Integer version, String updatedByUsername)` — enforce ADMIN role; find user; check `user.getVersion().equals(version)` → throw `VersionConflictException` (409) if mismatch; apply non-null fields from `UpdateUserRequest`; set `updatedAt` + `updatedBy`; save; write `USER_UPDATED` audit with changed field names (NOT values); return `UserDetailResponse` with new ETag
- [ ] T055 [US5] Implement `StaffService.deactivateUser(String userId, String requestingUserId)` — enforce ADMIN role; if `userId.equals(requestingUserId)` throw `ForbiddenException` (403) with message "An administrator cannot deactivate their own account"; if already INACTIVE return without error (idempotent); set `status = INACTIVE`; save; write `USER_DEACTIVATED` audit
- [ ] T056 [US5] Create `backend/src/main/java/com/ainexus/hospital/patient/controller/StaffController.java` — `@RestController @RequestMapping("/api/v1/admin/users")`; `POST /` → `staffService.createUser()` + HTTP 201 + `Location` header; `GET /` → `staffService.listUsers()` with `@RequestParam` for role/status/sort/direction and `Pageable`; `GET /{userId}` → `staffService.getUserById()`; `PATCH /{userId}` → reads `If-Match` header, parses version int, calls `staffService.updateUser()`, sets `ETag` response header; `DELETE /{userId}` → reads `AuthContext.Holder.get().getUserId()` for self-check, calls `staffService.deactivateUser()`, returns 204
- [ ] T057 [US5] Add `UsernameConflictException`, `VersionConflictException`, `ForbiddenException` (from self-deactivation guard) handlers to `GlobalExceptionHandler.java` — 409 for both conflict types, 403 for forbidden with message

**Checkpoint**: Full staff account lifecycle works. RBAC matrix verified for all 9 endpoints. ADMIN creates RECEPTIONIST → RECEPTIONIST logs in → accesses Patient Module.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Remove the dev stub, harden Nginx, update the frontend, and validate the complete stack.

- [ ] T058 Delete `backend/src/main/java/com/ainexus/hospital/patient/controller/DevAuthController.java` — remove the entire file; verify no other file imports or references it; the `/api/v1/auth/dev-login` endpoint must return 404 after deletion
- [ ] T059 [P] Update `nginx/nginx.conf` — add `limit_req_zone $binary_remote_addr zone=login_limit:10m rate=10r/m;` in the `http` block alongside the existing `api_limit` zone; add `location /api/v1/auth/login { limit_req zone=login_limit burst=3 nodelay; limit_req_status 429; add_header Retry-After 60 always; proxy_pass http://backend:8080/api/v1/auth/login; ... }` block BEFORE the existing `location /api/v1/` block (longest-prefix takes precedence); all existing lines are unchanged
- [ ] T060 [P] Update `frontend/src/pages/LoginPage.jsx` — change POST target from `/api/v1/auth/dev-login` to `/api/v1/auth/login`; update response field extraction if the shape changed (`token`, `userId`, `username`, `role`, `expiresAt`); ensure `sessionStorage.setItem('jwt_token', data.token)` still works
- [ ] T061 [P] Update `frontend/src/api/authApi.js` — create/update with `login(username, password)` calling `POST /api/v1/auth/login`, `logout()` calling `POST /api/v1/auth/logout`, `refresh()` calling `POST /api/v1/auth/refresh`, `getMe()` calling `GET /api/v1/auth/me`; all calls use the Axios instance that injects the Bearer token
- [ ] T062 [P] Add `@EnableScheduling` to a new `backend/src/main/java/com/ainexus/hospital/patient/config/SchedulingConfig.java` — `@Configuration @EnableScheduling`; enables `BlacklistCleanupService.purgeExpiredBlacklistEntries()` to fire on cron schedule
- [ ] T063 Run full backend test suite — `cd backend && mvn verify -Pfailsafe -Dapi.version=1.44` and confirm: (a) all 108 existing Patient Module tests still pass (no regression), (b) all new Auth Module unit tests pass, (c) all new integration tests pass; total expected: 108 + new auth tests passing
- [ ] T064 [P] Validate Docker Compose stack end-to-end — `docker compose up --build -d`; confirm: (a) all 4 containers healthy, (b) Flyway applies V5/V6/V7 migrations without errors, (c) `AdminSeeder` creates seed ADMIN account (check backend logs), (d) `POST /api/v1/auth/login` with ADMIN credentials returns 200, (e) `GET /api/v1/patients` with returned token returns 200 (Patient Module accepts Auth Module token), (f) `POST /api/v1/auth/dev-login` returns 404 (stub removed)
- [ ] T065 [P] Update `CLAUDE.md` — add Auth Module key patterns to `Recent Changes`: BCrypt-12, BlacklistCheckFilter filter order, AdminSeeder env var requirement, token blacklist cleanup schedule

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately; all T001–T007 are parallel
- **Foundational (Phase 2)**: Depends on Phase 1 completion — T008–T025 are parallel within phase
- **US1 (Phase 3)**: Depends on Phase 2 — BLOCKS all other story phases
- **US2 (Phase 4)**: Depends on Phase 2 — can proceed once US1 tests pass (shares `AuthService`)
- **US3 (Phase 5)**: Depends on Phase 2 + `BlacklistCheckFilter` from US1 (T032) — full blacklist chain required
- **US4 (Phase 6)**: Depends on Phase 2 only — independent of US2/US3
- **US5 (Phase 7)**: Depends on Phase 2 only — independent of US2/US3/US4
- **Polish (Phase 8)**: Depends on all US phases complete

### User Story Dependencies

- **US1 (P1)**: Only depends on Phase 2 (entities + service infrastructure)
- **US2 (P2)**: Depends on Phase 2 + `AuthService.issueToken()` from US1 — same `AuthService` class
- **US3 (P2)**: Depends on Phase 2 + `BlacklistCheckFilter` (T032) from US1 — filter must be registered
- **US4 (P3)**: Depends on Phase 2 only — `AuthController.me` is a simple lookup
- **US5 (P3)**: Depends on Phase 2 only — `StaffService` and `StaffController` are independent

### Within Each User Story (TDD Order — MANDATORY)

1. Write test tasks — run them — **confirm FAILING** (Red)
2. Implement production code tasks (Green)
3. Verify tests pass
4. Commit story

---

## Parallel Opportunities

### Phase 1 (T001–T007 all parallel after T001 config)

```
T002 (.env.example) ──┐
T003 (V5 migration) ──┤
T004 (V6 migration) ──┤── all parallel
T005 (V7 migration) ──┤
T006 (Exception)    ──┤
T007 (PasswordConfig)─┘
```

### Phase 2 (T008–T025, entities + repos + DTOs all parallel)

```
Entities:    T008, T009, T010, T011 ──── parallel
Repositories: T012, T013, T014, T015 ─── parallel (after entities)
DTOs:        T016–T021 ──────────────── parallel (no entity dependency)
Services:    T022, T023, T024 ─────────── parallel
AdminSeeder: T025 ─────────────────────── after T022 (uses PasswordEncoder bean)
```

### US5 Test Tasks (all parallel)

```
T047 (StaffServiceTest) ──┐
T048 (StaffManagementIT) ──┤── all written in parallel
T049 (AuthRbacIT)        ──┘
```

---

## Implementation Strategy

### MVP First (US1 Only — Replaces DevAuthController)

1. Complete Phase 1: Setup (migrations, config, exception, PasswordConfig)
2. Complete Phase 2: Foundation (all entities, repos, DTOs, services)
3. Complete Phase 3: US1 (login with lockout + BlacklistCheckFilter)
4. Run T063 partial — verify US1 integration tests pass + all 108 Patient Module tests still pass
5. **STOP and VALIDATE**: Staff can log in. Token is accepted by the Patient Module. DevAuthController can now be deleted (T058).

### Incremental Delivery

1. Setup + Foundation → Core infrastructure ready
2. US1 → Staff login works → Delete DevAuthController → **MVP**
3. US2 → Token refresh works → Shifts no longer interrupted
4. US3 → Logout works → Tokens revocable → **Security hardened**
5. US4 → `/me` endpoint → Frontend navigation bar populated
6. US5 → Admin CRUD → Staff onboarding/offboarding operational
7. Polish → Nginx rate limit + full regression → **Production ready**

### Parallel Team Strategy

With 2+ developers after Phase 2 completes:
- Developer A: US1 (login) — critical path
- Developer B: US4 (profile) + US5 (staff management) — independent
- After US1: Developer A moves to US2 (refresh) then US3 (logout)

---

## Notes

- **[P]** tasks operate on different files — no merge conflicts when run in parallel
- **[Story]** label maps each task to its user story for traceability
- **TDD is mandatory**: Test tasks within each story MUST fail before implementation starts
- Never log passwords, raw tokens, or PHI in any log statement — verified by test T063
- `ADMIN_INITIAL_PASSWORD` has no default; application fails to start if absent — by design
- Existing V1–V4 Flyway migrations must NEVER be modified (Flyway checksum validation)
- `DevAuthController` is deleted in T058 — after this, `/api/v1/auth/dev-login` returns 404
- The Patient Module's `JwtAuthFilter` and all other frozen files are touched ONLY in T033 (additive filter registration in `SecurityConfig.java`)
