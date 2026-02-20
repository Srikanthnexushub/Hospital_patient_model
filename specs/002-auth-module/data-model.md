# Data Model: Auth Module

**Branch**: `002-auth-module` | **Date**: 2026-02-20
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

---

## Overview

The Auth Module introduces three new database tables into the shared PostgreSQL instance.
Existing Patient Module tables (V1–V4) are untouched.

```
hospital_users           — staff accounts (identity, credentials, lifecycle)
staff_id_sequences       — year-based ID counter for hospital_users
token_blacklist          — revoked JWT entries
auth_audit_log           — immutable record of every auth event
```

All four tables are created via sequential Flyway migrations V5–V7. The seed ADMIN
account is inserted by an `ApplicationRunner` bean (not a SQL migration).

---

## Entity: HospitalUser

**Table**: `hospital_users`
**Migration**: V5
**Purpose**: Represents a hospital staff member who can authenticate and access the system.

### Fields

| Column | Java Field | Type | Nullable | Constraints | Notes |
|---|---|---|---|---|---|
| `user_id` | `userId` | VARCHAR(12) | NOT NULL | PK | Format: `U` + 4-digit year + 3-digit seq (e.g. `U2026001`). Immutable after creation. |
| `username` | `username` | VARCHAR(50) | NOT NULL | UNIQUE | Login identifier. Case-insensitive comparison enforced in service layer. Immutable after creation. |
| `password_hash` | `passwordHash` | VARCHAR(72) | NOT NULL | — | BCrypt output (always 60 chars; 72 = safe headroom). Never returned in any API response. Never logged. |
| `role` | `role` | VARCHAR(20) | NOT NULL | CHECK (RECEPTIONIST, DOCTOR, NURSE, ADMIN) | Single role per account. |
| `email` | `email` | VARCHAR(100) | NULL | — | Optional. Stored lowercase. RFC 5322-simplified format validated. |
| `department` | `department` | VARCHAR(100) | NULL | — | Optional. Free text. |
| `status` | `status` | VARCHAR(10) | NOT NULL | CHECK (ACTIVE, INACTIVE) | Default: ACTIVE. |
| `failed_attempts` | `failedAttempts` | INTEGER | NOT NULL | DEFAULT 0, CHECK ≥ 0 | Incremented on each login failure. Reset to 0 on success. |
| `locked_until` | `lockedUntil` | TIMESTAMPTZ | NULL | — | NULL = not locked. Set to NOW() + lockout duration when `failed_attempts` reaches threshold. Auto-clears when timestamp passes. |
| `last_login_at` | `lastLoginAt` | TIMESTAMPTZ | NULL | — | Updated on every successful login. |
| `created_at` | `createdAt` | TIMESTAMPTZ | NOT NULL | DEFAULT NOW() | System-set at creation. Immutable. |
| `created_by` | `createdBy` | VARCHAR(50) | NOT NULL | DEFAULT 'SYSTEM' | Username of creating user. 'SYSTEM' for seed account. |
| `updated_at` | `updatedAt` | TIMESTAMPTZ | NOT NULL | DEFAULT NOW() | Updated on every write. |
| `updated_by` | `updatedBy` | VARCHAR(50) | NOT NULL | DEFAULT 'SYSTEM' | Username of last updater. |
| `version` | `version` | INTEGER | NOT NULL | DEFAULT 0 | JPA `@Version` for optimistic locking (prevents lockout counter race conditions). |

### Indexes

| Index Name | Columns | Type | Purpose |
|---|---|---|---|
| `pk_hospital_users` | `user_id` | PRIMARY KEY | Identity lookup |
| `uq_hospital_users_username` | `username` | UNIQUE | Login lookup, uniqueness enforcement |
| `idx_hospital_users_status` | `status` | B-tree | Admin list filter by status |
| `idx_hospital_users_role` | `role` | B-tree | Admin list filter by role |
| `idx_hospital_users_locked_until` | `locked_until` | B-tree PARTIAL (WHERE NOT NULL) | Efficient lockout expiry query |

### State Transitions

```
ACTIVE ──[ADMIN deactivates]──→ INACTIVE
INACTIVE ──[ADMIN reactivates]──→ ACTIVE

Failed login counter:
0 → 1 → 2 → 3 → 4 → 5 (lock) → 0 (success or lockout expiry)
```

### Validation Rules

- `username`: 3–50 characters; alphanumeric, underscores, hyphens only.
- `password` (at creation): minimum 8 characters; at least one uppercase, one lowercase, one digit. Validated on raw password before hashing; hash is stored, raw is discarded.
- `email`: When provided, must match RFC 5322 simplified format. Stored lowercase.
- `role`: Must be exactly one of `RECEPTIONIST`, `DOCTOR`, `NURSE`, `ADMIN`.
- `department`: Max 100 characters. Free text when provided.

---

## Entity: StaffIdSequence

**Table**: `staff_id_sequences`
**Migration**: V5
**Purpose**: Year-based atomic counter for generating sequential, human-readable staff user IDs. Mirrors `patient_id_sequences`.

### Fields

| Column | Java Field | Type | Nullable | Constraints | Notes |
|---|---|---|---|---|---|
| `year` | `year` | INTEGER | NOT NULL | PK | Calendar year (e.g. 2026). |
| `last_sequence` | `lastSequence` | INTEGER | NOT NULL | DEFAULT 0 | Last-used sequence number for the year. Incremented under `SELECT FOR UPDATE`. |

### ID Generation Rules

- Format: `U` + 4-digit year + zero-padded sequence (min 3 digits, expands past 999)
- Examples: `U2026001`, `U2026012`, `U2026999`, `U20261000`
- `SELECT FOR UPDATE` on the row for the current year; increment and save atomically
- Row is auto-created on first use of a new year (INSERT … ON CONFLICT DO UPDATE or explicit check)
- IDs are never reused, even for deactivated accounts

---

## Entity: TokenBlacklist

**Table**: `token_blacklist`
**Migration**: V6
**Purpose**: Stores the JWT ID (`jti`) of every explicitly revoked token until the token's natural expiry. Checked on every authenticated request by `BlacklistCheckFilter`.

### Fields

| Column | Java Field | Type | Nullable | Constraints | Notes |
|---|---|---|---|---|---|
| `jti` | `jti` | VARCHAR(36) | NOT NULL | PK | UUID4 string from the JWT `jti` claim (e.g. `550e8400-e29b-41d4-a716-446655440000`). |
| `user_id` | `userId` | VARCHAR(12) | NOT NULL | — | Staff user ID who owned the token. NOT a FK (retention independence). |
| `expires_at` | `expiresAt` | TIMESTAMPTZ | NOT NULL | — | Copied from the JWT `exp` claim. Used by cleanup job. |
| `revoked_at` | `revokedAt` | TIMESTAMPTZ | NOT NULL | DEFAULT NOW() | When the token was revoked. |

### Indexes

| Index Name | Columns | Type | Purpose |
|---|---|---|---|
| `pk_token_blacklist` | `jti` | PRIMARY KEY | O(1) blacklist check on every request |
| `idx_token_blacklist_expires_at` | `expires_at` | B-tree | Efficient scheduled purge (`DELETE WHERE expires_at < NOW()`) |
| `idx_token_blacklist_user_id` | `user_id` | B-tree | Future: revoke all sessions for a user |

### Lifecycle

- Entry created on logout (`POST /api/v1/auth/logout`)
- Entry checked on every authenticated request (via `BlacklistCheckFilter`)
- Entries with `expires_at < NOW()` are purged every 15 minutes by `BlacklistCleanupService`
- Table is bounded: at steady state (500 staff, 8h token lifetime, ~2 logouts/shift), max ~2,000 rows

---

## Entity: AuthAuditLog

**Table**: `auth_audit_log`
**Migration**: V7
**Purpose**: Immutable record of every authentication event. Separate from `patient_audit_log` (different domain, different compliance queries). Never modified or deleted.

### Fields

| Column | Java Field | Type | Nullable | Constraints | Notes |
|---|---|---|---|---|---|
| `id` | `id` | BIGSERIAL | NOT NULL | PK | Auto-generated. |
| `timestamp` | `timestamp` | TIMESTAMPTZ | NOT NULL | DEFAULT NOW() | Event time (UTC). |
| `event_type` | `eventType` | VARCHAR(30) | NOT NULL | CHECK (see below) | Type of authentication event. |
| `actor_user_id` | `actorUserId` | VARCHAR(12) | NOT NULL | — | Staff user ID performing the action. NOT a FK. |
| `target_user_id` | `targetUserId` | VARCHAR(12) | NULL | — | Staff user ID affected (for admin operations). NULL for self-actions. |
| `outcome` | `outcome` | VARCHAR(10) | NOT NULL | CHECK (SUCCESS, FAILURE) | Result of the event. |
| `ip_address` | `ipAddress` | VARCHAR(45) | NULL | — | Client IP address (IPv4 or IPv6). Extracted from `X-Forwarded-For` header. |
| `details` | `details` | TEXT | NULL | — | Additional context. MUST NOT contain passwords, PHI, or secrets. |

### Event Types

| Value | Trigger |
|---|---|
| `LOGIN_SUCCESS` | Successful login |
| `LOGIN_FAILURE` | Invalid credentials or deactivated account |
| `ACCOUNT_LOCKED` | Account locked after repeated failures |
| `LOGOUT` | Explicit token revocation |
| `TOKEN_REFRESH` | Token refresh issued |
| `USER_CREATED` | New staff account created by ADMIN |
| `USER_UPDATED` | Staff account fields updated by ADMIN |
| `USER_DEACTIVATED` | Staff account deactivated by ADMIN |

### Indexes

| Index Name | Columns | Type | Purpose |
|---|---|---|---|
| `pk_auth_audit_log` | `id` | PRIMARY KEY | Identity |
| `idx_auth_audit_actor` | `actor_user_id` | B-tree | Per-user event history |
| `idx_auth_audit_timestamp` | `timestamp DESC` | B-tree | Chronological queries |
| `idx_auth_audit_event_type` | `event_type` | B-tree | Filter by event type |

---

## Flyway Migration Plan

| Version | File | Creates |
|---|---|---|
| V5 | `V5__create_hospital_users.sql` | `hospital_users`, `staff_id_sequences`, all constraints and indexes |
| V6 | `V6__create_token_blacklist.sql` | `token_blacklist`, all constraints and indexes |
| V7 | `V7__create_auth_audit_log.sql` | `auth_audit_log`, all constraints and indexes |
| — | `AdminSeeder.java` (ApplicationRunner) | Inserts seed ADMIN row into `hospital_users` on first startup |

---

## DTO Layer

All API boundaries use DTOs. Entities are never returned from controllers.

| DTO | Direction | Fields |
|---|---|---|
| `LoginRequest` | IN | `username` (String), `password` (String) |
| `TokenResponse` | OUT | `token` (String), `username` (String), `role` (String), `userId` (String), `expiresAt` (Instant) |
| `UserProfileResponse` | OUT | `userId`, `username`, `role`, `email`, `department`, `lastLoginAt` (Instant) |
| `CreateUserRequest` | IN | `username`, `password`, `role`, `email` (opt), `department` (opt) |
| `UpdateUserRequest` | IN | `email` (opt), `department` (opt), `role` (opt) — all nullable/optional PATCH fields |
| `UserSummaryResponse` | OUT (list) | `userId`, `username`, `role`, `department`, `status`, `lastLoginAt` |
| `UserDetailResponse` | OUT (single) | All `UserSummaryResponse` fields + `email`, `createdAt`, `createdBy`, `failedAttempts` |

---

## JWT Token Claims (Canonical — must not change)

These claims are consumed by the frozen `JwtAuthFilter` in the Patient Module:

| Claim | Type | Value | Note |
|---|---|---|---|
| `sub` | String | `userId` (e.g. `U2026001`) | Standard JWT subject |
| `username` | String | login username | Custom claim |
| `role` | String | one of `RECEPTIONIST`, `DOCTOR`, `NURSE`, `ADMIN` | Custom claim — single string, not array |
| `jti` | String | UUID4 string | Standard JWT ID — new in this module |
| `iat` | Long | milliseconds since epoch | Standard issued-at |
| `exp` | Long | milliseconds since epoch | Standard expiry |

**Algorithm**: HS256 (`HMAC-SHA256`)
**Secret**: `${JWT_SECRET}` environment variable (min 32 chars, no default in production profile)

---

## Relationship Diagram

```
hospital_users
  │
  ├──[created by]──→ token_blacklist.user_id  (NOT FK, loose coupling)
  ├──[created by]──→ auth_audit_log.actor_user_id  (NOT FK)
  └──[year]──→ staff_id_sequences.year  (used by StaffIdGeneratorService only)

patients  (existing — UNTOUCHED)
  └──[created by username]──→ patient_audit_log.performed_by  (string, no FK)
```

All cross-table references use string IDs (no FK constraints to the auth tables) to preserve HIPAA retention independence — the same design decision made in `V3__create_patient_audit_log.sql`.
