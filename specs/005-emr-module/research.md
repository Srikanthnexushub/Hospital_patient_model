# Research: Electronic Medical Records (EMR) Module

**Branch**: `005-emr-module` | **Date**: 2026-02-20
**Phase 0 output** for `plan.md`

---

## Decision 1: UUID Primary Key Strategy

**Question**: How to generate and map UUID PKs for `patient_problems`, `patient_medications`, and `patient_allergies` in Hibernate 6 + PostgreSQL 15?

**Decision**: Java-side generation using `GenerationType.UUID` (Hibernate 6.4 native), with PostgreSQL `uuid` column type.

**Implementation**:
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "id", columnDefinition = "uuid", nullable = false)
private UUID id;
```

**Rationale**:
- Spring Boot 3.2.x bundles Hibernate 6.4.x — `GenerationType.UUID` is natively supported (no extra annotations needed).
- PostgreSQL native `uuid` column type (16 bytes vs. 36 bytes for VARCHAR) gives better storage and index performance.
- Java-side generation means no DB round-trip for ID retrieval; consistent with project's client-ID pattern.
- `isNew()` detection works correctly: null UUID → INSERT; non-null UUID → UPDATE. No `Persistable` override needed.

**Alternatives Considered**:
- `gen_random_uuid()` DEFAULT in SQL — adds DB dependency, complicates test data setup.
- Legacy `@GenericGenerator(strategy = "uuid2")` — deprecated in Hibernate 6; `GenerationType.UUID` is the correct replacement.

**Gotcha**: Never set UUID manually before `save()` — Hibernate's `isNew()` depends on null ID to trigger INSERT. If UUID is assigned before save, JPA will attempt UPDATE, not INSERT.

---

## Decision 2: Upsert Pattern for Vitals (One Record Per Appointment)

**Question**: How to implement upsert for `patient_vitals` where only one record per `appointment_id` is allowed, and re-POST replaces existing data?

**Decision**: Service-level upsert via `findByAppointmentId()` → update-or-create in a `@Transactional` method.

**Implementation**:
```java
// Repository
Optional<PatientVitals> findByAppointmentId(String appointmentId);

// Service
Optional<PatientVitals> existing = vitalsRepository.findByAppointmentId(appointmentId);
PatientVitals vitals = existing.orElseGet(PatientVitals::new);
String action = existing.isPresent() ? "UPDATE" : "CREATE";
// ... set fields ...
vitalsRepository.save(vitals);
emrAuditService.writeAuditLog("VITAL", String.valueOf(vitals.getId()), ...);
```

**Rationale**:
- Matches existing `InvoiceService.existsByAppointmentId()` pattern — consistent across the codebase.
- Integrates cleanly with `EmrAuditService` (can capture old vs. new state for audit).
- Vitals are written at most once per appointment by a single nurse/doctor — concurrent writes in the same appointment are not a realistic scenario. No need for pessimistic locking.
- Two SQL statements (SELECT + INSERT/UPDATE) is acceptable for clinical write frequency (< 1/second per appointment).

**Alternatives Considered**:
- PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` native query: atomic single statement, but loses JPA entity tracking and complicates audit integration (`@Modifying` + no entity return). Justified only for streaming vitals monitors (50+ writes/second), not applicable here.
- JPQL MERGE: not supported by Hibernate/JPQL — not viable.

**UNIQUE constraint**: `appointment_id` column has `UNIQUE` constraint in DDL as a safety net. Service-level check prevents normal-flow conflicts.

---

## Decision 3: Audit Log Design for EMR

**Question**: Use one shared `emr_audit_log` table or four separate tables (one per entity)? One `EmrAuditService` or four domain-specific services?

**Decision**: Single `emr_audit_log` table with `entity_type` discriminator column; single `EmrAuditService` with `Propagation.MANDATORY`.

**Table schema**:
```sql
CREATE TABLE emr_audit_log (
    id           BIGSERIAL    NOT NULL PRIMARY KEY,
    entity_type  VARCHAR(20)  NOT NULL,  -- VITAL | PROBLEM | MEDICATION | ALLERGY
    entity_id    VARCHAR(50)  NOT NULL,  -- BIGINT id for vitals, UUID for others
    patient_id   VARCHAR(14)  NOT NULL,  -- denormalized for fast filtering
    action       VARCHAR(30)  NOT NULL,  -- CREATE | UPDATE | DISCONTINUE | RESOLVE | DELETE
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    details      TEXT
);
```

**Rationale**:
- All 4 EMR entities belong to the same clinical workflow and same patient context — cross-entity queries ("show all EMR changes for patient X") are common.
- Lower migration overhead: 1 migration instead of 4 separate tables.
- `Propagation.MANDATORY` — matches `InvoiceAuditService` and `AppointmentAuditService` patterns (state-change audits require atomic commit with parent entity).
- HIPAA-safe: `details` stores only field names or action context (e.g., "status changed from ACTIVE to RESOLVED"), not actual clinical values. No PHI in audit values.

**Alternatives Considered**:
- 4 separate tables (one per entity): justified if entities have vastly different audit schemas or separate compliance reporting needs; not warranted here — all EMR entities share the same audit fields.
- Storing JSONB old/new values: adds full change history for malpractice window, but increases PHI exposure in audit log. Deferred to later compliance requirement if needed.

**Propagation**: `MANDATORY` — caller (e.g., `VitalsService`, `ProblemService`) must be `@Transactional`. Audit write and entity save commit atomically. EMR is clinical data — audit cannot be silently skipped.

---

## Decision 4: `patient_id` Column Width

**Question**: Existing tables use `VARCHAR(12)` for patient_id. New tables should use what width?

**Decision**: Use `VARCHAR(14)` to match the actual patient ID format (`P` + 4-digit year + 3+ digit sequence = up to 14 chars for 9999+ patients).

**Rationale**: Memory notes document "Patient ID format: P + year + 3-digit zero-padded seq (e.g. P2026001); 4+ digits past 999". `VARCHAR(14)` is safe for all realistic values.

---

## Decision 5: Appointment Existence Check for Vitals

**Question**: Should VitalsService verify the `appointment_id` exists before saving vitals?

**Decision**: Yes — call `AppointmentRepository.existsById(appointmentId)` before saving. Return 404 if not found.

**Rationale**: FR-026 requires a not-found response when any EMR operation references a non-existent appointment. The existing `AppointmentRepository` can be injected directly (same Maven module, same JPA context). This is the same pattern used in `InvoiceService` which checks patient existence via `PatientRepository`.

---

## Decision 6: Medical Summary — Data Source for Visit Stats

**Question**: `GET /patients/{patientId}/medical-summary` needs `lastVisitDate` and `totalVisits`. Where does this data come from?

**Decision**: Query `AppointmentRepository` for COMPLETED appointments by `patientId`. Use `count()` for `totalVisits` and `max(appointmentDate)` for `lastVisitDate`.

**Rationale**: Appointments are in the same JPA context (same Maven module). A JPQL query like `SELECT COUNT(a), MAX(a.appointmentDate) FROM Appointment a WHERE a.patientId = :patientId AND a.status = 'COMPLETED'` is efficient and avoids cross-service HTTP calls.

---

## Decision 7: Medication `prescribedBy` — Auto-Set vs. User-Provided

**Question**: FR-013 says `prescribedBy` is auto-set from auth context. Should the request body allow overriding it?

**Decision**: `prescribedBy` is ignored from request body; always set from `AuthContext.Holder.get().getUsername()`.

**Rationale**: Prevents one doctor from forging prescriptions attributed to another doctor. Auth context is authoritative. This matches the `patientId` auto-population pattern used in existing modules.
