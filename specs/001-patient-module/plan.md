# Implementation Plan: Patient Module

**Branch**: `001-patient-module` | **Date**: 2026-02-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/001-patient-module/spec.md`

---

## Summary

Build the foundational Patient Module for the Hospital Management System — a full-stack
web application providing CRUD + status management for patient records. The backend is a
Spring Boot 3.2.x REST API backed by PostgreSQL 15, containerised with Docker Compose.
The frontend is a React 18 SPA. The system must sustain >100,000 requests/hour, meet a
99.9% availability SLA, be fully HIPAA-compliant, and start from a single
`docker compose up` command with no manual setup.

---

## Technical Context

**Language/Version**: Java 17 (backend), JavaScript/Node 20 LTS (frontend)
**Primary Dependencies**:
- Backend: Spring Boot 3.2.x, Spring Data JPA, Spring Security, Resilience4j, HikariCP,
  Flyway, Lombok, MapStruct, SpringDoc OpenAPI, Logback (JSON encoder), Micrometer,
  JUnit 5, Mockito, Testcontainers
- Frontend: React 18.x, React Router v6, Axios, React Query (TanStack Query v5),
  React Hook Form, Zod (validation), Tailwind CSS, Vite
- Infrastructure: PostgreSQL 15, Nginx 1.25, Docker Compose v2

**Storage**: PostgreSQL 15 inside Docker container (`db` service). Three tables:
`patients`, `patient_id_sequences`, `patient_audit_log`. Flyway manages migrations.

**Testing**:
- Unit: JUnit 5 + Mockito (service layer, validators, mappers)
- Integration: Spring Boot Test + Testcontainers PostgreSQL (full stack in-process)
- Contract: SpringDoc OpenAPI spec validated
- Frontend: Vitest + React Testing Library

**Target Platform**: Linux containers (Docker on macOS/Linux developer machines).
Nginx reverse proxy on ports 80/443. Backend on 8080 internally. Frontend on 3000
internally.

**Project Type**: Web application (Spring Boot backend + React frontend, separate
source trees, coordinated by Docker Compose).

**Performance Goals**:
- >100,000 requests/hour peak (~150 req/sec burst)
- All API operations ≤ 2 seconds at p95 (registration ≤ 3s)
- Search across 100,000 records ≤ 2 seconds at p95

**Constraints**:
- 99.9% monthly uptime SLA (RTO ≤ 5 min, RPO ≤ 1 hour)
- HIPAA: no PHI in logs, immutable audit trail, 7-year retention
- All writes use optimistic locking (version field), conflict → HTTP 409
- Rate limit: 200 req/IP/min at Nginx layer
- No hard deletes on patient records — ever

**Scale/Scope**:
- 100,000 patient records, 500 concurrent users
- Single backend instance (horizontally scalable in future but not designed for multi-instance now)
- Docker local deployment only (no cloud/K8s in scope)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate Condition | Status |
|---|---|---|
| I. Spec-Driven Development | Spec exists and is approved before any code | ✅ PASS — spec.md v3.0.0 complete |
| II. HIPAA-First | PHI handling, audit log, no PHI in logs, HTTPS, role enforcement | ✅ PASS — all HIPAA controls specified in FR-035–FR-039 |
| III. Test-First | Tests written before implementation; JUnit 5 + Testcontainers plan confirmed | ✅ PASS — unit + integration tests planned, TDD order enforced in tasks |
| IV. Layered Architecture | Controller → Service → Repository; DTOs at boundary; no cross-layer leaks | ✅ PASS — three-layer structure enforced in project layout |
| V. RBAC | Server-side role check on every endpoint; HTTP 401/403 defined; Auth Module integration | ✅ PASS — all 6 operations mapped to roles; circuit breaker defined |

**No constitution violations. Proceeding to Phase 0.**

### Post-Design Constitution Re-check (Phase 1 Complete)

| Principle | Design Decision | Status |
|---|---|---|
| I. Spec-Driven | All design artifacts derived from spec.md v3.0.0 | ✅ PASS |
| II. HIPAA-First | PHI excluded from logs (MDC holds only `patientId`, not names/phones); audit written in same transaction; TLS via Nginx; parameterised queries only | ✅ PASS |
| III. Test-First | Unit tests: JUnit 5 + Mockito per service/validator/mapper; Integration: Testcontainers PostgreSQL per user story; TDD order enforced in tasks | ✅ PASS |
| IV. Layered Architecture | Controller (PatientController) → Service (PatientService) → Repository (PatientRepository); MapStruct DTOs at controller boundary; AuditService called from Service layer | ✅ PASS |
| V. RBAC | `RoleGuard` checked in service layer; `JwtAuthFilter` validates every request; circuit breaker on Auth Module; HTTP 401/403 defined in OpenAPI contracts | ✅ PASS |

**All constitution gates pass post-design. Plan is approved for task generation.**

---

## Project Structure

### Documentation (this feature)

```text
specs/001-patient-module/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── openapi.yaml     ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
Hospital_patient_model/
├── docker-compose.yml              # Single command to start full stack
├── .env.example                    # All required env vars documented
├── .env                            # Local values (gitignored)
│
├── nginx/
│   ├── nginx.conf                  # Reverse proxy, HTTPS termination, rate limiting
│   └── ssl/                        # Self-signed certs for local dev (gitignored)
│       ├── cert.pem
│       └── key.pem
│
├── backend/                        # Spring Boot application
│   ├── Dockerfile
│   ├── pom.xml                     # Spring Boot 3.2.x parent, all dependencies
│   └── src/
│       ├── main/
│       │   ├── java/com/ainexus/hospital/patient/
│       │   │   ├── PatientModuleApplication.java
│       │   │   ├── controller/
│       │   │   │   └── PatientController.java
│       │   │   ├── service/
│       │   │   │   ├── PatientService.java
│       │   │   │   └── PatientIdGeneratorService.java
│       │   │   ├── repository/
│       │   │   │   ├── PatientRepository.java
│       │   │   │   ├── PatientIdSequenceRepository.java
│       │   │   │   └── PatientAuditLogRepository.java
│       │   │   ├── entity/
│       │   │   │   ├── Patient.java
│       │   │   │   ├── PatientIdSequence.java
│       │   │   │   └── PatientAuditLog.java
│       │   │   ├── dto/
│       │   │   │   ├── request/
│       │   │   │   │   ├── PatientRegistrationRequest.java
│       │   │   │   │   └── PatientUpdateRequest.java
│       │   │   │   └── response/
│       │   │   │       ├── PatientResponse.java
│       │   │   │       ├── PatientSummaryResponse.java
│       │   │   │       └── PagedResponse.java
│       │   │   ├── mapper/
│       │   │   │   └── PatientMapper.java       # MapStruct
│       │   │   ├── validation/
│       │   │   │   ├── PhoneNumber.java          # Custom annotation
│       │   │   │   └── PhoneNumberValidator.java
│       │   │   ├── exception/
│       │   │   │   ├── PatientNotFoundException.java
│       │   │   │   ├── ConflictException.java
│       │   │   │   └── GlobalExceptionHandler.java
│       │   │   ├── audit/
│       │   │   │   └── AuditService.java
│       │   │   ├── security/
│       │   │   │   ├── AuthContext.java          # Holds userId, username, role
│       │   │   │   ├── RoleGuard.java            # Role enforcement helper
│       │   │   │   └── JwtAuthFilter.java        # Extracts Auth Module JWT
│       │   │   └── config/
│       │   │       ├── SecurityConfig.java
│       │   │       ├── HikariConfig.java
│       │   │       ├── CircuitBreakerConfig.java  # Resilience4j for Auth Module
│       │   │       ├── FlywayConfig.java
│       │   │       └── OpenApiConfig.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-docker.yml
│       │       ├── logback-spring.xml           # JSON structured logging
│       │       └── db/migration/
│       │           ├── V1__create_patients.sql
│       │           ├── V2__create_patient_id_sequences.sql
│       │           ├── V3__create_patient_audit_log.sql
│       │           └── V4__create_indexes.sql
│       └── test/
│           └── java/com/ainexus/hospital/patient/
│               ├── unit/
│               │   ├── service/
│               │   │   ├── PatientServiceTest.java
│               │   │   └── PatientIdGeneratorServiceTest.java
│               │   ├── validation/
│               │   │   └── PhoneNumberValidatorTest.java
│               │   └── mapper/
│               │       └── PatientMapperTest.java
│               └── integration/
│                   ├── PatientRegistrationIT.java
│                   ├── PatientSearchIT.java
│                   ├── PatientProfileIT.java
│                   ├── PatientUpdateIT.java
│                   ├── PatientStatusIT.java
│                   └── PatientRbacIT.java
│
├── frontend/                       # React 18 SPA
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── api/
│       │   └── patientApi.js       # Axios client + React Query hooks
│       ├── hooks/
│       │   ├── usePatients.js      # useQuery for list + search
│       │   ├── usePatient.js       # useQuery for single profile
│       │   └── useListState.js     # Persist search/filter/page state
│       ├── pages/
│       │   ├── PatientListPage.jsx
│       │   ├── PatientRegistrationPage.jsx
│       │   └── PatientProfilePage.jsx
│       ├── components/
│       │   ├── patient/
│       │   │   ├── PatientRegistrationForm.jsx
│       │   │   ├── PatientEditForm.jsx
│       │   │   ├── PatientList.jsx
│       │   │   ├── PatientListRow.jsx
│       │   │   ├── PatientProfile.jsx
│       │   │   ├── PatientStatusBadge.jsx
│       │   │   └── DeactivateConfirmModal.jsx
│       │   └── common/
│       │       ├── Pagination.jsx
│       │       ├── SearchBox.jsx
│       │       ├── FilterBar.jsx
│       │       ├── InlineError.jsx
│       │       └── LoadingSpinner.jsx
│       └── test/
│           ├── PatientRegistrationForm.test.jsx
│           ├── PatientList.test.jsx
│           └── PatientProfile.test.jsx
│
└── scripts/
    ├── backup.sh                   # pg_dump daily backup
    ├── restore.sh                  # Restore from backup
    └── healthcheck.sh             # Manual stack health verification
```

**Structure Decision**: Web application (Option 2) — separate `backend/` and `frontend/`
directories coordinated by `docker-compose.yml` at the repository root. This reflects the
Spring Boot + React stack and allows each layer to be built, tested, and containerised
independently. The `nginx/` directory at the root serves as the reverse proxy configuration.

---

## Complexity Tracking

No constitution violations requiring justification.

| Decision | Rationale |
|---|---|
| MapStruct for entity↔DTO mapping | Eliminates manual mapping boilerplate; compile-time safe; aligns with Layered Architecture principle (DTOs at boundary) |
| Resilience4j circuit breaker | Auth Module is a hard external dependency; circuit breaker prevents cascading failure and satisfies 99.9% SLA |
| Flyway for DB migrations | Versioned, repeatable, auditable schema evolution; required for Docker init-on-first-run pattern |
| Testcontainers for integration tests | Spins up real PostgreSQL for integration tests; ensures test fidelity matches production DB |
| React Query for frontend data fetching | Provides caching, background refetch, and request deduplication — critical for 100-concurrent-user target without overloading the backend |
| Optimistic locking (version field) | Prevents silent data loss from concurrent edits; mandated by spec FR-030 |
