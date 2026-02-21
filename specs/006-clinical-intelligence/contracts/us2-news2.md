# Contract: US2 — NEWS2 Early Warning Score

**Base URL**: `/api/v1`

---

## GET /patients/{patientId}/news2

**Description**: Compute and return the NHS NEWS2 Early Warning Score for the patient's most recently recorded vitals. Auto-creates a ClinicalAlert if the risk level is MEDIUM or HIGH.
**Roles**: DOCTOR, NURSE, ADMIN

### No Request Body

### Response — 200 OK (vitals present)

```json
{
  "totalScore": 9,
  "riskLevel": "HIGH",
  "riskColour": "red",
  "recommendation": "Emergency clinical assessment required. Continuous monitoring and immediate escalation to clinical team.",
  "components": [
    {
      "parameter": "Respiratory Rate",
      "value": 26,
      "score": 3,
      "unit": "/min",
      "defaulted": false
    },
    {
      "parameter": "SpO2",
      "value": 90,
      "score": 3,
      "unit": "%",
      "defaulted": false
    },
    {
      "parameter": "Systolic BP",
      "value": 95,
      "score": 2,
      "unit": "mmHg",
      "defaulted": false
    },
    {
      "parameter": "Heart Rate",
      "value": 115,
      "score": 1,
      "unit": "bpm",
      "defaulted": false
    },
    {
      "parameter": "Temperature",
      "value": 38.9,
      "score": 1,
      "unit": "°C",
      "defaulted": false
    },
    {
      "parameter": "Consciousness",
      "value": null,
      "score": 0,
      "unit": "AVPU",
      "defaulted": true
    }
  ],
  "basedOnVitalsId": 42,
  "computedAt": "2026-02-21T12:00:00Z",
  "alertCreated": true,
  "alertId": "880e8400-e29b-41d4-a716-446655440003"
}
```

### Response — 200 OK (no vitals on record)

```json
{
  "totalScore": null,
  "riskLevel": "NO_DATA",
  "riskColour": "grey",
  "recommendation": null,
  "components": [],
  "basedOnVitalsId": null,
  "computedAt": "2026-02-21T12:00:00Z",
  "message": "No vitals on record for this patient",
  "alertCreated": false,
  "alertId": null
}
```

### NEWS2 Scoring Reference

**Respiratory Rate (/min)**:
| Range | Score |
|---|---|
| ≤ 8 | 3 |
| 9–11 | 1 |
| 12–20 | 0 |
| 21–24 | 2 |
| ≥ 25 | 3 |

**SpO2 Scale 1 (%)**:
| Range | Score |
|---|---|
| ≤ 91 | 3 |
| 92–93 | 2 |
| 94–95 | 1 |
| ≥ 96 | 0 |

**Systolic BP (mmHg)**:
| Range | Score |
|---|---|
| ≤ 90 | 3 |
| 91–100 | 2 |
| 101–110 | 1 |
| 111–219 | 0 |
| ≥ 220 | 3 |

**Heart Rate (bpm)**:
| Range | Score |
|---|---|
| ≤ 40 | 3 |
| 41–50 | 1 |
| 51–90 | 0 |
| 91–110 | 1 |
| 111–130 | 2 |
| ≥ 131 | 3 |

**Temperature (°C)**:
| Range | Score |
|---|---|
| ≤ 35.0 | 3 |
| 35.1–36.0 | 1 |
| 36.1–38.0 | 0 |
| 38.1–39.0 | 1 |
| ≥ 39.1 | 2 |

**Consciousness**: Defaults to ALERT (score=0). AVPU not yet captured.

**Risk Classification**:
| Condition | Risk Level | Colour | Action |
|---|---|---|---|
| Total = 0 | LOW | green | Routine ward monitoring |
| Total 1-4, no single score=3 | LOW_MEDIUM | yellow | 4-6h monitoring |
| Total 1-4, any single score=3 | MEDIUM | orange | 1h urgent review |
| Total 5-6 | MEDIUM | orange | 1h urgent review |
| Total ≥ 7 | HIGH | red | Emergency clinical assessment |

**Alert Side Effects**:
- riskLevel=MEDIUM → ClinicalAlert(severity=WARNING, type=NEWS2_HIGH); deduplicated per patient
- riskLevel=HIGH → ClinicalAlert(severity=CRITICAL, type=NEWS2_CRITICAL); deduplicated per patient
- riskLevel=LOW or LOW_MEDIUM → dismiss any existing ACTIVE NEWS2 alerts for this patient

### Error Responses
- `403` — role not DOCTOR, NURSE, or ADMIN
- `404` — patient not found
