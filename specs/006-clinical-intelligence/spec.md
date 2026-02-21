# Feature Specification: Clinical Intelligence & Safety Module

**Feature Branch**: `006-clinical-intelligence`
**Created**: 2026-02-21
**Status**: Draft
**Company**: Ai Nexus
**System**: Hospital Management System

---

## Overview

Module 6 introduces a proactive clinical intelligence layer that operates on top of existing patient data (vitals, medications, allergies, appointments) to detect patient deterioration and dangerous drug combinations **before** they become crises. The system becomes self-aware — it does not wait for clinicians to notice problems; it flags them automatically and ranks patients by risk in real time.

Modules 1–5 (Patient, Auth, Appointments, Billing, EMR) are **FROZEN**. All existing entities must remain unmodified. This module reads from existing data and adds new intelligence entities on top.

---

## User Scenarios & Testing

### User Story 1 — Lab Orders & Results (Priority: P1)

A doctor working a morning ward round needs to order a blood panel for a deteriorating patient. They create a lab order specifying the test, urgency level, and optionally link it to today's appointment. A nurse collects the specimen and later records the result. If the potassium level comes back critically high, the system instantly creates a CRITICAL alert without the doctor having to manually check the lab result list.

**Why this priority**: Lab results are the most time-critical diagnostic data in a hospital. A missed CRITICAL_HIGH potassium result can cause cardiac arrest within hours. Automating the alert removes the human failure mode. This is P1 because it provides immediate, life-safety value independently of all other stories.

**Independent Test**: Can be fully tested by: (1) creating a lab order as DOCTOR, (2) recording a CRITICAL_HIGH result as NURSE, (3) verifying a CRITICAL clinical alert is auto-created — all without any other module 6 feature being active.

**Acceptance Scenarios**:

1. **Given** a doctor is authenticated, **When** they submit a lab order for a patient with category HEMATOLOGY and priority STAT, **Then** the order is created with status PENDING and orderedBy captured from the auth context.

2. **Given** an existing lab order in PENDING status, **When** a nurse records a result with interpretation CRITICAL_HIGH, **Then** the order status advances to RESULTED and a ClinicalAlert with severity=CRITICAL and type=LAB_CRITICAL is automatically created for the patient.

3. **Given** an existing lab order, **When** a nurse records a result with interpretation HIGH or LOW, **Then** a ClinicalAlert with severity=WARNING and type=LAB_ABNORMAL is automatically created.

4. **Given** an existing lab order, **When** a result with interpretation NORMAL is recorded, **Then** no ClinicalAlert is created and the order status becomes RESULTED.

5. **Given** a RECEPTIONIST user, **When** they attempt to create or view lab orders, **Then** the system returns 403 Forbidden.

6. **Given** a doctor filters lab orders by status=PENDING, **When** the query executes, **Then** only orders in PENDING status are returned for that patient.

7. **Given** a lab order already in RESULTED status, **When** a second result recording is attempted for the same order, **Then** the system returns a conflict error (the order is immutable once resulted).

---

### User Story 2 — NEWS2 Early Warning Score (Priority: P1)

A nurse has just recorded vitals for a post-operative patient — respiratory rate 26/min, SpO2 90%, temperature 38.9 degrees C. Without NEWS2, those numbers sit in a list. With NEWS2, the system instantly computes a total score of 9, classifies the patient as HIGH risk (red), and automatically creates a CRITICAL alert. The ward nurse seeing the alert escalates within minutes, preventing respiratory failure.

**Why this priority**: NEWS2 is the NHS standard early warning score used in every acute hospital in the UK. It synthesises six vital parameters into a single actionable risk score that guides escalation decisions. Computing it automatically means deteriorating patients are never missed. P1 because it uses existing vitals data and provides immediate triage value without requiring any new data collection.

**Independent Test**: Can be fully tested by: recording vitals for a patient via existing EMR module endpoints, then calling GET /news2 and verifying the score, risk level, colour, recommendation, and component breakdown are correct per the NHS algorithm.

**Acceptance Scenarios**:

1. **Given** a patient with recorded vitals (RR=14, SpO2=97, SBP=120, HR=75, Temp=37.0), **When** NEWS2 is computed, **Then** totalScore=0, riskLevel=LOW, riskColour=green, recommendation="Routine ward monitoring".

2. **Given** a patient with vitals (RR=26, SpO2=90, SBP=95, HR=115, Temp=38.9), **When** NEWS2 is computed, **Then** totalScore is 10 or higher, riskLevel=HIGH, riskColour=red, and a ClinicalAlert with severity=CRITICAL and type=NEWS2_CRITICAL is created.

3. **Given** a patient with riskLevel=HIGH and an existing ACTIVE NEWS2_CRITICAL alert, **When** NEWS2 is recomputed after new vitals, **Then** the old alert is auto-dismissed and a new ACTIVE alert replaces it (deduplication).

4. **Given** a patient with vitals scoring total 3 with no single parameter scoring 3, **When** NEWS2 is computed, **Then** riskLevel=LOW_MEDIUM, no alert is created.

5. **Given** a patient with vitals where any single parameter scores 3 (e.g., RR=8) and total is 1 to 4, **When** NEWS2 is computed, **Then** riskLevel=MEDIUM and a WARNING alert type=NEWS2_HIGH is created.

6. **Given** a patient with no vitals on record, **When** GET /news2 is called, **Then** response is totalScore null, riskLevel NO_DATA, message "No vitals on record" and no alert is created.

7. **Given** a RECEPTIONIST user, **When** they call GET /news2, **Then** 403 Forbidden is returned.

8. **Given** NEWS2 is computed for a patient with riskLevel=MEDIUM, **When** NEWS2 is recomputed later with riskLevel=LOW, **Then** the existing NEWS2_HIGH alert is dismissed (not left dangling as a stale active alert).

---

### User Story 3 — Drug Interaction & Allergy Contraindication Checker (Priority: P2)

A doctor is about to prescribe Warfarin to a post-surgical patient. Before adding it to the medication list, they run an interaction check. The system instantly checks Warfarin against the patient's active medications (already on Aspirin) and their recorded allergies. It returns a MAJOR bleeding risk interaction (Warfarin + Aspirin) with mechanism and recommendation. The doctor sees this before prescribing. A DRUG_INTERACTION alert is created and appears in the alert feed.

**Why this priority**: Drug interactions are a leading cause of preventable hospital harm. A real-time check at the point of prescribing is the most effective intervention point. P2 because it requires the EMR medication list from Module 5 but does not require lab orders or NEWS2.

**Independent Test**: Can be fully tested by: (1) recording active medications for a patient via EMR module, (2) recording an allergy via EMR module, (3) calling POST /interaction-check with a drug that interacts, (4) verifying the response includes the correct interaction entries, safe=false, and a DRUG_INTERACTION alert exists.

**Acceptance Scenarios**:

1. **Given** a patient on Aspirin, **When** a doctor runs interaction-check for Warfarin, **Then** the response contains an interaction entry with severity=MAJOR, drug1=Warfarin, drug2=Aspirin, and safe=false.

2. **Given** a patient with a Penicillin allergy, **When** a doctor checks Amoxicillin, **Then** allergyContraindications contains an entry matching the Penicillin allergy record and the response includes safe=false.

3. **Given** a patient on no medications and no allergies, **When** interaction-check is run for Paracetamol, **Then** response is interactions empty, allergyContraindications empty, safe=true.

4. **Given** a MAJOR interaction is detected, **When** the check completes, **Then** a ClinicalAlert with severity=CRITICAL and type=DRUG_INTERACTION is auto-created for the patient.

5. **Given** a CONTRAINDICATED interaction is detected such as SSRIs plus MAOIs, **When** the check completes, **Then** a ClinicalAlert with severity=CRITICAL and type=DRUG_INTERACTION is auto-created.

6. **Given** a MODERATE interaction is detected, **When** the check completes, **Then** no ClinicalAlert is auto-created (only MAJOR and CONTRAINDICATED trigger alerts).

7. **Given** a doctor calls GET /interaction-summary, **When** the patient is on Warfarin and Aspirin, **Then** the summary includes all known interactions across the full active medication list.

8. **Given** a NURSE calls GET /interaction-summary, **When** they access the endpoint, **Then** 200 OK is returned (nurses can view summary).

9. **Given** a NURSE calls POST /interaction-check, **When** they attempt to run a check, **Then** 403 Forbidden is returned (only DOCTOR and ADMIN can initiate checks).

---

### User Story 4 — Clinical Alerts Feed (Priority: P2)

A doctor arriving for a shift opens the alerts dashboard. They see 3 CRITICAL alerts at the top (2 lab criticals, 1 NEWS2 CRITICAL for a patient in bay 4) and 5 WARNING alerts below. They acknowledge the NEWS2 alert after reviewing the patient and dismiss a LAB_ABNORMAL alert they have already actioned with a note explaining the clinical context. The nurse, viewing the same feed, sees all alerts for all patients.

**Why this priority**: The alerts feed is the central triage surface that aggregates signals from US1, US2, and US3. P2 because it has no value without at least US1 or US2 generating alerts, but once alerts exist it is the primary workflow for clinicians to act on them.

**Independent Test**: Can be fully tested by: manually seeding a clinical_alert record, then verifying GET /patients/{id}/alerts returns it, PATCH /acknowledge sets acknowledgedAt, and PATCH /dismiss sets dismissedAt with the supplied reason.

**Acceptance Scenarios**:

1. **Given** a patient has 3 ACTIVE alerts (2 CRITICAL, 1 WARNING), **When** GET /patients/{id}/alerts is called, **Then** all 3 alerts are returned in the response.

2. **Given** a doctor calls PATCH /alerts/{id}/acknowledge, **When** the alert is ACTIVE, **Then** acknowledgedAt is set to current timestamp and acknowledgedBy is set from auth context; status remains ACTIVE.

3. **Given** a doctor calls PATCH /alerts/{id}/dismiss with a reason, **When** the alert exists, **Then** status=DISMISSED, dismissedAt and dismissReason are persisted.

4. **Given** a DOCTOR calls GET /alerts (global feed), **When** they have appointments with patients A and B, **Then** only alerts for patients A and B are returned (role-scoped view).

5. **Given** an ADMIN calls GET /alerts, **When** filtering by severity=CRITICAL, **Then** only CRITICAL-severity alerts across all patients are returned.

6. **Given** a NURSE calls GET /alerts, **When** no filter is applied, **Then** alerts for ALL patients are returned.

7. **Given** a RECEPTIONIST calls any alerts endpoint, **When** the request is processed, **Then** 403 Forbidden is returned.

8. **Given** GET /alerts is called with status=DISMISSED, **When** the response is returned, **Then** only alerts with status=DISMISSED are included.

---

### User Story 5 — Patient Risk Dashboard (Priority: P2)

A doctor opening their shift sees a risk-ranked list of all their patients: at the top is a patient with 2 CRITICAL alerts and NEWS2 score 9 (red), below is a patient with 1 WARNING alert and NEWS2 score 5 (orange), at the bottom are stable patients with scores of 0. Clicking the stats card shows system-wide numbers: 47 active patients, 3 with critical alerts, 1 with HIGH NEWS2. This gives the entire clinical team shared situational awareness.

**Why this priority**: The dashboard aggregates and visualises risk signals from US1 through US4. It is the highest-leverage view for clinical handover and shift management. P2 because it requires alerts and NEWS2 to have value, but provides the strategic overview that makes the whole module coherent.

**Independent Test**: Can be fully tested by: seeding patients with known alert counts and NEWS2 scores, then calling GET /dashboard/patient-risk and verifying the sort order (criticalAlertCount DESC, news2Score DESC, warningAlertCount DESC) and GET /dashboard/stats and verifying aggregate counts.

**Acceptance Scenarios**:

1. **Given** 3 patients — Patient A with 2 critical alerts and NEWS2=9, Patient B with 0 critical and NEWS2=5 and 1 warning, Patient C with 0 alerts and NEWS2=0 — **When** GET /dashboard/patient-risk is called, **Then** the order is A, B, C.

2. **Given** an ADMIN calls GET /dashboard/patient-risk, **When** the list is returned, **Then** all patients in the system appear (not scoped to appointments).

3. **Given** a DOCTOR calls GET /dashboard/patient-risk, **When** the list is returned, **Then** only patients from their own appointments appear.

4. **Given** GET /dashboard/stats is called, **When** there are 47 active patients, 3 with CRITICAL alerts, and 1 with HIGH NEWS2, **Then** the response includes all these figures plus an alertsByType breakdown.

5. **Given** a RECEPTIONIST calls any dashboard endpoint, **When** the request is processed, **Then** 403 Forbidden is returned.

6. **Given** a patient has no vitals on record, **When** they appear on the risk dashboard, **Then** news2Score=null and news2RiskLevel=NO_DATA (not an error or missing row).

7. **Given** the dashboard is paginated with default size=20, **When** there are 50 patients, **Then** page 0 returns 20 patients sorted by risk rank.

---

### Edge Cases

- What happens when a lab order is cancelled before a result is recorded? The order moves to CANCELLED status (terminal); no result can be recorded; no alert is created.
- What if the same drug appears in both active medications and the drug being checked? The interaction checker ignores self-matching; it only checks the new drug against other active medications.
- What if SpO2 is missing from vitals when NEWS2 is computed? That parameter contributes a score of 0 and the response notes which parameters were absent or defaulted.
- What if a patient has both a NEWS2_HIGH and NEWS2_CRITICAL alert and then scores LOW? Both active NEWS2 alerts are dismissed when the new computation produces a risk level below MEDIUM.
- What if a drug interaction check is run for a drug with no known interactions in the database? Response is interactions empty, allergyContraindications empty unless allergy match exists, safe=true.
- What if the allergy substance is a partial match (e.g., "Penicillin V" when drug is "Penicillin")? Fuzzy substring match catches it using case-insensitive contains logic.
- What if two concurrent NEWS2 computations race to create an alert? The service layer deduplication logic prevents duplicates; only one ACTIVE alert per (patientId, alertType) is permitted.
- What if a patient is discharged (status=INACTIVE) but has open alerts? Alerts remain queryable but the patient no longer appears in the active patient risk dashboard.

---

## Requirements

### Functional Requirements

**Lab Orders and Results (US1)**

- **FR-001**: The system MUST allow DOCTOR and ADMIN roles to create lab orders for a patient, specifying test name, category, priority, and optional appointment linkage.
- **FR-002**: The system MUST allow NURSE, DOCTOR, and ADMIN roles to record results against an existing lab order, including value, unit, reference range, and interpretation.
- **FR-003**: The system MUST automatically advance lab order status: PENDING when created, status moves to RESULTED when a result is recorded.
- **FR-004**: The system MUST automatically create a CRITICAL ClinicalAlert when a lab result interpretation is CRITICAL_LOW or CRITICAL_HIGH.
- **FR-005**: The system MUST automatically create a WARNING ClinicalAlert when a lab result interpretation is HIGH or LOW.
- **FR-006**: The system MUST allow all clinical roles (DOCTOR, NURSE, ADMIN) to retrieve lab orders and lab results for a patient; RECEPTIONIST access is forbidden.
- **FR-007**: The system MUST support filtering lab orders by status (PENDING, IN_PROGRESS, RESULTED, CANCELLED).
- **FR-008**: Lab results MUST be returned paginated, sorted by resultedAt descending.

**NEWS2 Early Warning Score (US2)**

- **FR-009**: The system MUST compute a NEWS2 score from the patient's most recently recorded vitals using the standard NHS NEWS2 algorithm covering six parameters: respiratory rate, SpO2, systolic blood pressure, heart rate, temperature, and consciousness (defaulted to ALERT).
- **FR-010**: The system MUST classify the NEWS2 total score into a risk level: LOW for score 0, LOW_MEDIUM for 1-4 without any parameter score of 3, MEDIUM for 1-4 with any parameter score of 3 or total 5-6, HIGH for 7 or above.
- **FR-011**: The system MUST return the NEWS2 response with: totalScore, riskLevel, riskColour, recommendation, components per-parameter breakdown, basedOnVitalsId, computedAt.
- **FR-012**: The system MUST automatically create a WARNING ClinicalAlert (type=NEWS2_HIGH) when riskLevel=MEDIUM, subject to deduplication.
- **FR-013**: The system MUST automatically create a CRITICAL ClinicalAlert (type=NEWS2_CRITICAL) when riskLevel=HIGH, subject to deduplication.
- **FR-014**: The system MUST deduplicate NEWS2 alerts: only one ACTIVE alert per (patientId, alertType) is allowed; creating a new one auto-dismisses the existing one.
- **FR-015**: The system MUST return a structured NO_DATA response when no vitals are on record, without creating any alert.
- **FR-016**: NEWS2 computation MUST be accessible to DOCTOR, NURSE, and ADMIN roles; RECEPTIONIST is forbidden.

**Drug Interaction and Allergy Checker (US3)**

- **FR-017**: The system MUST maintain a built-in curated database of at least 40 clinically significant drug-drug interaction pairs, covering anticoagulants, cardiac drugs, CNS agents, antibiotics, diabetes medications, respiratory drugs, and common OTC risks.
- **FR-018**: The system MUST allow DOCTOR and ADMIN to run an interaction check for a named drug against a patient's full active medication list and recorded allergy list.
- **FR-019**: Interaction check MUST return: the checked drug name, all matching interaction pairs with severity, mechanism, clinical effect, and recommendation, all allergy contraindications, a safe boolean, and a checkedAt timestamp.
- **FR-020**: Allergy contraindication matching MUST use case-insensitive substring matching between the drug name and allergy substance names.
- **FR-021**: The system MUST automatically create a CRITICAL ClinicalAlert when a MAJOR or CONTRAINDICATED interaction is detected.
- **FR-022**: The system MUST allow DOCTOR, NURSE, and ADMIN to retrieve the interaction summary across all active medications for a patient.
- **FR-023**: RECEPTIONIST role MUST be forbidden from all interaction-check endpoints.

**Clinical Alerts Feed (US4)**

- **FR-024**: The system MUST provide a per-patient alert feed returning all alerts for a given patient regardless of status.
- **FR-025**: The system MUST provide a global alert feed filterable by status and severity; DOCTOR sees only alerts for patients from their own appointments; NURSE and ADMIN see all.
- **FR-026**: The system MUST allow DOCTOR and ADMIN to acknowledge an alert, recording acknowledgedAt and acknowledgedBy from the auth context.
- **FR-027**: The system MUST allow DOCTOR and ADMIN to dismiss an alert with a mandatory reason, recording dismissedAt and dismissReason.
- **FR-028**: Each alert MUST capture: id (UUID), patientId, patientName, alertType, severity, title, description, source, optional triggerValue, status, createdAt, and optional acknowledgement and dismissal fields.
- **FR-029**: RECEPTIONIST role MUST be forbidden from all alert endpoints.

**Patient Risk Dashboard (US5)**

- **FR-030**: The system MUST provide a paginated risk-ranked patient list with default page size 20, sorted by: criticalAlertCount DESC, then news2Score DESC NULLS LAST, then warningAlertCount DESC.
- **FR-031**: Each row in the risk dashboard MUST include: patientId, patientName, bloodGroup, news2Score, news2RiskLevel, news2RiskColour, criticalAlertCount, warningAlertCount, activeMedicationCount, activeProblemCount, activeAllergyCount, lastVitalsAt, lastVisitDate.
- **FR-032**: DOCTOR role MUST see only patients from their own appointments; ADMIN MUST see all patients.
- **FR-033**: The system MUST provide a system-wide statistics snapshot including: totalActivePatients, patientsWithCriticalAlerts, patientsWithHighNews2, totalActiveAlerts, totalCriticalAlerts, totalWarningAlerts, alertsByType breakdown.
- **FR-034**: RECEPTIONIST role MUST be forbidden from all dashboard endpoints.

**Cross-Cutting**

- **FR-035**: Every write operation (create lab order, record result, acknowledge alert, dismiss alert) MUST produce an audit log entry.
- **FR-036**: The drug interaction database MUST be a static in-memory component — no external API calls, no additional database tables.
- **FR-037**: Existing Flyway migrations (V1 through V21) MUST NOT be modified; new tables use Flyway V22 and above.
- **FR-038**: Existing entities (Patient, Appointment, Invoice, PatientVitals, PatientProblem, PatientMedication, PatientAllergy) MUST NOT be modified.

### Key Entities

- **LabOrder**: Represents a clinician's request for a laboratory test. Key attributes: UUID id, patient reference, optional appointment reference, test name, test code, category (HEMATOLOGY, CHEMISTRY, MICROBIOLOGY, IMMUNOLOGY, URINALYSIS, OTHER), priority (ROUTINE, URGENT, STAT), status (PENDING, IN_PROGRESS, RESULTED, CANCELLED), ordered by, ordered at, optional notes, optional cancellation reason.

- **LabResult**: The recorded outcome of a lab order. One-to-one with LabOrder. Key attributes: UUID id, order reference, patient reference, result value (text allowing numeric or descriptive), unit, optional reference range low and high, interpretation (NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH, ABNORMAL), optional result notes, resulted by, resulted at.

- **ClinicalAlert**: A unified safety notification generated by any clinical intelligence engine. Key attributes: UUID id, patient reference, alert type (LAB_CRITICAL, LAB_ABNORMAL, NEWS2_HIGH, NEWS2_CRITICAL, DRUG_INTERACTION, ALLERGY_CONTRAINDICATION), severity (INFO, WARNING, CRITICAL), title, description, source (human-readable origin), optional trigger value, status (ACTIVE, ACKNOWLEDGED, DISMISSED), created at, optional acknowledged by and at, optional dismissed at and reason.

- **News2Result** (computed, not persisted): The output of running the NEWS2 algorithm against a patient's latest vitals. Contains: total score, risk level, risk colour, recommendation text, per-parameter component breakdown, the vitals record ID it was based on, and computation timestamp.

- **DrugInteraction** (in-memory, not persisted): Represents a known clinically significant drug pair. Attributes: drug1, drug2 (canonical names), severity (MINOR, MODERATE, MAJOR, CONTRAINDICATED), mechanism, clinical effect, recommendation text.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: A lab result with CRITICAL interpretation results in a ClinicalAlert being created within the same transaction — zero lag between result recording and alert availability.
- **SC-002**: NEWS2 score computation completes and returns a response within 500ms for any patient with vitals on record.
- **SC-003**: A drug interaction check against a 10-medication active list returns a complete response within 200ms.
- **SC-004**: The patient risk dashboard correctly ranks patients by risk score with 100% sort-order accuracy across all defined test scenarios.
- **SC-005**: NEWS2 alert deduplication prevents more than one ACTIVE alert of the same type per patient at any given time — verified across concurrent creation scenarios.
- **SC-006**: All 5 user stories are accessible to DOCTOR, NURSE (where applicable), and ADMIN roles, and all return 403 for RECEPTIONIST — verified by role matrix integration tests.
- **SC-007**: The drug interaction database contains a minimum of 40 curated drug pairs covering at least 6 clinical categories (anticoagulants, cardiac, CNS, antibiotics, diabetes, respiratory).
- **SC-008**: Zero modifications to existing Flyway migrations or existing entity classes — verified by confirming the git diff of those files remains empty.
- **SC-009**: All new API endpoints return structured error responses consistent with the existing GlobalExceptionHandler format.
- **SC-010**: Clinical alert feed for a patient with 50 alerts is retrievable in under 300ms.

---

## Assumptions

1. The drug interaction database uses canonical drug names (e.g., Warfarin, Aspirin). Fuzzy matching applies only for allergy contraindications using substring matching; exact canonical name matching is used for drug-drug interactions.
2. NEWS2 Consciousness parameter defaults to ALERT (score=0) as the AVPU scale is not yet captured in the PatientVitals schema. This is documented in the response.
3. The patient risk dashboard computes NEWS2 on-the-fly for each patient using their latest recorded vitals — it does not cache scores.
4. Lab order cancellation is a valid terminal status; the cancellation reason is optional.
5. A doctor seeing only their own appointment patients is defined as: patients for whom at least one appointment exists with the current doctor's username as the treating clinician.
6. The interaction summary (GET /interaction-summary) runs all pairwise checks across the patient's active medication list and returns all known interactions — it does not check against a new drug.
7. Alert deduplication for NEWS2 is strictly by (patientId, alertType). Other alert types (LAB_CRITICAL, DRUG_INTERACTION) are not deduplicated; each event creates a new alert.

---

## Out of Scope

- External HL7 lab system integration
- DICOM or radiology imaging
- ML-based predictive models
- WebSocket or SSE real-time push (polling-based frontend)
- Barcode or specimen label printing
- Drug dosage calculation
- Pharmacy dispensing
- NEWS2 Scale 2 (supplemental oxygen adjustment)
- AVPU consciousness scoring in NEWS2 (requires vitals schema change which is frozen)
- Drug interaction severity grading beyond MINOR, MODERATE, MAJOR, CONTRAINDICATED
