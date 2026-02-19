# Research: Patient Module

**Feature**: `001-patient-module`
**Date**: 2026-02-19
**Status**: Complete — all decisions resolved

---

## 1. Atomic Patient ID Generation

**Decision**: Use a dedicated `patient_id_sequences` table with a `SELECT ... FOR UPDATE`
pessimistic row lock per calendar year.

**Rationale**: PostgreSQL `SERIAL` / `SEQUENCE` objects reset or are global. A dedicated
table allows per-year reset and the `FOR UPDATE` lock guarantees exactly one thread
increments the counter at a time — safe under >100,000 req/hr concurrent load. Each
registration obtains the lock, increments `last_sequence`, commits, and releases. Lock
contention is negligible because registration is a low-frequency write relative to reads.

**Alternatives considered**:
- `SEQUENCE` per year: requires dynamic DDL at year boundary → rejected (operational complexity)
- UUID patient ID: violates spec requirement for `P2026001` format → rejected
- Application-level counter (Redis): additional dependency; complexity vs. need → rejected

---

## 2. Database Migrations — Flyway vs. Liquibase

**Decision**: Flyway with versioned SQL migration scripts (`V1__`, `V2__`, etc.)

**Rationale**: Flyway SQL migrations are plain SQL — readable, reviewable, and compatible
with the PostgreSQL-specific DDL in the spec (custom CHECK constraints, GIN indexes,
`TIMESTAMPTZ`). Spring Boot 3.2.x has first-class Flyway auto-configuration. Simpler
mental model than Liquibase XML/YAML changelogs.

**Alternatives considered**:
- Liquibase: more powerful rollback support, but XML syntax is verbose and rollback is not
  a stated requirement → rejected
- Manual schema init via `init.sql` in Docker: works for first run but does not handle
  incremental migrations → rejected

---

## 3. Full-Text Search Strategy

**Decision**: PostgreSQL `ILIKE` with `%term%` pattern for name/email/phone, prefix match
for Patient ID. Backed by B-Tree indexes on individual columns and a GIN tsvector index
on `(first_name || ' ' || last_name)` for combined name search.

**Rationale**: The dataset cap (100,000 records) does not require Elasticsearch or
pg_trgm extensions. `ILIKE` on indexed columns is sufficient for ≤ 2s p95 search SLA.
Spring Data JPA `@Query` with `LOWER()` handles case-insensitive matching cleanly.
The GIN index on the full name covers the most common search pattern (partial name).

**Alternatives considered**:
- Elasticsearch: powerful but a separate service, adds operational overhead → rejected for
  this scale
- `pg_trgm` trigram index: excellent for fuzzy search but requires PostgreSQL extension
  install; overkill for exact prefix/partial match → deferred as enhancement
- Full-text `tsvector` / `tsquery`: appropriate for natural language search; partial match
  (e.g., "smi" → "Smith") is better served by `ILIKE` → rejected for primary search

---

## 4. Optimistic Locking

**Decision**: JPA `@Version` annotation on the `Patient` entity. Hibernate manages the
`version` integer column automatically. On conflict, Hibernate throws
`ObjectOptimisticLockingFailureException` which is caught by `GlobalExceptionHandler`
and returned as HTTP 409 with user-friendly message.

**Rationale**: Standard Spring Data JPA / Hibernate pattern. Zero custom code beyond
exception mapping. Satisfies spec FR-030 with minimum complexity.

**Alternatives considered**:
- Pessimistic locking (`SELECT FOR UPDATE`): serialises all concurrent writes → degrades
  throughput under 100+ concurrent users → rejected
- Last-write-wins (no locking): spec explicitly forbids this → rejected

---

## 5. Circuit Breaker — Auth Module Integration

**Decision**: Resilience4j `CircuitBreaker` on the Auth Module JWT validation call.
Configuration: failure threshold 5 calls in 10 seconds → OPEN; half-open after 30s
cooldown; 1 probe call; success → CLOSED.

**Rationale**: Resilience4j is the Spring Boot ecosystem standard (replaces Hystrix).
Ships as a Spring Boot Starter. Minimal configuration. Satisfies spec FR-043 precisely.
The `@CircuitBreaker` annotation wraps the Auth Module HTTP call in `JwtAuthFilter`.

**Alternatives considered**:
- Sentinel (Alibaba): powerful but heavy, complex config → rejected for this scale
- Manual retry/timeout in filter: error-prone custom code → rejected

---

## 6. Structured JSON Logging

**Decision**: Logback with `logstash-logback-encoder` library producing JSON to stdout.
MDC (Mapped Diagnostic Context) carries `traceId`, `userId`, `operation`, `patientId`
fields. `logback-spring.xml` configures JSON appender for `docker` profile; human-readable
appender for `local` profile.

**Rationale**: `logstash-logback-encoder` is the de-facto standard for Spring Boot JSON
logging. Zero-config integration with Logback. MDC is thread-safe and propagates through
async operations. PHI restriction enforced by never placing PHI fields in MDC.

**Alternatives considered**:
- Log4j2 JSON layout: requires replacing Spring Boot's default Logback dependency → rejected
- Manual JSON string building in log messages: fragile, misses stack traces → rejected

---

## 7. Connection Pool — HikariCP

**Decision**: HikariCP (Spring Boot default) with: `minimumIdle=5`, `maximumPoolSize=20`,
`connectionTimeout=3000`, `idleTimeout=600000`, `maxLifetime=1800000`,
`keepaliveQuery=SELECT 1`.

**Rationale**: HikariCP is the fastest JVM connection pool and Spring Boot's default.
Pool size of 20 matches PostgreSQL `max_connections=100` with headroom for multiple
connections from admin/monitoring tools. `connectionTimeout=3000ms` fail-fast prevents
request queuing under DB overload. `keepaliveQuery` prevents stale connections from
PostgreSQL's `tcp_keepalives_idle`.

**Alternatives considered**:
- DBCP2: slower and more complex than HikariCP → rejected
- Larger pool (50+): PostgreSQL's cost-per-connection makes large pools
  counter-productive; 20 is optimal for the workload → pool-per-OLTP best practice

---

## 8. Frontend State Management for List Persistence

**Decision**: `useListState` custom hook using React's `useRef` + `sessionStorage`.
Search query, active filters, and current page stored in `sessionStorage` under a
stable key. Restored on navigation back from profile page.

**Rationale**: `sessionStorage` persists across React Router navigations within the same
browser tab (matching the spec requirement) but clears on tab close (appropriate
security behaviour for PHI access). `useRef` avoids re-renders on intermediate
state changes. No Redux or global store needed — state is local to the list page.

**Alternatives considered**:
- React Router search params (URL state): exposes search terms in URL bar (PHI risk
  for patient names/phones) → rejected
- Redux / Zustand: global store overkill for a single list's state → rejected
- localStorage: persists across sessions (PHI not appropriate for localStorage) → rejected

---

## 9. Frontend Validation

**Decision**: React Hook Form with Zod schema validation.

**Rationale**: React Hook Form provides uncontrolled inputs (performant, no re-render
per keystroke). Zod provides type-safe, composable validation schemas that mirror the
server-side validation rules exactly. Blur-triggered validation (`mode: 'onBlur'`)
matches the spec's requirement for field-level errors on blur.

**Alternatives considered**:
- Formik + Yup: heavier bundle, slower than React Hook Form → rejected
- Manual validation state: error-prone, duplicates logic → rejected

---

## 10. React Query Caching Strategy

**Decision**: TanStack Query v5 (`@tanstack/react-query`).
- Patient list: `staleTime: 30_000` (30 seconds), `gcTime: 300_000` (5 minutes)
- Patient profile: `staleTime: 10_000` (10 seconds)
- After any mutation (register, update, status change): invalidate affected queries

**Rationale**: 30-second stale time for the list means 100 concurrent users browsing
the list don't each generate a backend request every second — dramatically reduces load.
Profile stale time is shorter (10s) because profile is accessed during active clinical
use where freshness matters more. Post-mutation invalidation ensures consistency.

**Alternatives considered**:
- SWR: less feature-complete than React Query for mutation invalidation → rejected
- No caching (fetch on every render): violates 100-concurrent-user performance target
  → rejected

---

## 11. Docker Build Strategy

**Decision**:
- Backend: Multi-stage Dockerfile. Stage 1: Maven build (`maven:3.9-eclipse-temurin-17`).
  Stage 2: Runtime (`eclipse-temurin:17-jre-alpine`). Final image ~150MB.
- Frontend: Multi-stage Dockerfile. Stage 1: Node 20 build (`node:20-alpine`). Stage 2:
  Nginx serving static files (`nginx:1.25-alpine`). Final image ~25MB.

**Rationale**: Multi-stage builds keep runtime images small (no build tools in production
image), reducing attack surface and startup time. Alpine base images are standard for
minimal footprint. `eclipse-temurin` is the recommended OpenJDK distribution for
production containers.

**Alternatives considered**:
- JIB (Buildless Java container): excellent but removes Dockerfile as the artefact of
  understanding → rejected for clarity
- Single-stage build: final image includes Maven + JDK (500MB+) → rejected

---

## 12. HTTPS / TLS for Local Development

**Decision**: Nginx handles TLS termination with self-signed certificates generated at
`docker compose up` time via a one-time `openssl` command documented in `quickstart.md`.
A `generate-certs.sh` helper script automates this.

**Rationale**: HTTPS is mandatory per spec. Self-signed certs are sufficient for local
dev. Nginx TLS termination means the backend runs plain HTTP internally (simpler,
no JVM keystore management). The `generate-certs.sh` script ensures zero manual steps.

**Alternatives considered**:
- mkcert: generates locally-trusted certs; excellent DX but requires installing mkcert
  as a prerequisite → documented as optional enhancement
- Spring Boot embedded TLS: requires JKS/PKCS12 keystore file and Java-specific config
  → unnecessarily complex when Nginx handles termination

---

## 13. Test Strategy — Testcontainers

**Decision**: Testcontainers `PostgreSQLContainer` shared across all integration tests
via a `@TestConfiguration` base class with `@DynamicPropertySource`. Flyway migrations
run automatically on container startup.

**Rationale**: Integration tests run against a real PostgreSQL 15 instance (not H2
in-memory), catching PostgreSQL-specific behaviour (GIN indexes, `TIMESTAMPTZ`,
`SELECT FOR UPDATE`). Shared container across test class reduces container spin-up
overhead. This is the Spring Boot + Testcontainers best practice pattern.

**Alternatives considered**:
- H2 in-memory: does not support PostgreSQL-specific SQL or GIN indexes → rejected
- Separate test PostgreSQL Docker service: requires Docker Compose during CI → more
  complex setup; Testcontainers is self-contained → rejected

---

## All NEEDS CLARIFICATION Items: Resolved

| Item | Resolution |
|---|---|
| Auth Module JWT format | Assumed standard Bearer JWT in `Authorization` header. `JwtAuthFilter` extracts `userId`, `username`, `role` claims. Auth Module contract defines these fields. |
| Multi-instance deployment | Single instance in scope. Horizontal scaling deferred. Patient ID generation uses row-level lock (safe for single instance; for multi-instance, a DB sequence per year would replace this). |
| Backup scheduling mechanism | Documented as host-level cron job invoking `backup.sh`. Docker does not manage scheduling. |
| Specific TLS cert provider | Self-signed via `openssl` for local dev. Production cert management is out of scope. |
