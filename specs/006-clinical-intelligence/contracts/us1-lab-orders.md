# Contract: US1 — Lab Orders & Results

**Base URL**: `/api/v1`

---

## POST /patients/{patientId}/lab-orders

**Description**: Create a new lab order for a patient.
**Roles**: DOCTOR, ADMIN

### Request

```json
{
  "testName": "Complete Blood Count",
  "testCode": "58410-2",
  "category": "HEMATOLOGY",
  "appointmentId": "A2026001",
  "priority": "STAT",
  "notes": "Suspected sepsis — urgent"
}
```

**Field validation**:
- `testName`: required, max 200 chars
- `category`: required, enum HEMATOLOGY|CHEMISTRY|MICROBIOLOGY|IMMUNOLOGY|URINALYSIS|OTHER
- `priority`: optional, default ROUTINE, enum ROUTINE|URGENT|STAT
- `appointmentId`: optional, must reference existing appointment if provided
- `testCode`, `notes`: optional

### Response — 201 Created

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P2026001",
  "testName": "Complete Blood Count",
  "testCode": "58410-2",
  "category": "HEMATOLOGY",
  "priority": "STAT",
  "status": "PENDING",
  "orderedBy": "dr.smith",
  "orderedAt": "2026-02-21T10:30:00Z",
  "appointmentId": "A2026001",
  "notes": "Suspected sepsis — urgent"
}
```

### Error Responses
- `400` — validation failure (missing required field, invalid enum)
- `403` — role not DOCTOR or ADMIN
- `404` — patient not found, or appointmentId not found

---

## GET /patients/{patientId}/lab-orders

**Description**: List lab orders for a patient, with optional status filter.
**Roles**: DOCTOR, NURSE, ADMIN

### Query Parameters
| Param | Type | Description |
|---|---|---|
| status | String | Optional. Filter by: PENDING, IN_PROGRESS, RESULTED, CANCELLED |
| page | int | Default 0 |
| size | int | Default 20 |

### Response — 200 OK

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "patientId": "P2026001",
      "testName": "Complete Blood Count",
      "category": "HEMATOLOGY",
      "priority": "STAT",
      "status": "RESULTED",
      "orderedBy": "dr.smith",
      "orderedAt": "2026-02-21T10:30:00Z",
      "hasResult": true
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

---

## POST /lab-orders/{orderId}/result

**Description**: Record a lab result against an existing order. Auto-advances order status to RESULTED. Auto-creates ClinicalAlert based on interpretation.
**Roles**: NURSE, DOCTOR, ADMIN

### Request

```json
{
  "value": "7.2",
  "unit": "mmol/L",
  "referenceRangeLow": 3.5,
  "referenceRangeHigh": 5.0,
  "interpretation": "CRITICAL_HIGH",
  "resultNotes": "Critically elevated potassium — immediate clinical review required"
}
```

**Field validation**:
- `value`: required, non-blank
- `interpretation`: required, enum NORMAL|LOW|HIGH|CRITICAL_LOW|CRITICAL_HIGH|ABNORMAL
- `unit`, `referenceRangeLow`, `referenceRangeHigh`, `resultNotes`: optional

### Response — 201 Created

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P2026001",
  "testName": "Complete Blood Count",
  "value": "7.2",
  "unit": "mmol/L",
  "referenceRangeLow": 3.5,
  "referenceRangeHigh": 5.0,
  "interpretation": "CRITICAL_HIGH",
  "resultNotes": "Critically elevated potassium — immediate clinical review required",
  "resultedBy": "nurse.jones",
  "resultedAt": "2026-02-21T11:45:00Z",
  "alertCreated": true,
  "alertId": "770e8400-e29b-41d4-a716-446655440002"
}
```

**Side effects**:
- Order status advances from PENDING/IN_PROGRESS → RESULTED
- If interpretation is CRITICAL_LOW or CRITICAL_HIGH: ClinicalAlert created (severity=CRITICAL, type=LAB_CRITICAL)
- If interpretation is LOW or HIGH: ClinicalAlert created (severity=WARNING, type=LAB_ABNORMAL)
- If interpretation is NORMAL or ABNORMAL: no alert created
- Audit log entry created

### Error Responses
- `400` — validation failure
- `403` — role not NURSE, DOCTOR, or ADMIN
- `404` — order not found
- `409` — order already in RESULTED or CANCELLED status

---

## GET /patients/{patientId}/lab-results

**Description**: Paginated list of all lab results for a patient, sorted by resultedAt DESC.
**Roles**: DOCTOR, NURSE, ADMIN

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
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "orderId": "550e8400-e29b-41d4-a716-446655440000",
      "testName": "Complete Blood Count",
      "category": "HEMATOLOGY",
      "value": "7.2",
      "unit": "mmol/L",
      "referenceRangeLow": 3.5,
      "referenceRangeHigh": 5.0,
      "interpretation": "CRITICAL_HIGH",
      "resultedBy": "nurse.jones",
      "resultedAt": "2026-02-21T11:45:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```
