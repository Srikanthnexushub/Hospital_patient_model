# Quickstart & Integration Scenarios: Clinical Intelligence & Safety Module

**Feature**: 006-clinical-intelligence
**Date**: 2026-02-21

These scenarios guide integration testing and frontend development. Each scenario is end-to-end — it starts from authentication and exercises the full stack.

---

## Scenario 1: Lab Order Lifecycle → Auto-Alert (US1)

**Goal**: Verify that a CRITICAL lab result automatically generates a CRITICAL alert.

### Prerequisites
- Patient `P2025001` exists (status=ACTIVE)
- DOCTOR user `dr.smith` authenticated (JWT)
- NURSE user `nurse.jones` authenticated (JWT)

### Steps

**Step 1** — Doctor creates a STAT lab order:
```
POST /api/v1/patients/P2025001/lab-orders
Authorization: Bearer {dr.smith JWT}
{
  "testName": "Serum Potassium",
  "category": "CHEMISTRY",
  "priority": "STAT"
}
→ 201 Created
   Save: orderId = "..."
```

**Step 2** — Nurse records a CRITICAL_HIGH result:
```
POST /api/v1/lab-orders/{orderId}/result
Authorization: Bearer {nurse.jones JWT}
{
  "value": "7.2",
  "unit": "mmol/L",
  "referenceRangeLow": 3.5,
  "referenceRangeHigh": 5.0,
  "interpretation": "CRITICAL_HIGH"
}
→ 201 Created
   Verify: alertCreated = true, alertId present
```

**Step 3** — Verify alert exists:
```
GET /api/v1/patients/P2025001/alerts?status=ACTIVE&severity=CRITICAL
→ 200 OK
   Verify: at least 1 alert with alertType=LAB_CRITICAL, severity=CRITICAL
   Verify: triggerValue = "7.2 mmol/L"
```

**Step 4** — Doctor acknowledges the alert:
```
PATCH /api/v1/alerts/{alertId}/acknowledge
Authorization: Bearer {dr.smith JWT}
→ 200 OK
   Verify: acknowledgedBy = "dr.smith", acknowledgedAt != null, status = ACTIVE
```

**Step 5** — Doctor dismisses the alert:
```
PATCH /api/v1/alerts/{alertId}/dismiss
{ "reason": "Reviewed — repeat test confirmed; patient started on calcium gluconate IV" }
→ 200 OK
   Verify: status = DISMISSED, dismissReason set
```

---

## Scenario 2: NEWS2 Full Score Cycle (US2)

**Goal**: Verify NEWS2 computes correctly, creates alerts, and deduplicates.

### Prerequisites
- Appointment `A2025001` for patient `P2025001` exists
- NURSE user authenticated

### Steps

**Step 1** — Record elevated vitals via EMR endpoint:
```
POST /api/v1/appointments/A2025001/vitals
Authorization: Bearer {nurse.jones JWT}
{
  "respiratoryRate": 26,
  "oxygenSaturation": 90,
  "bloodPressureSystolic": 95,
  "bloodPressureDiastolic": 60,
  "heartRate": 115,
  "temperature": 38.9
}
→ 201 Created
```

**Step 2** — Compute NEWS2:
```
GET /api/v1/patients/P2025001/news2
→ 200 OK
   Verify: totalScore >= 7, riskLevel = HIGH, riskColour = red
   Verify: components[0].parameter = "Respiratory Rate", score = 3
   Verify: alertCreated = true, type = NEWS2_CRITICAL
   Save: alertId1
```

**Step 3** — Update vitals to improve (patient stable):
```
POST /api/v1/appointments/A2025001/vitals
{
  "respiratoryRate": 16,
  "oxygenSaturation": 97,
  "bloodPressureSystolic": 120,
  "bloodPressureDiastolic": 75,
  "heartRate": 78,
  "temperature": 37.1
}
→ 201 Created
```

**Step 4** — Recompute NEWS2:
```
GET /api/v1/patients/P2025001/news2
→ 200 OK
   Verify: totalScore = 0, riskLevel = LOW
   Verify: alertCreated = false (no alert for LOW)
```

**Step 5** — Confirm old CRITICAL alert was dismissed:
```
GET /api/v1/patients/P2025001/alerts
→ 200 OK
   Verify: alertId1 has status = DISMISSED, dismissReason contains "superseded" or "auto-dismissed"
```

---

## Scenario 3: Drug Interaction Check → MAJOR Alert (US3)

**Goal**: Verify interaction checker detects MAJOR interaction and creates alert.

### Prerequisites
- Patient `P2025001` with active medication Aspirin (via EMR module)
- DOCTOR user authenticated

### Steps

**Step 1** — Check Warfarin interaction:
```
POST /api/v1/patients/P2025001/interaction-check
Authorization: Bearer {dr.smith JWT}
{ "drugName": "Warfarin" }
→ 200 OK
   Verify: interactions contains entry with drug2=Aspirin, severity=MAJOR
   Verify: safe = false
   Verify: alertCreated = true, type = DRUG_INTERACTION
```

**Step 2** — Check paracetamol (safe drug):
```
POST /api/v1/patients/P2025001/interaction-check
{ "drugName": "Paracetamol" }
→ 200 OK
   Verify: interactions = [], safe = true (no aspirin interaction in DB)
   Verify: alertCreated = false
```

**Step 3** — View interaction summary:
```
GET /api/v1/patients/P2025001/interaction-summary
→ 200 OK
   Verify: activeMedicationCount >= 1
   Verify: interactions list shows all known interactions across active meds
```

**Step 4** — NURSE tries to initiate a check (should fail):
```
POST /api/v1/patients/P2025001/interaction-check
Authorization: Bearer {nurse.jones JWT}
{ "drugName": "Warfarin" }
→ 403 Forbidden
```

---

## Scenario 4: Allergy Contraindication (US3)

**Goal**: Verify allergy matching via fuzzy substring logic.

### Prerequisites
- Patient `P2025001` with active Penicillin allergy (substance="Penicillin", severity=SEVERE, reaction="Anaphylaxis")
- DOCTOR user authenticated

### Steps

**Step 1** — Check Amoxicillin (cross-reacts with Penicillin):
```
POST /api/v1/patients/P2025001/interaction-check
{ "drugName": "Amoxicillin" }
→ 200 OK
   Verify: allergyContraindications contains entry with substance="Penicillin"
   Verify: safe = false
   Verify: alertCreated = true, type = ALLERGY_CONTRAINDICATION
```

**Step 2** — Check Amoxicillin with case variation:
```
POST /api/v1/patients/P2025001/interaction-check
{ "drugName": "AMOXICILLIN" }
→ 200 OK (case-insensitive match)
   Verify: allergyContraindications not empty
```

---

## Scenario 5: Patient Risk Dashboard (US5)

**Goal**: Verify risk-ranked dashboard and stats endpoint.

### Prerequisites
- Multiple patients seeded with different risk profiles
- DOCTOR and ADMIN users authenticated

### Steps

**Step 1** — ADMIN views full dashboard:
```
GET /api/v1/dashboard/patient-risk?page=0&size=20
Authorization: Bearer {admin JWT}
→ 200 OK
   Verify: patients sorted by criticalAlertCount DESC, then news2Score DESC NULLS LAST
   Verify: first patient has highest criticalAlertCount
   Verify: patients with news2Score=null appear last
```

**Step 2** — DOCTOR views scoped dashboard:
```
GET /api/v1/dashboard/patient-risk
Authorization: Bearer {dr.smith JWT}
→ 200 OK
   Verify: only patients with at least one appointment with dr.smith appear
```

**Step 3** — View system stats:
```
GET /api/v1/dashboard/stats
Authorization: Bearer {admin JWT}
→ 200 OK
   Verify: totalActivePatients > 0
   Verify: alertsByType is a non-empty array
   Verify: totalCriticalAlerts + totalWarningAlerts = totalActiveAlerts
```

**Step 4** — NURSE tries to access dashboard (should fail):
```
GET /api/v1/dashboard/patient-risk
Authorization: Bearer {nurse.jones JWT}
→ 403 Forbidden
```

---

## Scenario 6: RBAC Boundary Verification (All US)

**Goal**: Confirm RECEPTIONIST is blocked from all Module 6 endpoints.

```
All of the following return 403 Forbidden for RECEPTIONIST:
  GET  /api/v1/patients/{id}/lab-orders
  POST /api/v1/patients/{id}/lab-orders
  POST /api/v1/lab-orders/{id}/result
  GET  /api/v1/patients/{id}/lab-results
  GET  /api/v1/patients/{id}/news2
  POST /api/v1/patients/{id}/interaction-check
  GET  /api/v1/patients/{id}/interaction-summary
  GET  /api/v1/patients/{id}/alerts
  GET  /api/v1/alerts
  PATCH /api/v1/alerts/{id}/acknowledge
  PATCH /api/v1/alerts/{id}/dismiss
  GET  /api/v1/dashboard/patient-risk
  GET  /api/v1/dashboard/stats
```

---

## Test Data Requirements

For integration tests use a Testcontainers PostgreSQL instance and seed:
- 2 patients: `P2025001` (active), `P2025002` (active)
- 1 appointment: `A2025001` (patient=P2025001, doctor=dr.smith)
- 1 active medication for P2025001: Aspirin 100mg
- 1 active allergy for P2025001: Penicillin (SEVERE)
- Users: dr.smith (DOCTOR), nurse.jones (NURSE), admin (ADMIN), receptionist1 (RECEPTIONIST)

Vitals are recorded during test execution (not seeded) to test NEWS2 with fresh data.
