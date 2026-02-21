# Data Model: Clinical Intelligence & Safety Module

**Feature**: 006-clinical-intelligence
**Date**: 2026-02-21

---

## New Entities (Flyway V22+)

> Existing entities (`Patient`, `Appointment`, `PatientVitals`, `PatientMedication`, `PatientAllergy`, etc.) are **FROZEN** — no modifications allowed.

---

### LabOrder

**Table**: `lab_orders`
**PK**: UUID (Java `GenerationType.UUID`, PostgreSQL `uuid` column type)

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Generated UUID |
| patient_id | VARCHAR(14) | FK → patients.patient_id, NOT NULL | Patient reference |
| appointment_id | VARCHAR(14) | FK → appointments.appointment_id, NULLABLE | Optional link to appointment |
| test_name | VARCHAR(200) | NOT NULL | Human-readable test name |
| test_code | VARCHAR(50) | NULLABLE | Standardized lab code (e.g., LOINC) |
| category | VARCHAR(30) | NOT NULL | HEMATOLOGY, CHEMISTRY, MICROBIOLOGY, IMMUNOLOGY, URINALYSIS, OTHER |
| priority | VARCHAR(20) | NOT NULL, DEFAULT 'ROUTINE' | ROUTINE, URGENT, STAT |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING, IN_PROGRESS, RESULTED, CANCELLED |
| ordered_by | VARCHAR(100) | NOT NULL | Username from auth context |
| ordered_at | TIMESTAMPTZ | NOT NULL | Auto-set on creation |
| notes | TEXT | NULLABLE | Clinical notes on the order |
| cancelled_reason | TEXT | NULLABLE | Required when status = CANCELLED |

**State Machine**: PENDING → IN_PROGRESS → RESULTED (terminal); PENDING → CANCELLED (terminal). RESULTED is immutable (no further state changes).

**Indexes**:
- `idx_lab_orders_patient_id` on `(patient_id)`
- `idx_lab_orders_patient_status` on `(patient_id, status)`

---

### LabResult

**Table**: `lab_results`
**PK**: UUID

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Generated UUID |
| order_id | UUID | FK → lab_orders.id, UNIQUE, NOT NULL | One result per order |
| patient_id | VARCHAR(14) | FK → patients.patient_id, NOT NULL | Denormalized for fast queries |
| value | TEXT | NOT NULL | Result value (numeric string or text) |
| unit | VARCHAR(50) | NULLABLE | Measurement unit (e.g., "mmol/L") |
| reference_range_low | NUMERIC(10,3) | NULLABLE | Lower bound of normal range |
| reference_range_high | NUMERIC(10,3) | NULLABLE | Upper bound of normal range |
| interpretation | VARCHAR(30) | NOT NULL | NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH, ABNORMAL |
| result_notes | TEXT | NULLABLE | Clinical comments on the result |
| resulted_by | VARCHAR(100) | NOT NULL | Username from auth context |
| resulted_at | TIMESTAMPTZ | NOT NULL | Auto-set when result is recorded |

**Indexes**:
- `idx_lab_results_patient_id` on `(patient_id, resulted_at DESC)`
- Unique constraint on `order_id` enforces one-to-one with LabOrder

---

### ClinicalAlert

**Table**: `clinical_alerts`
**PK**: UUID

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK, NOT NULL | Generated UUID |
| patient_id | VARCHAR(14) | FK → patients.patient_id, NOT NULL | Patient reference |
| alert_type | VARCHAR(40) | NOT NULL | LAB_CRITICAL, LAB_ABNORMAL, NEWS2_HIGH, NEWS2_CRITICAL, DRUG_INTERACTION, ALLERGY_CONTRAINDICATION |
| severity | VARCHAR(20) | NOT NULL | INFO, WARNING, CRITICAL |
| title | VARCHAR(200) | NOT NULL | Short alert title |
| description | TEXT | NOT NULL | Detailed alert description |
| source | VARCHAR(200) | NOT NULL | Human-readable origin (e.g., "Lab Result: Potassium 7.2 mmol/L") |
| trigger_value | VARCHAR(200) | NULLABLE | The value that triggered the alert |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, ACKNOWLEDGED, DISMISSED |
| created_at | TIMESTAMPTZ | NOT NULL | Auto-set on creation |
| acknowledged_at | TIMESTAMPTZ | NULLABLE | When acknowledged |
| acknowledged_by | VARCHAR(100) | NULLABLE | Username who acknowledged |
| dismissed_at | TIMESTAMPTZ | NULLABLE | When dismissed |
| dismiss_reason | TEXT | NULLABLE | Required when status = DISMISSED |

**Deduplication rule** (enforced in service layer, not DB constraint): For NEWS2 alert types (`NEWS2_HIGH`, `NEWS2_CRITICAL`), at most one ACTIVE alert per `(patient_id, alert_type)` pair at any time.

**Indexes**:
- `idx_clinical_alerts_patient_id` on `(patient_id)`
- `idx_clinical_alerts_patient_type_status` on `(patient_id, alert_type, status)` — supports deduplication query
- `idx_clinical_alerts_status_severity` on `(status, severity)` — supports global feed filtering

---

## In-Memory Structures (Not Persisted)

### DrugInteractionEntry (Java record)

Held in the `DrugInteractionDatabase @Component` — no DB table.

```
DrugInteractionEntry {
    String drug1          // canonical lowercase
    String drug2          // canonical lowercase
    InteractionSeverity severity   // MINOR, MODERATE, MAJOR, CONTRAINDICATED
    String mechanism
    String clinicalEffect
    String recommendation
}
```

### News2Result (Java record, response DTO)

Computed on-demand from `PatientVitals` — never persisted.

```
News2Result {
    Integer totalScore         // null if no vitals
    String riskLevel           // LOW, LOW_MEDIUM, MEDIUM, HIGH, NO_DATA
    String riskColour          // green, yellow, orange, red, grey
    String recommendation
    List<ComponentScore> components
    Long basedOnVitalsId       // PatientVitals.id used for computation
    OffsetDateTime computedAt
}

ComponentScore {
    String parameter    // "Respiratory Rate", "SpO2", etc.
    Number value        // actual measured value
    Integer score       // 0-3
    String unit         // "/min", "%", "mmHg", "bpm", "°C"
    Boolean defaulted   // true if parameter was missing (score defaults to 0)
}
```

---

## Relationships to Existing Entities

```
Patient (existing, FROZEN)
  ├── 1:N  LabOrder           (patient_id FK)
  ├── 1:N  LabResult          (patient_id FK, denormalized)
  ├── 1:N  ClinicalAlert      (patient_id FK)
  ├── 1:N  PatientVitals      (existing, read-only by NEWS2)
  ├── 1:N  PatientMedication  (existing, read-only by interaction checker)
  └── 1:N  PatientAllergy     (existing, read-only by allergy checker)

Appointment (existing, FROZEN)
  └── 1:0..1  LabOrder        (appointment_id FK, optional)

LabOrder (new)
  └── 1:0..1  LabResult       (order_id FK, UNIQUE)
```

---

## Enums (Java)

### LabOrderStatus
```
PENDING → IN_PROGRESS → RESULTED (terminal)
PENDING → CANCELLED (terminal)
```

### LabOrderPriority
`ROUTINE`, `URGENT`, `STAT`

### LabOrderCategory
`HEMATOLOGY`, `CHEMISTRY`, `MICROBIOLOGY`, `IMMUNOLOGY`, `URINALYSIS`, `OTHER`

### LabResultInterpretation
`NORMAL`, `LOW`, `HIGH`, `CRITICAL_LOW`, `CRITICAL_HIGH`, `ABNORMAL`

Alert-triggering interpretations:
- `CRITICAL_LOW` / `CRITICAL_HIGH` → CRITICAL severity, LAB_CRITICAL type
- `LOW` / `HIGH` → WARNING severity, LAB_ABNORMAL type
- `NORMAL` / `ABNORMAL` → no alert

### AlertType
`LAB_CRITICAL`, `LAB_ABNORMAL`, `NEWS2_HIGH`, `NEWS2_CRITICAL`, `DRUG_INTERACTION`, `ALLERGY_CONTRAINDICATION`

### AlertSeverity
`INFO`, `WARNING`, `CRITICAL`

### AlertStatus
`ACTIVE`, `ACKNOWLEDGED`, `DISMISSED`

### InteractionSeverity
`MINOR`, `MODERATE`, `MAJOR`, `CONTRAINDICATED`

Alert-triggering severities: `MAJOR`, `CONTRAINDICATED` → CRITICAL ClinicalAlert

### News2RiskLevel
`LOW`, `LOW_MEDIUM`, `MEDIUM`, `HIGH`, `NO_DATA`

### News2RiskColour
`green` (LOW), `yellow` (LOW_MEDIUM), `orange` (MEDIUM), `red` (HIGH), `grey` (NO_DATA)

---

## Flyway Migration Plan

| Migration | File | Content |
|---|---|---|
| V22 | `V22__create_lab_orders.sql` | Create `lab_orders` table + indexes |
| V23 | `V23__create_lab_results.sql` | Create `lab_results` table + indexes |
| V24 | `V24__create_clinical_alerts.sql` | Create `clinical_alerts` table + indexes |

All three migrations are additive — no changes to V1–V21.
