# Research: Clinical Intelligence & Safety Module

**Feature**: 006-clinical-intelligence
**Date**: 2026-02-21

---

## 1. UUID Primary Keys — Spring Data JPA + PostgreSQL

**Decision**: Use `@GeneratedValue(strategy = GenerationType.UUID)` with `@Column(columnDefinition = "uuid")`.

**Rationale**:
- This is the pattern already established in this codebase (`PatientMedication`, `PatientAllergy`).
- `GenerationType.UUID` delegates to Hibernate 6's UUIDGenerator which uses a version-4 UUID by default (random, sufficient entropy for this scale).
- `columnDefinition = "uuid"` stores as PostgreSQL native UUID type (16 bytes) for indexing efficiency.
- No extension needed (`gen_random_uuid()` via `uuid-ossp` is not required when Hibernate generates the UUID in Java).

**Flyway Migration Pattern**:
```sql
CREATE TABLE lab_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ...
);
```
Use `DEFAULT gen_random_uuid()` in Flyway as a fallback for any direct SQL inserts (tests, seeds). The `pgcrypto` extension providing `gen_random_uuid()` is available by default in PostgreSQL 13+; no explicit `CREATE EXTENSION` required.

---

## 2. Drug Interaction Database — Static @Component

**Decision**: Implement `DrugInteractionDatabase` as a `@Component` with a `Map<String, List<DrugInteractionEntry>>` keyed by **normalized lowercase drug name**. Each drug name maps to all known interactions involving that drug (both as drug1 and drug2).

**Pattern**:
```java
@Component
public class DrugInteractionDatabase {
    private final Map<String, List<DrugInteractionEntry>> index = new HashMap<>();

    @PostConstruct
    private void init() {
        register("warfarin", "aspirin", MAJOR, "Additive anticoagulation", "Severe GI/intracranial bleeding", "Avoid; use PPI if unavoidable");
        // ... 40+ pairs
    }

    private void register(String d1, String d2, InteractionSeverity severity, ...) {
        DrugInteractionEntry entry = new DrugInteractionEntry(d1, d2, severity, ...);
        index.computeIfAbsent(d1, k -> new ArrayList<>()).add(entry);
        index.computeIfAbsent(d2, k -> new ArrayList<>()).add(entry);
    }

    public List<DrugInteractionEntry> findInteractionsFor(String drugName) {
        return index.getOrDefault(drugName.toLowerCase().trim(), List.of());
    }
}
```

**Rationale**: O(1) lookup per drug. Bidirectional indexing avoids asymmetric misses (searching "warfarin" finds "warfarin+aspirin" AND "aspirin+warfarin"). No database table, no external API calls — matches spec FR-036.

**Alternatives considered**:
- External API (DrugBank, OpenFDA): Rejected — requires network, SLA dependency, potential rate limits. Spec explicitly forbids.
- Database table: Rejected — overkill for a static dataset; complicates Flyway, slows startup.

---

## 3. NEWS2 Score Computation — Pure Java Service

**Decision**: Implement `News2Calculator` as a `@Component` (stateless) with a single `compute(PatientVitals)` method returning `News2Result` (a record/DTO, not persisted).

**Algorithm implementation**:
```java
@Component
public class News2Calculator {
    public News2Result compute(PatientVitals vitals) {
        List<ComponentScore> components = new ArrayList<>();
        int total = 0;
        boolean anyThree = false;

        // Respiratory Rate
        if (vitals.getRespiratoryRate() != null) {
            int score = scoreRr(vitals.getRespiratoryRate());
            components.add(new ComponentScore("Respiratory Rate", vitals.getRespiratoryRate(), score, "/min"));
            total += score;
            if (score == 3) anyThree = true;
        }
        // ... 5 more parameters

        RiskLevel riskLevel = classifyRisk(total, anyThree);
        return new News2Result(total, riskLevel, ...);
    }

    private int scoreRr(int rr) {
        if (rr <= 8) return 3;
        if (rr <= 11) return 1;
        if (rr <= 20) return 0;
        if (rr <= 24) return 2;
        return 3; // >= 25
    }
    ...
}
```

**Risk Classification**:
- Total=0 → LOW
- Total 1-4, no single parameter score of 3 → LOW_MEDIUM
- Total 1-4, any single parameter score of 3 → MEDIUM
- Total 5-6 → MEDIUM
- Total ≥7 → HIGH

**Rationale**: Pure Java enum-driven switch/if logic. No ML, no external service. Fully unit-testable with mocked vitals. NHS NHS7 NEWS2 standard (2017).

---

## 4. Alert Deduplication — Service Layer Pattern

**Decision**: Implement deduplication in `ClinicalAlertService.createAlert()` using a repository query before saving.

**Pattern**:
```java
@Transactional
public ClinicalAlert createAlert(String patientId, AlertType alertType, ...) {
    if (alertType.isNews2Type()) {
        // dismiss existing active alert of same type for this patient
        clinicalAlertRepository
            .findActiveByPatientIdAndAlertType(patientId, alertType)
            .ifPresent(old -> {
                old.setStatus(AlertStatus.DISMISSED);
                old.setDismissReason("Auto-dismissed: superseded by new " + alertType + " alert");
                old.setDismissedAt(OffsetDateTime.now());
                clinicalAlertRepository.save(old);
            });
    }
    ClinicalAlert alert = new ClinicalAlert(...);
    return clinicalAlertRepository.save(alert);
}
```

**Repository query**:
```java
Optional<ClinicalAlert> findByPatientIdAndAlertTypeAndStatus(
    String patientId, AlertType alertType, AlertStatus status);
```

**Rationale**: Atomic within a `@Transactional` service call. Deduplication is NEWS2-scoped only (per spec); lab and drug alerts are not deduplicated. Using `Optional` prevents NPE on first-time creation.

---

## 5. Patient Risk Dashboard — Query Strategy

**Decision**: Use **native SQL with interface-based Spring Data projection** for the risk dashboard query. The dashboard row requires COUNT aggregates across multiple tables; JPQL cannot express cross-entity aggregates cleanly.

**Pattern**:
```sql
-- PatientRiskRow projection interface
SELECT
    p.patient_id         AS patientId,
    p.first_name || ' ' || p.last_name AS patientName,
    p.blood_group        AS bloodGroup,
    COALESCE(SUM(CASE WHEN ca.severity = 'CRITICAL' AND ca.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS criticalAlertCount,
    COALESCE(SUM(CASE WHEN ca.severity = 'WARNING'  AND ca.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS warningAlertCount,
    -- active meds, problems, allergies subqueries
    ...
FROM patients p
LEFT JOIN clinical_alerts ca ON ca.patient_id = p.patient_id
WHERE p.status = 'ACTIVE'
  AND (:doctorId IS NULL OR p.patient_id IN (SELECT a.patient_id FROM appointments a WHERE a.doctor_id = :doctorId))
GROUP BY p.patient_id, p.first_name, p.last_name, p.blood_group
ORDER BY criticalAlertCount DESC, news2Score DESC NULLS LAST, warningAlertCount DESC
```

**NEWS2 score in dashboard**: Computed from latest vitals in application code (fetch top-1 vitals per patient and compute) — not in SQL — to keep the NEWS2 algorithm in Java. For the dashboard query, `news2Score` is populated after the SQL query returns.

**Rationale**:
- Projection interface (not entity): avoids loading full entity for dashboard rows.
- Native SQL: NULLS LAST not supported in JPQL ORDER BY; COUNT(CASE WHEN) is cleaner in SQL.
- Two-step (SQL for counts, Java for NEWS2): keeps algorithm centralized and testable.

**Alternatives considered**:
- @SqlResultSetMapping: Verbose, error-prone XML mapping. Rejected.
- Multiple repository calls per patient: N+1 problem at scale. Rejected.
- Caching NEWS2 in DB: Premature optimization; adds stale-data risk. Rejected.

---

## 6. Doctor-Scoped Filtering

**Decision**: Use a subquery on `appointments` table to scope patients.

```sql
-- DOCTOR role: patients with at least one appointment with this doctor
AND (:doctorId IS NULL OR p.patient_id IN (
    SELECT DISTINCT a.patient_id FROM appointments a
    WHERE a.doctor_id = :doctorId
))
```

**Rationale**: `doctorId` comes from `AuthContext.Holder.get().getUserId()`. ADMIN passes `doctorId = null` to bypass the filter. Clean and reusable.

---

## 7. Audit Strategy for Module 6

**Decision**: Reuse existing `EmrAuditService` for all Module 6 mutations, adding new entity types: `LAB_ORDER`, `LAB_RESULT`, `CLINICAL_ALERT`.

**Rationale**: `EmrAuditService` already handles the HIPAA-compliant pattern (no PHI in audit logs, only field names and action context). No new audit service needed — just new entityType constants.

---

## 8. Frontend Integration Pattern

**Decision**: Follow existing TanStack Query v5 + Axios pattern. New API hooks:
- `useLabOrders(patientId, status)` — GET /api/v1/patients/{id}/lab-orders
- `useNews2(patientId)` — GET /api/v1/patients/{id}/news2
- `useAlerts(patientId)` — GET /api/v1/patients/{id}/alerts
- `useRiskDashboard(page)` — GET /api/v1/dashboard/patient-risk
- `useDashboardStats()` — GET /api/v1/dashboard/stats

**Polling**: Per spec (no WebSocket), use `refetchInterval: 30000` (30s) for alerts and dashboard.

---

## Unresolved Items

None. All NEEDS CLARIFICATION markers from the spec were resolved:
1. Doctor-scoped filtering → via appointments.doctor_id subquery (see §6)
2. NEWS2 in dashboard → computed in Java, not SQL (see §5)
3. Alert deduplication race condition → @Transactional service method (see §4)
