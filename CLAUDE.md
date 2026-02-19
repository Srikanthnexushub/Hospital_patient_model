# Hospital Patient Management System — Development Guidelines

Auto-generated from feature plans. Last updated: 2026-02-19

## Active Technologies

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

- `001-patient-module`: Initial patient module — registration, search, profile, update, status management

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
