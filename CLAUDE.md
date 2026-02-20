# Hospital Patient Management System — Development Guidelines

Auto-generated from feature plans. Last updated: 2026-02-19

## Active Technologies
- Java 17 (backend), JavaScript/Node 20 LTS (frontend) (002-auth-module)
- PostgreSQL 15 (same Docker container as Patient Module). (002-auth-module)

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2.x, Spring Data JPA, Spring Security, Resilience4j, HikariCP, Flyway, MapStruct, Lombok |
| Frontend | React 18.x, Vite, React Router v6, TanStack Query v5, React Hook Form, Zod, Tailwind CSS |
| Database | PostgreSQL 15 (Docker) |
| Infrastructure | Docker Compose v2, Nginx 1.25 |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest, React Testing Library |

## Project Structure

```text
Hospital_patient_model/
├── docker-compose.yml        # Full stack: db, backend, frontend, proxy
├── .env.example              # Required env vars (copy to .env)
├── nginx/nginx.conf          # Reverse proxy + TLS + rate limiting
├── backend/                  # Spring Boot Maven project
│   ├── pom.xml
│   └── src/main/java/com/ainexus/hospital/patient/
│       ├── controller/       # HTTP layer — no business logic
│       ├── service/          # All business logic lives here
│       ├── repository/       # Spring Data JPA repositories
│       ├── entity/           # JPA entities (Patient, PatientIdSequence, PatientAuditLog)
│       ├── dto/              # Request/Response DTOs (never expose entities)
│       ├── mapper/           # MapStruct entity↔DTO mappers
│       ├── validation/       # Custom validators (@PhoneNumber, @ValidDateOfBirth)
│       ├── exception/        # GlobalExceptionHandler + custom exceptions
│       ├── audit/            # AuditService (writes PatientAuditLog in same transaction)
│       ├── security/         # JwtAuthFilter, AuthContext, RoleGuard
│       └── config/           # HikariCP, CircuitBreaker, Flyway, OpenAPI
├── frontend/                 # React SPA
│   └── src/
│       ├── api/              # Axios client + React Query hooks
│       ├── pages/            # PatientListPage, PatientRegistrationPage, PatientProfilePage
│       ├── components/       # patient/ and common/ components
│       └── hooks/            # usePatients, usePatient, useListState
└── specs/001-patient-module/ # Feature spec, plan, data model, contracts, quickstart
```

## Key Commands

```bash
# Start full stack
docker compose up --build

# Backend tests only
cd backend && mvn test

# Backend integration tests (Testcontainers — requires Docker)
cd backend && mvn verify -Pfailsafe

# Frontend tests
cd frontend && npm test

# Generate TLS certs (first time only)
bash scripts/generate-certs.sh
```

## Critical Rules (from Constitution v1.0.0)

1. **Spec before code** — no implementation without an approved spec in `specs/`
2. **HIPAA-First** — PHI never in logs; every write produces an audit log entry
3. **Test-First** — tests written and failing BEFORE implementation (TDD)
4. **Layered architecture** — Controller → Service → Repository; DTOs at boundary only
5. **RBAC** — server-side role check on EVERY endpoint; client-side checks are cosmetic

## Recent Changes

- `002-auth-module`: Auth Module — JWT-backed staff login, token refresh/revoke, user management (ADMIN only)
  - **BCrypt-12**: `PasswordConfig` exposes `@Bean PasswordEncoder` → `new BCryptPasswordEncoder(12)`
  - **Filter order**: `addFilterBefore(blacklistCheckFilter, UsernamePasswordAuthenticationFilter.class)` THEN `addFilterBefore(jwtAuthFilter, ...)` — both anchored to `UsernamePasswordAuthenticationFilter`, insertion order makes blacklist run first; custom filters cannot be used as anchors
  - **JwtAuthFilter skip**: Only `/api/v1/auth/login` is skipped — refresh/logout/me all require a valid token
  - **Lockout persistence**: `login()` annotated `@Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})` — without this, failed-attempt counter is rolled back on exception
  - **AdminSeeder**: `ApplicationRunner` bean reads `ADMIN_INITIAL_PASSWORD` env var (NO default — startup fails if absent); hashes with BCrypt before insert
  - **Staff IDs**: `U` + year + 3-digit zero-padded seq (e.g. `U2026001`) via `StaffIdGeneratorService`
  - **Token blacklist cleanup**: `BlacklistCleanupService` runs `@Scheduled(cron = "0 */15 * * * *")` — requires `@EnableScheduling` in `SchedulingConfig`
  - **Java records as DTOs**: Accessor methods are `field()` not `getField()` — `request.username()`, `request.password()`
  - **AccountLockedException** → HTTP 423; **BadCredentialsException** → HTTP 401

- `001-patient-module`: Initial patient module — registration, search, profile, update, status management

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
