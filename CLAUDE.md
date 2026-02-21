# Hospital Patient Management System — Development Guidelines

Auto-generated from feature plans. Last updated: 2026-02-20

## Active Technologies
- Java 17 (backend), JavaScript/Node 20 LTS (frontend) (002-auth-module)
- PostgreSQL 15 (same Docker container as Patient Module). (002-auth-module)
- Java 17 (backend), JavaScript ES2022 (frontend) + Spring Boot 3.2.3, Spring Data JPA / Hibernate 6, Spring Security 6, MapStruct 1.5.5, Lombok 1.18.38, Resilience4j 2.2.0, Micrometer Prometheus, jjwt 0.12.5, React 18, TanStack Query v5, React Hook Form, Zod, Tailwind CSS — **all pre-existing; zero new dependencies required** (003-appointment-scheduling)
- PostgreSQL 15 — 4 new tables via Flyway V8–V11 (003-appointment-scheduling)
- Java 17, Spring Boot 3.2.x + Spring Data JPA, Spring Security (RoleGuard/AuthContext from Module 2), Flyway, MapStruct, Lombok, Micrometer (MeterRegistry) (004-billing-module)
- PostgreSQL 15 — new tables V12–V16 via Flyway; existing V1–V11 untouched (004-billing-module)
- Java 17 / Spring Boot 3.2.x (Hibernate 6.4.x) + Spring Data JPA, Spring Security (existing), MapStruct, Lombok, Resilience4j, Flyway, Micrometer (005-emr-module)
- PostgreSQL 15 — 4 new entity tables + 1 audit table via Flyway V17–V21 (005-emr-module)
- Java 17 (LTS), Spring Boot 3.2.x + Spring Data JPA (Hibernate 6), Spring Security, Lombok, MapStruct, Resilience4j, Micrometer, Flyway 10.x, jjwt 0.12.5 (006-clinical-intelligence)
- PostgreSQL 15 — 3 new tables via Flyway V22, V23, V24; no schema changes to existing tables (006-clinical-intelligence)

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
- 006-clinical-intelligence: Added Java 17 (LTS), Spring Boot 3.2.x + Spring Data JPA (Hibernate 6), Spring Security, Lombok, MapStruct, Resilience4j, Micrometer, Flyway 10.x, jjwt 0.12.5
- 005-emr-module: Added Java 17 / Spring Boot 3.2.x (Hibernate 6.4.x) + Spring Data JPA, Spring Security (existing), MapStruct, Lombok, Resilience4j, Flyway, Micrometer
- `004-billing-module`: Billing & Invoicing Module — full invoice lifecycle (US1–US5) + full frontend UI
  - **invoiceId format**: `INV` + year + 6-digit zero-padded seq (e.g. `INV2026000001`); expands beyond 999999
  - **Status lifecycle**: DRAFT → ISSUED → PARTIALLY_PAID → PAID; CANCELLED (from DRAFT/ISSUED); WRITTEN_OFF (from ISSUED/PARTIALLY_PAID)
  - **Monetary amounts**: `NUMERIC(12,2)` — never floating point; `@Value("${billing.tax-rate:0.00}") BigDecimal taxRate` configurable
  - **Payment methods**: `CASH | CARD | INSURANCE | BANK_TRANSFER | CHEQUE`; auto-status-transition on payment
  - **DOCTOR scoping**: `ctx.getUserId()` must match `invoice.doctorId` (derived from appointment)
  - **Native query pitfalls**: `CAST(col AS type)` not `col::type` (conflicts with `:param`); aggregate returns `List<Object[]>`
  - **Financial report**: `GET /api/v1/reports/financial?dateFrom=&dateTo=` — ADMIN only; date range filters on `CAST(created_at AS date)`
  - **Flyway migrations**: V12 (invoice_id_sequences), V13 (invoices), V14 (invoice_line_items), V15 (invoice_payments), V16 (invoice_audit_log)
  - **Test results**: 108 unit + 191 integration = 299 total, 0 failures
  - **Frontend**: `billingApi.js`, `useInvoices.js`, `InvoiceListPage`, `InvoiceCreatePage`, `InvoiceDetailPage`, `FinancialReportPage`; "Generate Invoice" button on COMPLETED appointments; Billing nav (non-NURSE); Reports nav (ADMIN)
  - **appointmentId format**: `APT` + year + 4-digit zero-padded seq (e.g. `APT20260001`)
  - **Status machine**: SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED; CANCELLED/NO_SHOW are terminal; transitions enforced in `AppointmentStatusService`
  - **Conflict detection**: `AppointmentRepository.findOverlappingAppointments()` uses `SELECT FOR UPDATE` to prevent race conditions
  - **Availability slots**: 30-min granularity, 08:00–18:00; slot blocked if overlap check `a.startTime < slotEnd && a.endTime > slotStart`
  - **Clinical notes encryption**: AES-256 via `ClinicalNotesEncryptionService`; `NOTES_ENCRYPTION_KEY` env var required; never logged
  - **DOCTOR filter**: All appointment list queries automatically scope to `doctorId = currentUserId` for DOCTOR role
  - **Flyway migrations**: V8 (appointments), V9 (appointment_audit_log), V10 (clinical_notes), V11 (appointment_id_sequences)
  - **`bookAppointment()` test helper** in `BaseIntegrationTest` has 4-param (defaults 30 min) and 5-param (explicit duration) overloads; use 5-param when test logic depends on which slots are blocked
  - **DoctorAvailabilityService**: JPQL query returns ALL non-deleted appointments; Java-level filter excludes CANCELLED/NO_SHOW before slot blocking (avoids Hibernate 6 enum string literal issues in JPQL)

  - **BCrypt-12**: `PasswordConfig` exposes `@Bean PasswordEncoder` → `new BCryptPasswordEncoder(12)`
  - **Filter order**: `addFilterBefore(blacklistCheckFilter, UsernamePasswordAuthenticationFilter.class)` THEN `addFilterBefore(jwtAuthFilter, ...)` — both anchored to `UsernamePasswordAuthenticationFilter`, insertion order makes blacklist run first; custom filters cannot be used as anchors
  - **JwtAuthFilter skip**: Only `/api/v1/auth/login` is skipped — refresh/logout/me all require a valid token
  - **Lockout persistence**: `login()` annotated `@Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})` — without this, failed-attempt counter is rolled back on exception
  - **AdminSeeder**: `ApplicationRunner` bean reads `ADMIN_INITIAL_PASSWORD` env var (NO default — startup fails if absent); hashes with BCrypt before insert
  - **Staff IDs**: `U` + year + 3-digit zero-padded seq (e.g. `U2026001`) via `StaffIdGeneratorService`
  - **Token blacklist cleanup**: `BlacklistCleanupService` runs `@Scheduled(cron = "0 */15 * * * *")` — requires `@EnableScheduling` in `SchedulingConfig`
  - **Java records as DTOs**: Accessor methods are `field()` not `getField()` — `request.username()`, `request.password()`
  - **AccountLockedException** → HTTP 423; **BadCredentialsException** → HTTP 401


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
