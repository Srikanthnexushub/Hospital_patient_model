# Implementation Plan: Auth Module

**Branch**: `002-auth-module` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Status**: Draft — ready for task generation
**Input**: Feature specification from `specs/002-auth-module/spec.md`

---

## Summary

Build the Auth Module for the Hospital Management System — a production-grade
authentication and staff identity service that replaces the temporary `DevAuthController`
stub. The backend is the same Spring Boot 3.2.x application as Module 1 (same Maven
module, same package root). Two new PostgreSQL tables (V5–V7 Flyway migrations) are
added. No new infrastructure services are required.

The module issues JWTs that are byte-for-byte compatible with the Patient Module's
existing `JwtAuthFilter`. The Patient Module is frozen and must not be modified.
Zero hardcoded secrets or passwords anywhere in the codebase.

---

## Technical Context

**Language/Version**: Java 17 (backend), JavaScript/Node 20 LTS (frontend)

**Primary Dependencies** (all already in `pom.xml`):
- Spring Boot 3.2.x, Spring Security 6.2.x, Spring Data JPA
- jjwt 0.12.5 (JWT issuance and parsing)
- `spring-security-crypto` (BCryptPasswordEncoder — transitively included)
- Resilience4j 2.2.0 (circuit breaker)
- Flyway (database migrations)
- MapStruct (entity↔DTO mapping)
- JUnit 5 + Mockito + Testcontainers (testing)
- React 18.x + TanStack Query v5 + React Hook Form + Zod (frontend)

**Storage**: PostgreSQL 15 (same Docker container as Patient Module).
New tables: `hospital_users`, `staff_id_sequences`, `token_blacklist`, `auth_audit_log`.
Migrations V5–V7. Existing V1–V4 must not be modified.

**Testing**:
- Unit: JUnit 5 + Mockito (AuthService, StaffService, BlacklistCheckFilter, validators)
- Integration: Spring Boot Test + Testcontainers PostgreSQL (full stack, all endpoints)
- TDD order enforced: tests written and failing before implementation

**Target Platform**: Same Docker Compose stack. No new containers. Nginx on 80/443,
Backend on 8080 internally.

**Project Type**: Web application (Spring Boot backend + React frontend, same source
trees as Module 1).

**Performance Goals**:
- Login ≤ 500ms p95 (BCrypt ~400ms + ~100ms overhead)
- Token validation ≤ 10ms p95 (in-memory signature check + ~1ms blacklist lookup)
- All other auth endpoints ≤ 200ms p95

**Constraints**:
- JWT claims structure MUST match the frozen Patient Module contract exactly
- No hardcoded passwords, secrets, or user credentials in any source file or migration
- `ADMIN_INITIAL_PASSWORD` env var has no default — missing value = startup failure
- BCrypt strength configurable (default 12); JWT expiry configurable (default 8h)
- `DevAuthController` is removed; `/api/v1/auth/dev-login` endpoint is deleted
- HIPAA: passwords never logged, never in audit trail, never in error responses
- All 4 roles (RECEPTIONIST, DOCTOR, NURSE, ADMIN) must issue valid tokens

**Scale/Scope**:
- ~500 concurrent staff users (login frequency ~50 logins/hour at peak)
- Same 100,000 req/hour peak as Patient Module
- Single backend instance (same as Module 1 constraint)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate Condition | Status |
|---|---|---|
| I. Spec-Driven Development | Spec exists and is approved before any code | ✅ PASS — spec.md v1.0.0 complete, all quality gates passed |
| II. HIPAA-First | PHI handling, audit log, no PHI in logs, HTTPS, role enforcement | ✅ PASS — FR-029–FR-034 cover all HIPAA audit requirements; passwords never logged; auth_audit_log records identity events only |
| III. Test-First | Tests written before implementation; JUnit 5 + Testcontainers plan confirmed | ✅ PASS — unit + integration tests planned, TDD order enforced in tasks |
| IV. Layered Architecture | Controller → Service → Repository; DTOs at boundary; no cross-layer leaks | ✅ PASS — `AuthController` / `StaffController` → `AuthService` / `StaffService` → repositories; all responses use DTOs |
| V. RBAC | Server-side role check on every endpoint; HTTP 401/403 defined | ✅ PASS — `RoleGuard.requireRoles("ADMIN")` on all admin endpoints; public endpoints explicitly marked `security: []` |

**No constitution violations. Proceeding to Phase 1.**

### Post-Design Constitution Re-check (Phase 1 Complete)

| Principle | Design Decision | Status |
|---|---|---|
| I. Spec-Driven | All design artifacts derived from spec.md v1.0.0 | ✅ PASS |
| II. HIPAA-First | Auth audit log records event type + outcome + user ID (no passwords, no PHI); BCrypt ensures passwords are never recoverable; no plaintext credential anywhere in code | ✅ PASS |
| III. Test-First | Unit tests: JUnit 5 + Mockito for AuthService, StaffService, BlacklistCheckFilter, validators; Integration: Testcontainers for all 9 endpoints | ✅ PASS |
| IV. Layered Architecture | `AuthController` (HTTP only) → `AuthService` (login/refresh/logout logic) → `HospitalUserRepository` + `TokenBlacklistRepository`; `StaffController` → `StaffService` → repositories; MapStruct mappers for all DTOs | ✅ PASS |
| V. RBAC | `RoleGuard.requireRoles("ADMIN")` in `StaffService`; public auth endpoints use `security: []` in OpenAPI and `permitAll()` in SecurityConfig; `BlacklistCheckFilter` enforces token revocation before RBAC evaluation | ✅ PASS |

**All constitution gates pass post-design. Plan approved for task generation.**

---

## Project Structure

### Documentation (this feature)

```text
specs/002-auth-module/
├── plan.md              ← this file
├── spec.md              ← feature specification
├── research.md          ← Phase 0 research decisions
├── data-model.md        ← entity and schema design
├── quickstart.md        ← developer getting-started guide
├── contracts/
│   └── openapi.yaml     ← REST API contract (OpenAPI 3.1.0)
├── checklists/
│   └── requirements.md  ← specification quality checklist
└── tasks.md             ← Phase 2 output (generated by /speckit.tasks)
```

### Source Code (additions to existing repository)

```text
backend/src/main/java/com/ainexus/hospital/patient/
├── controller/
│   ├── AuthController.java          # POST /auth/login, /auth/refresh, /auth/logout; GET /auth/me
│   └── StaffController.java         # POST|GET /admin/users; GET|PATCH|DELETE /admin/users/{id}
├── service/
│   ├── AuthService.java             # login, refresh, logout, issueToken, buildTokenResponse
│   ├── StaffService.java            # createUser, listUsers, getUser, updateUser, deactivateUser
│   ├── StaffIdGeneratorService.java # generateStaffId() — mirrors PatientIdGeneratorService
│   ├── BlacklistCleanupService.java # @Scheduled purge of expired token_blacklist rows
│   └── AdminSeeder.java             # ApplicationRunner — seeds ADMIN on first startup
├── repository/
│   ├── HospitalUserRepository.java  # findByUsername, findById, existsByUsername
│   ├── StaffIdSequenceRepository.java # findByYearForUpdate (SELECT FOR UPDATE)
│   └── TokenBlacklistRepository.java  # existsById (jti check), deleteByExpiresAtBefore
├── entity/
│   ├── HospitalUser.java            # @Entity hospital_users
│   ├── StaffIdSequence.java         # @Entity staff_id_sequences
│   ├── TokenBlacklist.java          # @Entity token_blacklist
│   └── AuthAuditLog.java            # @Entity auth_audit_log (immutable)
├── dto/
│   ├── LoginRequest.java
│   ├── TokenResponse.java
│   ├── UserProfileResponse.java
│   ├── CreateUserRequest.java
│   ├── UpdateUserRequest.java
│   ├── UserSummaryResponse.java
│   └── UserDetailResponse.java
├── mapper/
│   └── StaffMapper.java             # HospitalUser ↔ UserDetailResponse, UserSummaryResponse
├── security/
│   └── BlacklistCheckFilter.java    # OncePerRequestFilter — checks jti against token_blacklist
├── config/
│   └── PasswordConfig.java          # @Bean PasswordEncoder (BCryptPasswordEncoder strength 12)
├── exception/
│   └── AccountLockedException.java  # Maps to HTTP 423
└── audit/
    └── AuthAuditService.java        # writeAuthLog(eventType, actorId, targetId, outcome, ip)

backend/src/main/resources/db/migration/
├── V5__create_hospital_users.sql    # hospital_users + staff_id_sequences tables
├── V6__create_token_blacklist.sql   # token_blacklist table
└── V7__create_auth_audit_log.sql    # auth_audit_log table

backend/src/main/resources/
└── application.yml                  # +app.auth.jwt.expiration-hours, lockout config

backend/src/test/java/com/ainexus/hospital/patient/
├── service/
│   ├── AuthServiceTest.java         # Unit: login flows, lockout, refresh, logout
│   └── StaffServiceTest.java        # Unit: CRUD, role enforcement, self-deactivation guard
├── security/
│   └── BlacklistCheckFilterTest.java
└── integration/
    ├── AuthIT.java                  # Integration: US1 (login), US2 (refresh), US3 (logout)
    ├── SessionIT.java               # Integration: US4 (/me endpoint)
    ├── StaffManagementIT.java       # Integration: US5 (CRUD admin operations)
    └── AuthRbacIT.java              # Integration: full role matrix for all 9 endpoints

frontend/src/
├── pages/
│   └── LoginPage.jsx                # Update: POST → /api/v1/auth/login (was /dev-login)
├── api/
│   └── authApi.js                   # login(), logout(), refresh(), getMe()
└── hooks/
    └── useAuth.js                   # Update: reflect new /login path

nginx/
└── nginx.conf                       # Add login_limit zone + /api/v1/auth/login location block

# DELETED (Module 1 stub — replaced by this module):
backend/src/main/java/com/ainexus/hospital/patient/controller/DevAuthController.java
```

---

## Architecture Decisions

### AD-001: BCryptPasswordEncoder Strength 12
Store in a new `PasswordConfig.java` `@Configuration` class (not inside `SecurityConfig`)
to preserve separation of concerns. Strength 12 is non-configurable at runtime
(changing it would invalidate stored hashes); only changeable via code + full
re-hash migration.

### AD-002: BlacklistCheckFilter Before JwtAuthFilter
Register `BlacklistCheckFilter` in `SecurityConfig.java` before `JwtAuthFilter`.
`SecurityConfig` is shared infrastructure in the same Spring Boot app and is not
"frozen" in the same sense as business logic. The filter registration is a purely
additive change.

Filter order:
1. `BlacklistCheckFilter` (new) — rejects revoked tokens
2. `JwtAuthFilter` (existing) — validates signature, sets `AuthContext`

### AD-003: Staff ID Format `U + YYYY + seq`
New `StaffIdGeneratorService` and `staff_id_sequences` table mirror the Patient Module
pattern exactly. `SELECT FOR UPDATE` pessimistic locking on the sequence row ensures
atomicity. The `DevAuthController` already uses `U001`-style IDs; this is the natural
productionisation.

### AD-004: AdminSeeder as ApplicationRunner
`AdminSeeder` implements `ApplicationRunner` and runs after full `ApplicationContext`
assembly. It has access to the Spring-managed `BCryptPasswordEncoder` bean.
`@Value("${ADMIN_INITIAL_PASSWORD}")` (no default) causes Spring startup failure if
the variable is absent — satisfying FR-027.
Idempotent: `if (userRepository.count() > 0) return;`

### AD-005: Token Blacklist in PostgreSQL (No Redis)
At ~500 staff and ~8-hour token lifetime, the blacklist holds ≤ 2,000 rows at
steady state. A PK lookup on 2,000 rows is sub-millisecond. No new infrastructure
service is warranted. A `@Scheduled` job purges expired entries every 15 minutes.

### AD-006: Token Refresh — No Old-Token Blacklisting
Old token remains valid until natural expiry. Protects multi-tab hospital workstation
usage. `BlacklistCheckFilter` already ensures any explicitly logged-out token is
rejected. The spec explicitly endorses this approach.

### AD-007: Auth Audit Log (New Table, New Service)
`auth_audit_log` is separate from `patient_audit_log`. A new `AuthAuditService` writes
only auth domain events. No FK constraints — same HIPAA retention-independence pattern
as the patient audit log.

### AD-008: DevAuthController Removed
The class is deleted. All clients switch from `/api/v1/auth/dev-login` to
`/api/v1/auth/login`. The frontend `LoginPage.jsx` is updated accordingly.
No new endpoints are added to replace `/dev-login` — it is a development stub.

---

## Complexity Tracking

> No constitution violations — no entries required.

---

## Phase 0: Research Summary

See [research.md](./research.md) for full findings. All unknowns resolved:

| Unknown | Resolution |
|---|---|
| BCrypt strength | 12 (satisfies OWASP, fits ≤ 2s login SLA) |
| Token blacklist storage | PostgreSQL `token_blacklist` table, jti PK |
| Blacklist check point | New `BlacklistCheckFilter` before `JwtAuthFilter` |
| Account lockout storage | `failed_attempts` + `locked_until` on `HospitalUser` |
| Token refresh strategy | New token; old valid until expiry |
| Staff user ID format | `U + YYYY + seq` (mirrors Patient Module) |
| Seed admin mechanism | `ApplicationRunner` reads `ADMIN_INITIAL_PASSWORD` |
| Next Flyway migration | V5 |
| Nginx login rate limit | New `login_limit` zone + dedicated location block |

---

## Phase 1: Design Artifacts

| Artifact | File | Status |
|---|---|---|
| Data model | `data-model.md` | ✅ Complete |
| API contract | `contracts/openapi.yaml` | ✅ Complete |
| Developer guide | `quickstart.md` | ✅ Complete |

---

## Implementation Order (for task generation)

Priority order mirrors user story priority and dependency graph:

1. **Foundation** — DB migrations (V5–V7), entities, repositories
2. **Core Auth** — US1: Login (BCrypt verify, lockout, JWT issuance, audit)
3. **Token Lifecycle** — US2: Refresh; US3: Logout + BlacklistCheckFilter
4. **Session** — US4: `/me` endpoint
5. **Staff Management** — US5: ADMIN CRUD operations
6. **Security Hardening** — remove DevAuthController, Nginx login rate limit, PasswordConfig
7. **Frontend** — Update LoginPage to use new `/auth/login` endpoint
8. **Tests** — TDD throughout; integration test suite covering all endpoints and role matrix
