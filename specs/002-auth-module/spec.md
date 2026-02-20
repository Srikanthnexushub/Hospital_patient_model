# Feature Specification: Auth Module

**Feature Branch**: `002-auth-module`
**Created**: 2026-02-20
**Status**: Draft
**Version**: 1.0.0
**Company**: Ai Nexus
**System**: Hospital Management System

---

## Overview

The Auth Module provides secure identity verification and access control for all hospital
staff interacting with the Hospital Management System. It replaces the temporary
development authentication stub (DevAuthController) with a production-grade solution
backed by a persistent user store.

Every staff member must authenticate before performing any operation. The Auth Module
is responsible for verifying identity, issuing access tokens, maintaining token validity,
and enforcing role-based access at the boundary. All downstream modules — including the
Patient Module — consume the tokens this module issues without modification.

The Auth Module shares the existing database and application runtime with the Patient
Module. No new infrastructure services are required.

---

## Scope

### In Scope

- Staff login with username and password
- Secure token issuance compatible with the Patient Module's token validation
- Token refresh to extend sessions without re-login
- Explicit logout with immediate token invalidation
- Current-user profile retrieval
- Administrator-managed staff account lifecycle (create, update, deactivate)
- Account lockout after repeated failed login attempts
- Audit trail for all authentication events (HIPAA compliance)
- Seed default administrator account via environment configuration

### Out of Scope

| Capability | Reason |
|---|---|
| OAuth2 / SSO / SAML | Future infrastructure module |
| Multi-factor authentication | Future security enhancement |
| Password reset via email | Notification Module dependency |
| Patient self-service portal auth | Patient Portal Module |
| Cloud identity providers | Future infrastructure module |
| Role creation / permission editing | Static roles inherited from Patient Module |

---

## User Roles & Permissions

| Operation | RECEPTIONIST | DOCTOR | NURSE | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Login | ✅ | ✅ | ✅ | ✅ |
| Refresh token | ✅ | ✅ | ✅ | ✅ |
| Logout | ✅ | ✅ | ✅ | ✅ |
| View own profile | ✅ | ✅ | ✅ | ✅ |
| Create staff account | ❌ | ❌ | ❌ | ✅ |
| List staff accounts | ❌ | ❌ | ❌ | ✅ |
| View any staff account | ❌ | ❌ | ❌ | ✅ |
| Update staff account | ❌ | ❌ | ❌ | ✅ |
| Deactivate staff account | ❌ | ❌ | ❌ | ✅ |

**Rules**:
- All non-public operations require a valid, non-revoked token.
- Role enforcement occurs server-side on every request. Client-side checks are cosmetic only.
- A deactivated staff account cannot log in.
- An admin cannot deactivate their own account.

---

## User Scenarios & Testing

### User Story 1 — Staff Login (Priority: P1)

A hospital staff member opens the login page, enters their username and password,
and receives an access token that grants them entry to the system. The token encodes
their identity and role so all downstream modules can enforce access rules without
an additional database call.

If the credentials are wrong, the system rejects the request and increments a failure
counter. After five consecutive failures the account is locked for 15 minutes to
prevent brute-force attacks. A locked-account response differs from a wrong-password
response so the UI can show an appropriate message.

**Why this priority**: No staff member can use any other part of the system without a
valid token. This story is the single prerequisite for all other functionality and for
the Patient Module to operate correctly in production.

**Independent Test**: A staff member with valid credentials can log in and immediately
use the resulting token to search patients — end-to-end value is delivered by this
story alone.

**Acceptance Scenarios**:

1. **Given** a staff member with a valid, active account, **When** they submit correct
   credentials, **Then** the system returns a token containing their user ID, username,
   and role, along with the token expiry time.

2. **Given** a staff member, **When** they submit an incorrect password, **Then** the
   system returns an authentication failure response and increments the failure counter.

3. **Given** a staff member who has failed login five consecutive times, **When** they
   attempt to log in again (correct or incorrect password), **Then** the system returns
   a locked-account response and no token is issued.

4. **Given** a locked account whose lockout period has elapsed, **When** the staff
   member submits correct credentials, **Then** the lockout is cleared, the failure
   counter resets, and a token is issued normally.

5. **Given** a deactivated staff account, **When** the staff member submits correct
   credentials, **Then** the system returns an authentication failure response without
   revealing the reason for refusal.

6. **Given** a login request with a missing username or missing password, **When** the
   request is submitted, **Then** the system returns a validation error without
   attempting credential verification.

---

### User Story 2 — Token Refresh (Priority: P2)

An authenticated staff member whose token is approaching expiry requests a new token
without re-entering credentials. This prevents mid-shift interruption during long
working sessions. The system issues a fresh token only if the current token is still
valid and has not been revoked; it does not extend revoked or expired tokens.

**Why this priority**: Without refresh, staff must re-login every eight hours. During
busy hospital shifts this causes operational disruption. Refresh is the primary
mechanism for maintaining continuity of access.

**Independent Test**: A staff member can refresh their token and use the new token to
access patient records — tested without any other new auth story.

**Acceptance Scenarios**:

1. **Given** an authenticated staff member with a valid, non-revoked token, **When**
   they request a refresh, **Then** the system returns a new token with a fresh expiry
   and the original token remains usable until its own expiry.

2. **Given** a revoked (logged-out) token, **When** a refresh is requested with that
   token, **Then** the system returns an authentication failure response and no new
   token is issued.

3. **Given** an expired token, **When** a refresh is requested, **Then** the system
   returns an authentication failure response and the staff member must log in again.

---

### User Story 3 — Logout (Priority: P2)

A staff member explicitly logs out. The system immediately invalidates the token so it
cannot be reused, even if it has not yet expired. This protects against token theft on
shared workstations or when a session must be forcibly ended.

**Why this priority**: Logout is a fundamental security control. Without it, tokens
remain valid for their full lifetime after a user leaves the workstation, creating an
unacceptable HIPAA exposure window.

**Independent Test**: A staff member logs out, then attempts to access patient records
with the old token and receives an authentication failure — testable as a standalone
security scenario.

**Acceptance Scenarios**:

1. **Given** an authenticated staff member, **When** they log out, **Then** the system
   records the token as revoked and returns a success response.

2. **Given** a logged-out token, **When** any subsequent request carries that token,
   **Then** the system returns an authentication failure response.

3. **Given** an already-revoked token, **When** a logout request is made with it,
   **Then** the system returns an authentication failure response (idempotent — no
   double-revocation side-effects).

---

### User Story 4 — Current User Profile (Priority: P3)

An authenticated staff member retrieves their own profile. The frontend uses this
to display the user's name, role badge, and department in the navigation bar without
embedding that information in the token payload beyond the minimum required claims.

**Why this priority**: The Patient Module already works without this endpoint. It is a
quality-of-life feature for the frontend that does not block any other story.

**Independent Test**: A logged-in user calls the profile endpoint and the response
populates the navigation header — testable as a single API call.

**Acceptance Scenarios**:

1. **Given** an authenticated staff member, **When** they request their profile, **Then**
   the system returns their user ID, username, role, email, department, and the time
   of their last successful login.

2. **Given** a revoked or expired token, **When** the profile endpoint is called,
   **Then** the system returns an authentication failure response.

---

### User Story 5 — Staff Account Management (Priority: P3)

An ADMIN staff member manages the lifecycle of all hospital staff accounts. They can
onboard new staff, update details (name, email, department, role), and deactivate
accounts when staff leave. Deactivation is a soft-delete: historical audit records
remain intact and the account can be reactivated if needed. An ADMIN cannot deactivate
their own account to prevent accidental lockout.

**Why this priority**: Essential for real-world operations but does not affect the token
validation path already working through the Patient Module. A single seed ADMIN account
is sufficient to bootstrap access before this story is complete.

**Independent Test**: An ADMIN creates a new RECEPTIONIST account, that receptionist
logs in and searches patients — end-to-end provisioning is testable independently.

**Acceptance Scenarios**:

1. **Given** an ADMIN, **When** they create a new staff account with valid details,
   **Then** the account is saved, a unique user ID is generated, and the new user can
   log in with the provided credentials.

2. **Given** an ADMIN, **When** they attempt to create an account with a username that
   already exists, **Then** the system returns a conflict error and no account is
   created.

3. **Given** an ADMIN, **When** they update a staff member's role, **Then** subsequent
   tokens issued to that staff member carry the new role.

4. **Given** an ADMIN, **When** they deactivate a staff account, **Then** the account
   status becomes inactive and the deactivated user can no longer log in.

5. **Given** an ADMIN, **When** they attempt to deactivate their own account, **Then**
   the system returns a forbidden error and the account remains active.

6. **Given** a non-ADMIN staff member, **When** they attempt any user management
   operation, **Then** the system returns an authorization failure response.

7. **Given** an ADMIN, **When** they list staff accounts, **Then** the system returns a
   paginated list including each account's user ID, username, role, department, status,
   and last login time.

---

### Edge Cases

- What happens when the JWT secret is rotated while active tokens exist?
  → Tokens signed with the old secret become invalid; all staff must re-login. This is
  acceptable for a planned maintenance event and is documented in operational runbooks.

- What happens if the token blacklist table grows unbounded?
  → Blacklist entries with an expiry in the past can be purged; they are no longer
  relevant since the original tokens would be expired anyway.

- What happens if a staff member's role is changed while they hold an active token?
  → The token retains the role at issuance until it expires or is refreshed. The new
  role takes effect on the next token (refresh or new login). This is the standard
  behaviour for stateless tokens and is acceptable for a hospital shift change.

- What happens when the system starts up without the seed ADMIN password configured?
  → Application startup fails with a clear configuration error. The system refuses to
  run without a secure initial administrator credential.

- What happens if a refresh is requested within seconds of a new token being issued?
  → The system issues another new token; refresh is not rate-limited at the token level
  (the global IP rate limit applies).

---

## Requirements

### Functional Requirements

#### Authentication

- **FR-001**: System MUST accept a username and password pair as login credentials and
  return an access token on successful verification.
- **FR-002**: System MUST store staff passwords using a one-way adaptive hashing
  algorithm with a work factor sufficient to resist offline brute-force attacks.
- **FR-003**: System MUST issue access tokens that are cryptographically signed and
  contain at minimum: user ID, username, role, issue time, and expiry time.
- **FR-004**: The issued token's claims and signature MUST be compatible with the
  existing Patient Module token validation — no changes to that module are permitted.
- **FR-005**: System MUST allow configuring token expiry duration via environment
  variable with a documented default of 8 hours.
- **FR-006**: System MUST increment a per-account failure counter on each unsuccessful
  login attempt.
- **FR-007**: System MUST lock an account for 15 minutes after 5 consecutive failed
  login attempts. The lockout duration MUST be configurable via environment variable.
- **FR-008**: System MUST reset the failure counter to zero upon a successful login.
- **FR-009**: System MUST return a distinct response code for a locked account versus
  invalid credentials.
- **FR-010**: System MUST reject login attempts for deactivated accounts.

#### Token Lifecycle

- **FR-011**: System MUST provide a token refresh operation that accepts a valid,
  non-revoked token and returns a new token with a fresh expiry.
- **FR-012**: System MUST reject refresh requests that carry a revoked token.
- **FR-013**: System MUST reject refresh requests that carry an expired token.
- **FR-014**: System MUST provide a logout operation that immediately revokes the
  presented token by recording it in a persistent blacklist.
- **FR-015**: System MUST reject any request carrying a revoked token, regardless of
  the token's expiry time.
- **FR-016**: The token blacklist MUST be persisted across application restarts.
- **FR-017**: Revoked tokens whose original expiry has passed MAY be purged from the
  blacklist without loss of security.

#### User Profile

- **FR-018**: Authenticated staff members MUST be able to retrieve their own profile
  including: user ID, username, role, email address, department, and last login time.

#### Staff Account Management

- **FR-019**: ADMIN staff MUST be able to create new staff accounts by providing:
  username, initial password, role, email address, and department.
- **FR-020**: System MUST enforce username uniqueness across all accounts, active and
  inactive.
- **FR-021**: ADMIN staff MUST be able to update the following fields on any account:
  email address, department, and role.
- **FR-022**: ADMIN staff MUST be able to deactivate any staff account except their own.
- **FR-023**: System MUST prevent a deactivated account from logging in.
- **FR-024**: System MUST prevent an ADMIN from deactivating their own account.
- **FR-025**: ADMIN staff MUST be able to retrieve a paginated list of all staff
  accounts with filtering by role and status.
- **FR-026**: ADMIN staff MUST be able to retrieve the full profile of any individual
  staff account.

#### Seed Account

- **FR-027**: System MUST create an initial ADMIN account on first startup using
  credentials supplied via environment variables. If the required password variable is
  absent or empty, the system MUST refuse to start.
- **FR-028**: The seed account MUST NOT use any hardcoded password. The initial password
  MUST be sourced entirely from configuration.

#### Audit & Compliance

- **FR-029**: System MUST write an immutable audit log entry for every login attempt
  (recording outcome: success or failure) without recording the submitted password.
- **FR-030**: System MUST write an audit log entry for every logout.
- **FR-031**: System MUST write an audit log entry for every token refresh.
- **FR-032**: System MUST write an audit log entry for every staff account creation,
  update, and deactivation.
- **FR-033**: Audit log entries MUST record: event type, timestamp (UTC), actor user ID,
  target user ID (where applicable), and outcome.
- **FR-034**: Audit log entries MUST NOT contain passwords, plaintext secrets, or any
  Protected Health Information.

#### Security

- **FR-035**: All authentication endpoints MUST be accessible without a prior token
  (public routes). All other Auth Module endpoints MUST require a valid token.
- **FR-036**: The system MUST sign tokens using a secret read exclusively from
  environment configuration. No secret value may appear in source code or migration
  scripts.
- **FR-037**: The login endpoint MUST be subject to a stricter rate limit than other
  endpoints to slow brute-force attempts. The exact limit MUST be configurable.

### Key Entities

- **Staff User**: Represents a hospital staff member who accesses the system. Has an
  identity (user ID, username), a credential (hashed password), an organisational
  position (role, department, email), a lifecycle status (active / inactive), and a
  security state (failure counter, lockout expiry, last login time).

- **Access Token**: A signed, self-contained credential issued to an authenticated staff
  member. Carries the minimum claims needed for downstream authorisation (user ID,
  username, role). Has a finite validity window.

- **Token Blacklist Entry**: A record of a token that has been explicitly revoked before
  its natural expiry. Identified by a unique token identifier. Retained until the
  original token's expiry time passes.

- **Auth Audit Log Entry**: An immutable record of a security-relevant event. Records
  who did what, when, and the outcome. Never modified or deleted.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: A staff member can complete the login flow (enter credentials, receive
  token) in under 2 seconds under normal operating conditions.
- **SC-002**: Token validation adds no perceptible latency to downstream requests — the
  system validates credentials in memory without a database call on every request.
- **SC-003**: After 5 consecutive failed login attempts, the account is locked within
  the same response cycle — no additional request is needed to trigger the lockout.
- **SC-004**: A logout request invalidates the token immediately — any request using
  that token submitted within one second of logout is rejected.
- **SC-005**: All 4 staff roles (RECEPTIONIST, DOCTOR, NURSE, ADMIN) can log in and
  receive tokens that are accepted by the Patient Module without any modification to
  that module.
- **SC-006**: An ADMIN can onboard a new staff member (create account) and that member
  can log in within the same session.
- **SC-007**: The system starts up and issues working tokens from a cold state (no
  pre-seeded data) using only environment variable configuration — zero manual database
  steps required.
- **SC-008**: Every authentication event (login, logout, refresh, account change) is
  auditable — an ADMIN can reconstruct the full access history for any staff member.
- **SC-009**: Removing the development authentication stub causes no change in observed
  behaviour for any existing Patient Module test or feature.
- **SC-010**: The system sustains the same peak load (100,000+ requests per hour) as
  the Patient Module — auth token validation does not become the bottleneck.

---

## Dependencies

- **Patient Module (001-patient-module)**: Frozen and must not be modified. Provides
  the JWT validation filter, `AuthContext`, `RoleGuard`, and `SecurityConfig` that
  all issued tokens must satisfy.
- **Database**: Shared PostgreSQL instance. Auth Module adds new tables via sequential
  Flyway migrations (V5 onwards) without altering existing Patient Module tables.
- **Environment Configuration**: `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_INITIAL_PASSWORD`,
  `APP_JWT_EXPIRATION_HOURS`, `APP_JWT_REFRESH_EXPIRATION_HOURS`,
  `AUTH_LOCKOUT_MAX_ATTEMPTS`, `AUTH_LOCKOUT_DURATION_MINUTES`,
  `AUTH_LOGIN_RATE_LIMIT` — all must be provided via environment; none have insecure
  defaults.

---

## Assumptions

- **Single-role model**: Each staff account holds exactly one role (matching Patient
  Module's single-role JWT claim). Multi-role support is a future enhancement.
- **Shared secret signing**: Both the Auth Module (issuer) and Patient Module (validator)
  read the same `JWT_SECRET` environment variable. Key rotation is a planned-downtime
  operation.
- **Same application process**: Auth Module code ships in the same Spring Boot
  application as the Patient Module. No separate auth microservice is introduced.
- **Password policy**: Minimum 8 characters, at least one uppercase letter, one
  lowercase letter, and one digit. Configurable in future; hardened defaults for now.
- **Refresh token strategy**: A new access token is issued on refresh; there is no
  separate long-lived refresh token. The refresh endpoint requires a valid (non-expired,
  non-revoked) access token.
- **Account reactivation**: Reactivating a deactivated account is an update operation
  (PATCH) performed by ADMIN — same endpoint as other account updates.
- **Lockout auto-clear**: Lockouts expire automatically after the configured duration;
  no admin intervention is required to unlock a staff account.
