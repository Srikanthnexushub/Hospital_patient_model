# Contract: US5 — Patient Risk Dashboard

**Base URL**: `/api/v1`

---

## GET /dashboard/patient-risk

**Description**: Paginated, risk-ranked patient list. DOCTOR sees only their appointment patients; ADMIN sees all active patients.
**Roles**: DOCTOR, ADMIN

### Query Parameters
| Param | Type | Description |
|---|---|---|
| page | int | Default 0 |
| size | int | Default 20 |

### Response — 200 OK

```json
{
  "content": [
    {
      "patientId": "P2026001",
      "patientName": "Jane Doe",
      "bloodGroup": "O+",
      "news2Score": 9,
      "news2RiskLevel": "HIGH",
      "news2RiskColour": "red",
      "criticalAlertCount": 2,
      "warningAlertCount": 1,
      "activeMedicationCount": 5,
      "activeProblemCount": 3,
      "activeAllergyCount": 1,
      "lastVitalsAt": "2026-02-21T11:55:00Z",
      "lastVisitDate": "2026-02-21"
    },
    {
      "patientId": "P2026002",
      "patientName": "John Smith",
      "bloodGroup": "A+",
      "news2Score": 4,
      "news2RiskLevel": "LOW_MEDIUM",
      "news2RiskColour": "yellow",
      "criticalAlertCount": 0,
      "warningAlertCount": 2,
      "activeMedicationCount": 2,
      "activeProblemCount": 1,
      "activeAllergyCount": 0,
      "lastVitalsAt": "2026-02-20T14:30:00Z",
      "lastVisitDate": "2026-02-20"
    },
    {
      "patientId": "P2026003",
      "patientName": "Alice Brown",
      "bloodGroup": "B-",
      "news2Score": null,
      "news2RiskLevel": "NO_DATA",
      "news2RiskColour": "grey",
      "criticalAlertCount": 0,
      "warningAlertCount": 0,
      "activeMedicationCount": 1,
      "activeProblemCount": 0,
      "activeAllergyCount": 0,
      "lastVitalsAt": null,
      "lastVisitDate": "2026-01-15"
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

**Sort Order**: `criticalAlertCount DESC`, then `news2Score DESC NULLS LAST`, then `warningAlertCount DESC`

**Notes**:
- Only `ACTIVE` patients appear in the dashboard
- `news2Score` is computed from the patient's most recently recorded vitals
- `lastVisitDate` is derived from the most recent COMPLETED appointment
- DOCTOR role: list scoped to patients with at least one appointment with the requesting doctor

### Error Responses
- `403` — role not DOCTOR or ADMIN (NURSE cannot access risk dashboard)

---

## GET /dashboard/stats

**Description**: System-wide clinical snapshot. DOCTOR sees stats for their patient population; ADMIN sees system-wide stats.
**Roles**: DOCTOR, ADMIN

### No Request Body

### Response — 200 OK

```json
{
  "totalActivePatients": 47,
  "patientsWithCriticalAlerts": 3,
  "patientsWithHighNews2": 1,
  "totalActiveAlerts": 12,
  "totalCriticalAlerts": 5,
  "totalWarningAlerts": 7,
  "alertsByType": [
    {
      "alertType": "LAB_CRITICAL",
      "count": 2
    },
    {
      "alertType": "NEWS2_CRITICAL",
      "count": 1
    },
    {
      "alertType": "LAB_ABNORMAL",
      "count": 4
    },
    {
      "alertType": "NEWS2_HIGH",
      "count": 2
    },
    {
      "alertType": "DRUG_INTERACTION",
      "count": 2
    },
    {
      "alertType": "ALLERGY_CONTRAINDICATION",
      "count": 1
    }
  ],
  "generatedAt": "2026-02-21T12:30:00Z"
}
```

**Notes**:
- All counts reflect only ACTIVE alerts (not ACKNOWLEDGED or DISMISSED)
- `patientsWithHighNews2`: count of active patients with `news2RiskLevel = HIGH`
- `patientsWithCriticalAlerts`: count of active patients with at least one CRITICAL ACTIVE alert
- DOCTOR role: stats scoped to their patient population

### Error Responses
- `403` — role not DOCTOR or ADMIN
