# Research: Auth Module

**Branch**: `002-auth-module` | **Date**: 2026-02-20
**Phase**: Phase 0 — Technology & Pattern Research

---

## R-001: Password Hashing

**Decision**: `BCryptPasswordEncoder` with work factor 12, declared as a Spring `@Bean` in a dedicated `PasswordConfig.java` class.

**Rationale**:
- At strength 12, each hash operation takes ~400ms on a modern server. This is imperceptible to a human but makes brute-force attacks prohibitively slow (1M attempts ≈ 4.6 days/core). OWASP recommends strength ≥ 12 for medical systems.
- Login SLA (SC-001 ≤ 2s total) is satisfied: 400ms for hashing + ~100ms network/DB + ~50ms token issuance = well within 2 seconds.
- `BCryptPasswordEncoder.encode()` auto-generates a unique salt per call; `matches()` uses constant-time comparison — both protect against rainbow table and timing attacks.
- Placing the bean in `PasswordConfig.java` (not `SecurityConfig.java`) keeps the shared `SecurityConfig` concise.

**Alternatives considered**:
- Strength 10 — faster (~100ms) but below OWASP minimum for sensitive data.
- Strength 13 — doubles server CPU per login; no measurable security improvement at this scale.
- Argon2 — stronger than BCrypt but not in `spring-security-crypto` as a first-class encoder; requires additional dependency. BCrypt is sufficient for the stated threat model.

---

## R-002: JWT Token Blacklist

**Decision**: `jti` (JWT ID) UUID4 claim added at token issuance; blacklist entries stored in a `token_blacklist` PostgreSQL table; checked by a new `BlacklistCheckFilter` registered before the existing `JwtAuthFilter`.

**Rationale**:
- Adding `jti` to the token payload is backward-compatible: the existing `JwtAuthFilter` does not read `jti` (it ignores unknown claims), satisfying FR-004.
- A dedicated `BlacklistCheckFilter` means `JwtAuthFilter` (in the frozen Patient Module) is never modified.
- `token_blacklist` table: `jti` VARCHAR(36) PRIMARY KEY + `expires_at` TIMESTAMPTZ with an index. At steady state (500 staff, 1-2 logouts/shift) the table holds ~2,000 rows. A PK lookup on 2,000 rows completes in < 1ms — negligible latency.
- Scheduled purge every 15 minutes deletes rows where `expires_at < NOW()` — the table stays bounded.
- No Redis needed: in-memory caching of the blacklist would add operational complexity (new service) for a workload that PostgreSQL handles trivially.

**Alternatives considered**:
- In-memory `ConcurrentHashMap` blacklist — lost on restart, violates FR-016.
- Redis blacklist — adds a new infrastructure service not present in the current Docker Compose stack; unjustified for the current scale.
- Modify `JwtAuthFilter` to check blacklist — violates FR-004 (frozen Patient Module).

---

## R-003: Account Lockout

**Decision**: Store `failed_attempts` (INTEGER) and `locked_until` (TIMESTAMPTZ) directly on the `HospitalUser` entity with `@Version` for optimistic locking.

**Rationale**:
- The lockout check (step 1: is `locked_until` in the future?) happens **before** the password verification — correct per FR-007/FR-009.
- On success: reset `failed_attempts = 0`, clear `locked_until = null`. On failure: increment counter; if ≥ threshold, set `locked_until = NOW() + duration`.
- `@Version` (integer) prevents the race condition where two concurrent failed login attempts both read the same counter value. The second concurrent `save()` triggers `ObjectOptimisticLockingFailureException` which `AuthService` catches and re-throws as `AuthenticationException` — the attacker does not gain an extra attempt.
- Both `lockoutMaxAttempts` and `lockoutDurationMinutes` are externalised to `application.yml` via `${AUTH_LOCKOUT_MAX_ATTEMPTS:5}` and `${AUTH_LOCKOUT_DURATION_MINUTES:15}` — zero hardcoded values.
- Locked-account response: HTTP 423 (Locked), distinct from HTTP 401 — satisfies FR-009.

**Alternatives considered**:
- Pessimistic locking (`SELECT FOR UPDATE`) — eliminates the race completely but adds DB lock contention on every login; overkill for human-paced login frequency.
- Spring Security `UserDetailsService` / `AbstractUserDetailsAuthenticationProvider` — adds complexity (requires full Spring Security auth infrastructure) for no benefit beyond what a plain service method achieves.

---

## R-004: Token Refresh Strategy

**Decision**: Accept a valid (non-expired, non-revoked) access token; issue a new access token; the old token remains valid until its natural expiry.

**Rationale**:
- The spec explicitly requires this approach (Assumptions section).
- Blacklisting the old token on refresh would require all browser tabs to synchronously update the stored token before the next request — creating race conditions on multi-tab hospital workstations.
- The security risk (two valid tokens briefly) is bounded: within an 8-hour shift, the old token expires naturally; explicit logout immediately revokes it.

**Alternatives considered**:
- Blacklist old token on refresh — stricter but creates frontend complexity; not justified for the current use case.
- Separate long-lived refresh token — adds a refresh token storage/validation path; overkill when the access token lifetime (8 hours) already matches a hospital shift.

---

## R-005: Staff User ID Generation

**Decision**: `U + YYYY + 3-digit-zero-padded-seq` pattern (e.g., `U2026001`), implemented with a new `StaffIdGeneratorService` and `staff_id_sequences` table mirroring the existing `PatientIdGeneratorService` and `patient_id_sequences` design.

**Rationale**:
- Consistent format across all entities in the system — immediate visual distinction in audit logs (`P2026001` = patient, `U2026001` = staff user).
- No new infrastructure: the `SELECT FOR UPDATE` pessimistic locking sequence pattern is already battle-tested in the Patient Module.
- The `DevAuthController` uses short IDs (`U001`, `U002`) — the `U + YYYY + seq` format is the natural productionisation.
- `AuthContext.userId` is a `String`; both UUID and `U + YYYY + seq` work equally; the human-readable format is preferable for logs and audit trail correlation.

**Alternatives considered**:
- UUID4 — no sequence table needed, globally unique, but not human-readable in logs/audit; inconsistent with the existing entity ID style.
- Sequential integer — enumerable, potentially exposable via JWT `sub` claim.

---

## R-006: Database Migration Strategy

**Decision**: Two new Flyway SQL migrations (V5, V6); seed admin via `ApplicationRunner` bean, not a SQL migration.

**Migration files**:
- `V5__create_hospital_users.sql` — `hospital_users` table, constraints, indexes, `staff_id_sequences` table
- `V6__create_token_blacklist.sql` — `token_blacklist` table, constraints, indexes

**Seed approach**: Spring `ApplicationRunner` (runs after full `ApplicationContext` is assembled). Reads `ADMIN_INITIAL_PASSWORD` env var via `@Value` with no default — Spring startup fails if absent, satisfying FR-027. Calls `PasswordEncoder.encode()` (BCrypt strength 12, Spring-managed bean). Idempotent: no-ops if any users already exist.

**Rationale**:
- Flyway SQL migrations cannot BCrypt-hash a password in SQL (PostgreSQL has no BCrypt function).
- Flyway Java migrations run before Spring beans are assembled — `PasswordEncoder` is unavailable without hacks.
- `ApplicationRunner` is the canonical Spring Boot 3.2.x pattern for bootstrap seeding.
- Idempotent check (`userRepository.count() > 0 → skip`) makes restarts safe.

**Existing migration inventory** (V1–V4, must not be modified):
- V1: `patients` table (demographics, status, audit fields)
- V2: `patient_id_sequences` table (year-based ID generation)
- V3: `patient_audit_log` table (immutable, no FK)
- V4: Indexes on `patients` and `patient_audit_log`

---

## R-007: Nginx Login Rate Limiting

**Decision**: Add a new `login_limit` zone (10 req/IP/min, burst=3) and a dedicated `location /api/v1/auth/login` block. The login location block overrides the general `api_limit` zone for the login path (Nginx longest-prefix matching).

**Rationale**:
- Nginx longest-prefix match: a `location /api/v1/auth/login` block (length 21) beats `location /api/v1/` (length 8), so the login endpoint is exclusively governed by `login_limit`.
- `burst=3` with `nodelay` serves legitimate rapid retries (mistyped password) immediately, then returns HTTP 429 — automated scanners hit the limit fast.
- The existing `api_limit` zone, the `/api/v1/` location block, and all other Nginx configuration are untouched — purely additive change.

**Change summary** (two additions to `nginx/nginx.conf`):
1. New zone declaration in `http` block: `limit_req_zone $binary_remote_addr zone=login_limit:10m rate=10r/m;`
2. New location block in the HTTPS `server` block, before the existing `/api/v1/` block.

---

## R-008: Filter Registration in SecurityConfig

**Decision**: Add `BlacklistCheckFilter` registration to the existing `SecurityConfig.java`, placing it before `JwtAuthFilter`.

**Rationale**:
- `SecurityConfig` is shared infrastructure in the same Spring Boot application — modifying it to add a filter bean registration is additive and non-breaking for the Patient Module.
- The spec's "no changes to Patient Module" constraint refers to not breaking the JWT validation behaviour, not to absolute immutability of every file in the shared application process.
- Alternative (a second `@Configuration` with a second `SecurityFilterChain`) would require `@Order` coordination and risks filter chain interference; the single-chain approach is simpler.

**Filter order** (after change):
1. `BlacklistCheckFilter` — checks `jti` against `token_blacklist`
2. `JwtAuthFilter` — validates signature, sets `AuthContext` and `SecurityContextHolder`
3. `UsernamePasswordAuthenticationFilter` (Spring default, unused)

---

## R-009: Auth Audit Log

**Decision**: New `auth_audit_log` table (separate from `patient_audit_log`) with a new `AuthAuditService`. Same design principles: BIGSERIAL PK, TIMESTAMPTZ, no FK, no PHI/passwords.

**Columns**: `id` (BIGSERIAL PK), `timestamp` (TIMESTAMPTZ), `event_type` (VARCHAR 30), `actor_user_id` (VARCHAR 12), `target_user_id` (VARCHAR 12 nullable), `outcome` (VARCHAR 10), `ip_address` (VARCHAR 45 — IPv6-safe), `details` (TEXT nullable).

**Event types**: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `ACCOUNT_LOCKED`, `LOGOUT`, `TOKEN_REFRESH`, `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`.

**Why separate from `patient_audit_log`**: Different domain, different event types, different retention and compliance queries. Keeping them separate maintains the single-responsibility principle established in V3.

---

## R-010: Resilience4j Circuit Breaker Placement

**Decision**: Apply `@CircuitBreaker(name = "authModule")` to `AuthService.login()` method body — the DB access + password verification path.

**Rationale**:
- The `authModule` circuit breaker instance is already declared in `application.yml` and `CircuitBreakerConfig.java`.
- Per MEMORY.md: "Never `@CircuitBreaker` on filter methods — causes CGLIB proxy NPE on `GenericFilterBean.logger`."
- The circuit breaker protects against DB unavailability during login; token validation (`JwtAuthFilter`) and blacklist check (`BlacklistCheckFilter`) are fast in-memory operations and should not be wrapped.

---

## Resolved Unknowns Summary

| Unknown | Resolution |
|---|---|
| Password hashing algorithm and strength | BCrypt strength 12 |
| Blacklist storage | PostgreSQL `token_blacklist` table, jti PK |
| Blacklist check integration point | New `BlacklistCheckFilter` before `JwtAuthFilter` |
| Account lockout storage | `failed_attempts` + `locked_until` on `HospitalUser` entity |
| Token refresh strategy | New token; old remains valid until expiry |
| User ID format | `U + YYYY + seq` (mirroring Patient Module) |
| Seed admin mechanism | `ApplicationRunner` bean, `ADMIN_INITIAL_PASSWORD` env var |
| Next Flyway migration number | V5 |
| Nginx login rate limit | New `login_limit` zone + dedicated location block |
| Filter registration | Add `BlacklistCheckFilter` to existing `SecurityConfig.java` |
