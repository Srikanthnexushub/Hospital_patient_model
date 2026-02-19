<!--
SYNC IMPACT REPORT
==================
Version change: N/A (initial) → 1.0.0
Modified principles: None (initial ratification)
Added sections:
  - Core Principles (I–V)
  - Technology Stack
  - Development Workflow
  - Governance
Removed sections: None
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (Constitution Check gates apply)
  - .specify/templates/spec-template.md ✅ (HIPAA + RBAC as mandatory req sections)
  - .specify/templates/tasks-template.md ✅ (test-first task ordering enforced)
Follow-up TODOs: None
-->

# Hospital Patient Management System — Patient Module Constitution

## Core Principles

### I. Spec-Driven Development

Every feature MUST begin with a written specification before any implementation starts.
Specifications MUST be approved before coding begins.
No code MUST be written for a feature that lacks a corresponding spec in `specs/`.
AI coding agents MUST reference the spec as the source of truth during implementation.

### II. HIPAA-First (NON-NEGOTIABLE)

Patient data is Protected Health Information (PHI) and MUST be handled in full compliance
with HIPAA at all times.
- All patient data fields (name, DOB, phone, email, address, medical info) MUST be treated as PHI.
- All access to patient data MUST be logged for audit purposes with user identity and timestamp.
- Patient data MUST only be accessible to authenticated users with appropriate roles.
- No PHI MUST be logged in plain text in application logs.
- Sensitive fields (phone, email) MUST be validated before storage.

### III. Test-First (NON-NEGOTIABLE)

TDD is mandatory for all feature implementation.
- Tests MUST be written and reviewed before implementation begins.
- Tests MUST fail before implementation starts (Red phase confirmed).
- Implementation proceeds only to make failing tests pass (Green phase).
- Refactor only after tests pass (Refactor phase).
- Unit tests MUST cover all service-layer business logic.
- Integration tests MUST cover all API endpoints.

### IV. Layered Architecture (Spring Boot Standard)

The backend MUST follow a strict three-layer architecture with no cross-layer violations.
- **Controller layer**: HTTP request/response handling only — no business logic.
- **Service layer**: All business logic, validation, and orchestration lives here.
- **Repository layer**: All database interactions via Spring Data JPA — no raw SQL in services.
- DTOs MUST be used at the controller boundary; entities MUST NOT be exposed directly in API responses.
- Each layer MUST be independently testable (unit-testable in isolation).

### V. Role-Based Access Control

Every API endpoint MUST enforce role-based access control before processing any request.
- Roles: RECEPTIONIST, DOCTOR, NURSE, ADMIN.
- Write operations (register, update, deactivate/activate) MUST be restricted to
  RECEPTIONIST and ADMIN only.
- Read operations (search, view) MUST be available to all authenticated roles.
- Role enforcement MUST be implemented at the service layer, not only at the controller.
- The Auth module is a hard dependency; patient endpoints MUST NOT be accessible
  without a valid authenticated session.

## Technology Stack

- **Backend**: Spring Boot 3.2.x, Java 17
- **Frontend**: React 18.x
- **Database**: PostgreSQL 15+
- **ORM**: Spring Data JPA / Hibernate
- **Build Tool**: Maven
- **Testing**: JUnit 5, Mockito (unit), Spring Boot Test (integration)
- **API Style**: RESTful JSON API

All technology choices MUST align with this stack. Deviations require constitution amendment.

## Development Workflow

- Features MUST follow the spec-kit workflow: `specify → plan → implement → tasks`
- All specs live in `specs/<###-feature-name>/`
- Patient ID format: `P` + 4-digit year + 3-digit sequential number (e.g., `P2026001`)
- Date of birth MUST NOT allow future dates; age MUST be auto-calculated from DOB
- Phone validation MUST accept: `+1-XXX-XXX-XXXX`, `(XXX) XXX-XXXX`, `XXX-XXX-XXXX`
- Duplicate phone numbers MUST trigger a warning but MUST NOT block registration
- Patient records MUST be soft-deleted (status: INACTIVE) — hard deletes are forbidden

## Governance

- This constitution supersedes all other coding practices and guidelines for this project.
- All implementation MUST be verified against this constitution before merging.
- Amendments require: documented rationale, version increment per semantic versioning rules,
  and update to `LAST_AMENDED_DATE`.
- Complexity MUST be justified — YAGNI and simplicity are defaults.
- Versioning:
  - MAJOR: Removal or incompatible redefinition of a principle.
  - MINOR: New principle or section added.
  - PATCH: Clarifications, wording fixes, non-semantic refinements.

**Version**: 1.0.0 | **Ratified**: 2026-02-19 | **Last Amended**: 2026-02-19
