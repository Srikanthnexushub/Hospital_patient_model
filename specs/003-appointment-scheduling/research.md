# Research: Appointment Scheduling Module

**Branch**: `003-appointment-scheduling`
**Phase**: 0 — Research & Decision Log
**Date**: 2026-02-20

---

## Decision 1: Conflict Detection Strategy

**Decision**: Use JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the overlap-detection query (equivalent to `SELECT FOR UPDATE`) within the existing `@Transactional` booking method.

**Rationale**: The existing stack already uses PostgreSQL 15 and Spring Data JPA with Hibernate 6. Pessimistic locking on the overlap query is the most targeted approach — it locks only the doctor's appointment rows for the queried time window, not unrelated rows. It satisfies the spec's "atomic conflict detection" requirement with zero risk of false positives.

**Alternatives considered**:
- SERIALIZABLE isolation level — rejects more than necessary (any concurrent write to the appointments table fails), causing excessive retry overhead. Overkill for a single-row conflict scenario.
- Application-level optimistic retry loop — not atomic. A race between two concurrent bookings could both pass the check before either commits.
- DB-level EXCLUDE constraint (PostgreSQL range type) — powerful but requires the `btree_gist` extension and adds DB schema complexity that is harder to test and document. PESSIMISTIC_WRITE achieves identical safety with familiar JPA patterns.

**Implementation**: `AppointmentRepository.findOverlappingAppointments()` annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and called inside `AppointmentService.bookAppointment()` which is `@Transactional`.

---

## Decision 2: Clinical Notes Encryption (AES-256)

**Decision**: Use JDK built-in `javax.crypto` with `AES/GCM/NoPadding` via a JPA `AttributeConverter` (`NotesEncryptionConverter`). A random 12-byte IV is generated per encryption; the IV is prepended to the ciphertext and the combined bytes are stored as Base64 in `TEXT` columns. The 256-bit key is read from env var `APP_NOTES_ENCRYPTION_KEY` (base64-encoded 32-byte key).

**Rationale**:
- **No new Maven dependency** — AES/GCM is in the Java standard library (JDK 7+); the project already targets Java 17.
- **Authenticated encryption** — GCM mode provides both confidentiality and integrity (tamper detection), which is stronger than AES/CBC.
- **Transparent to the entity layer** — `@Convert(converter = NotesEncryptionConverter.class)` on the encrypted fields means the entity uses plain `String`; the DB stores ciphertext. Zero leakage risk in Hibernate query logs or JPA debug output.
- **Per-field granularity** — Only the specific `TEXT` columns on `clinical_notes` that contain PHI are encrypted. Non-PHI metadata (followUpRequired, followUpDays) are stored in plain.

**Alternatives considered**:
- Postgres `pgcrypto` extension — adds infrastructure dependency; key management in DB is harder to audit.
- Jasypt / Bouncy Castle — adds new dependencies; unnecessary when JDK provides everything needed.
- Whole-row encryption — over-broad; complicates queries on non-sensitive columns.

**Implementation**: `NotesEncryptionConverter implements AttributeConverter<String, String>`. A `NotesEncryptionConfig` `@Configuration` class reads `APP_NOTES_ENCRYPTION_KEY` from the environment and provides the `SecretKeySpec` as a `@Bean`. The converter is injected via Spring (using `@Autowired` on the converter + marking it `@Component`).

**Key format**: 32 random bytes, base64-encoded, stored in env. Generate with: `openssl rand -base64 32`

---

## Decision 3: Appointment ID Generation

**Decision**: Mirror the `PatientIdGeneratorService` pattern exactly — a new `appointment_id_sequences` table (PK: `year`, column: `last_sequence`) with `SELECT FOR UPDATE` via a custom JPQL query. Format: `APT` + 4-digit year + 4-digit zero-padded seq (e.g., `APT20260001`). Sequence resets to `0001` each year.

**Rationale**: The existing pattern is proven in production (Module 1) with 12 patient IDs created without collision. Using the same approach reduces cognitive load and testing surface. The `VARCHAR(14)` column handles growth beyond 9999 IDs/year gracefully (ID becomes `APT202610000` = 12 chars, well within 14).

**Alternatives considered**:
- PostgreSQL `SEQUENCE` — atomic but tightly coupled to DB; harder to unit test and doesn't support the year-reset requirement cleanly.
- UUID — doesn't match the specified format; not human-readable for hospital staff.

---

## Decision 4: Doctor Role Filtering in Queries

**Decision**: Service-layer enforcement using `AuthContext`. When `role == DOCTOR`, the service injects `ctx.getUserId()` as the `doctorId` filter on all list/search queries. Repository receives the filter as an explicit parameter — no custom security annotations or JPQL interceptors.

**Rationale**: Consistent with how `RoleGuard` works today — role enforcement is purely in the service layer. The repository simply accepts optional filter parameters; the service decides what values to pass. This is independently testable: unit tests can verify the service passes the correct doctorId for DOCTOR role.

**Implementation**: `AppointmentService.listAppointments()` checks `if ("DOCTOR".equals(ctx.getRole()))` and forces `doctorId = ctx.getUserId()` before delegating to the repository.

---

## Decision 5: Doctor Availability Slot Computation

**Decision**: Pure Java computation in `DoctorAvailabilityService`. The service queries all non-cancelled/no-show appointments for the doctor on the requested date, then generates a 20-slot grid (08:00–18:00 at 30-minute intervals) and marks each slot OCCUPIED if any appointment's `[startTime, endTime)` interval overlaps the slot's `[slotStart, slotEnd)`.

**Rationale**: 20 slots is a trivially small in-memory operation — O(20 × N) where N is the number of appointments for that doctor on that day (typically ≤ 20). No caching required to hit the 100 ms p95 target.

**Overlap condition**: Slot is OCCUPIED if any appointment satisfies `appointment.startTime < slotEnd && appointment.endTime > slotStart`.

---

## Decision 6: One-to-One Clinical Notes (Upsert)

**Decision**: `ClinicalNotes` has `appointmentId` as its `@Id` (same value as the parent `Appointment`). POST to `/appointments/{id}/notes` checks if a note record exists; if yes, updates in place; if no, inserts. This is "upsert" semantics — a single endpoint for both create and update.

**Rationale**: Clinical documentation for a single appointment is a single document. Forcing the client to know whether to use POST vs PUT adds unnecessary complexity. Using `appointmentId` as the PK eliminates a surrogate key column and makes the one-to-one relationship self-documenting at the DB level.

---

## Decision 7: Immutable Audit Log

**Decision**: New `appointment_audit_log` table mirroring `patient_audit_log` — `BIGSERIAL` PK, no FK to `appointments` (HIPAA retention independence), `@GeneratedValue(IDENTITY)` in the entity. `AppointmentAuditService` writes one entry per mutation, called within the enclosing `@Transactional` scope.

**Rationale**: Identical to Module 1's proven pattern. Audit entries survive appointment updates or even hypothetical future data cleanup. Append-only enforcement at the service layer (no UPDATE/DELETE ever called on this table).

---

## Decision 8: Frontend Architecture

**Decision**: New `appointmentApi.js` with all 11 appointment-related API functions. New `useAppointments.js` TanStack Query v5 hooks. Four new page components. No changes to existing pages, hooks, or API files.

**Rationale**: Follows the existing Module 1 pattern exactly (`patientApi.js` + `usePatients.js` + page components). Zero risk of breaking existing Patient pages.

---

## Decision 9: No New Maven Dependencies

**Decision**: The appointment module requires zero new Maven dependencies.

| Need | Resolved By |
|------|-------------|
| AES-256 encryption | JDK 17 `javax.crypto` |
| Pessimistic locking | Spring Data JPA `@Lock` |
| Time slot arithmetic | `java.time.LocalTime` |
| ID generation | Existing sequence pattern |
| MapStruct mapping | Already in pom.xml |
| Audit logging | Same `AppointmentAuditService` pattern |

---

## Decision 10: SecurityConfig — No Changes Needed

**Decision**: The existing `SecurityConfig` uses `.anyRequest().authenticated()` after permitting only `/api/v1/auth/login`, Actuator, and Swagger. All new `/api/v1/appointments/**` and `/api/v1/doctors/**` endpoints are automatically protected by the JWT filter chain without any SecurityConfig modification.

**Rationale**: New endpoints fall through to `.anyRequest().authenticated()` naturally. Role enforcement is handled at the service layer by `RoleGuard`, not at the SecurityConfig level.

---

## Resolved Clarifications

No `NEEDS CLARIFICATION` markers existed in spec.md. All design decisions derived from existing codebase patterns and spec requirements.

## Flyway Migration Plan

| Version | Table | Purpose |
|---------|-------|---------|
| V8 | `appointments` | Core scheduling table |
| V9 | `appointment_id_sequences` | Sequence counter for APT IDs |
| V10 | `appointment_audit_log` | Immutable audit trail |
| V11 | `clinical_notes` | Encrypted clinical documentation |
