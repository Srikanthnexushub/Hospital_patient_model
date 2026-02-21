# Contract: US4 — Clinical Alerts Feed

**Base URL**: `/api/v1`

---

## GET /patients/{patientId}/alerts

**Description**: Retrieve all clinical alerts for a specific patient (all statuses).
**Roles**: DOCTOR, NURSE, ADMIN

### Query Parameters
| Param | Type | Description |
|---|---|---|
| status | String | Optional. Filter: ACTIVE, ACKNOWLEDGED, DISMISSED |
| severity | String | Optional. Filter: CRITICAL, WARNING, INFO |
| page | int | Default 0 |
| size | int | Default 20 |

### Response — 200 OK

```json
{
  "content": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440003",
      "patientId": "P2026001",
      "patientName": "Jane Doe",
      "alertType": "NEWS2_CRITICAL",
      "severity": "CRITICAL",
      "title": "NEWS2 Critical — Score 9 (HIGH)",
      "description": "Patient's NEWS2 Early Warning Score is 9 (HIGH risk). Immediate emergency clinical assessment required.",
      "source": "NEWS2 computation based on vitals recorded 2026-02-21T11:55:00Z",
      "triggerValue": "9",
      "status": "ACTIVE",
      "createdAt": "2026-02-21T12:00:00Z",
      "acknowledgedBy": null,
      "acknowledgedAt": null,
      "dismissReason": null,
      "dismissedAt": null
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### Error Responses
- `403` — role not DOCTOR, NURSE, or ADMIN
- `404` — patient not found

---

## GET /alerts

**Description**: Global alert feed across patients. DOCTOR sees only alerts from their appointment patients. NURSE and ADMIN see all alerts.
**Roles**: DOCTOR, NURSE, ADMIN

### Query Parameters
| Param | Type | Description |
|---|---|---|
| status | String | Optional. Filter: ACTIVE, ACKNOWLEDGED, DISMISSED |
| severity | String | Optional. Filter: CRITICAL, WARNING, INFO |
| alertType | String | Optional. Filter by specific alert type |
| page | int | Default 0 |
| size | int | Default 20 |

### Response — 200 OK (same structure as per-patient alerts)

```json
{
  "content": [
    {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "patientId": "P2026001",
      "patientName": "Jane Doe",
      "alertType": "LAB_CRITICAL",
      "severity": "CRITICAL",
      "title": "Critical Lab Result — Potassium CRITICAL_HIGH",
      "description": "Lab result for Complete Blood Count recorded a CRITICAL_HIGH value: 7.2 mmol/L (reference: 3.5–5.0). Immediate clinical review required.",
      "source": "Lab result recorded by nurse.jones on 2026-02-21",
      "triggerValue": "7.2 mmol/L",
      "status": "ACTIVE",
      "createdAt": "2026-02-21T11:45:00Z",
      "acknowledgedBy": null,
      "acknowledgedAt": null,
      "dismissReason": null,
      "dismissedAt": null
    }
  ],
  "totalElements": 8,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### Error Responses
- `403` — RECEPTIONIST role

---

## PATCH /alerts/{alertId}/acknowledge

**Description**: Acknowledge a clinical alert. Sets acknowledgedAt and acknowledgedBy from auth context. Status remains ACTIVE (acknowledged ≠ dismissed).
**Roles**: DOCTOR, ADMIN

### No Request Body

### Response — 200 OK

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "patientId": "P2026001",
  "patientName": "Jane Doe",
  "alertType": "NEWS2_CRITICAL",
  "severity": "CRITICAL",
  "title": "NEWS2 Critical — Score 9 (HIGH)",
  "status": "ACTIVE",
  "createdAt": "2026-02-21T12:00:00Z",
  "acknowledgedBy": "dr.smith",
  "acknowledgedAt": "2026-02-21T12:05:00Z",
  "dismissReason": null,
  "dismissedAt": null
}
```

### Error Responses
- `403` — role not DOCTOR or ADMIN (NURSE cannot acknowledge)
- `404` — alert not found
- `409` — alert already DISMISSED (cannot acknowledge dismissed alert)

---

## PATCH /alerts/{alertId}/dismiss

**Description**: Dismiss a clinical alert with a mandatory reason. Sets status=DISMISSED.
**Roles**: DOCTOR, ADMIN

### Request

```json
{
  "reason": "Reviewed with patient — potassium level within acceptable range given current diuretic therapy. Will recheck in 4 hours."
}
```

**Field validation**:
- `reason`: required, non-blank, max 500 chars

### Response — 200 OK

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "patientId": "P2026001",
  "patientName": "Jane Doe",
  "alertType": "NEWS2_CRITICAL",
  "severity": "CRITICAL",
  "title": "NEWS2 Critical — Score 9 (HIGH)",
  "status": "DISMISSED",
  "createdAt": "2026-02-21T12:00:00Z",
  "acknowledgedBy": "dr.smith",
  "acknowledgedAt": "2026-02-21T12:05:00Z",
  "dismissedAt": "2026-02-21T12:10:00Z",
  "dismissReason": "Reviewed with patient — potassium level within acceptable range given current diuretic therapy. Will recheck in 4 hours."
}
```

### Error Responses
- `400` — reason is blank
- `403` — role not DOCTOR or ADMIN
- `404` — alert not found
- `409` — alert already DISMISSED
