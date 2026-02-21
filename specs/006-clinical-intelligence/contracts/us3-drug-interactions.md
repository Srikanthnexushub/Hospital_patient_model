# Contract: US3 — Drug Interaction & Allergy Contraindication Checker

**Base URL**: `/api/v1`

---

## POST /patients/{patientId}/interaction-check

**Description**: Check a named drug against the patient's active medications and recorded allergies. Creates a CRITICAL ClinicalAlert if MAJOR or CONTRAINDICATED interaction found.
**Roles**: DOCTOR, ADMIN

### Request

```json
{
  "drugName": "Warfarin"
}
```

**Field validation**:
- `drugName`: required, non-blank, max 200 chars

### Response — 200 OK

```json
{
  "drugName": "Warfarin",
  "interactions": [
    {
      "drug1": "Warfarin",
      "drug2": "Aspirin",
      "severity": "MAJOR",
      "mechanism": "Additive anticoagulation — both inhibit platelet function and coagulation cascade",
      "clinicalEffect": "Significantly increased risk of serious bleeding (GI, intracranial)",
      "recommendation": "Avoid combination. If unavoidable, monitor INR closely and use PPI prophylaxis."
    },
    {
      "drug1": "Warfarin",
      "drug2": "Ibuprofen",
      "severity": "MAJOR",
      "mechanism": "NSAIDs inhibit platelet aggregation and COX-1, additive with warfarin anticoagulation",
      "clinicalEffect": "Increased bleeding risk, especially GI haemorrhage",
      "recommendation": "Avoid NSAIDs. Use paracetamol for analgesia if anticoagulation required."
    }
  ],
  "allergyContraindications": [],
  "safe": false,
  "checkedAt": "2026-02-21T12:15:00Z",
  "alertCreated": true,
  "alertId": "990e8400-e29b-41d4-a716-446655440004"
}
```

### Response — 200 OK (safe drug, allergy match)

```json
{
  "drugName": "Amoxicillin",
  "interactions": [],
  "allergyContraindications": [
    {
      "allergyId": "550e8400-e29b-41d4-a716-000000000001",
      "substance": "Penicillin",
      "matchedDrug": "Amoxicillin",
      "matchType": "CROSS_REACTIVITY",
      "severity": "SEVERE",
      "reaction": "Anaphylaxis",
      "recommendation": "CONTRAINDICATED — patient has documented Penicillin allergy. Amoxicillin is a penicillin-class antibiotic."
    }
  ],
  "safe": false,
  "checkedAt": "2026-02-21T12:15:00Z",
  "alertCreated": true,
  "alertId": "aa0e8400-e29b-41d4-a716-446655440005"
}
```

### Alert Side Effects
- MAJOR or CONTRAINDICATED interaction found → ClinicalAlert(severity=CRITICAL, type=DRUG_INTERACTION)
- Allergy contraindication found → ClinicalAlert(severity=CRITICAL, type=ALLERGY_CONTRAINDICATION)
- MINOR or MODERATE interaction only → no alert

### Error Responses
- `400` — drugName blank
- `403` — role not DOCTOR or ADMIN
- `404` — patient not found

---

## GET /patients/{patientId}/interaction-summary

**Description**: Returns all known drug-drug interactions across the patient's complete active medication list. Does not check against a new drug.
**Roles**: DOCTOR, NURSE, ADMIN

### Response — 200 OK

```json
{
  "patientId": "P2026001",
  "activeMedicationCount": 5,
  "interactions": [
    {
      "drug1": "Warfarin",
      "drug2": "Aspirin",
      "severity": "MAJOR",
      "mechanism": "Additive anticoagulation",
      "clinicalEffect": "Increased bleeding risk",
      "recommendation": "Avoid combination. Monitor INR closely."
    }
  ],
  "interactionCount": 1,
  "highSeverityCount": 1,
  "checkedAt": "2026-02-21T12:16:00Z"
}
```

### Error Responses
- `403` — role not DOCTOR, NURSE, or ADMIN
- `404` — patient not found

---

## Drug Interaction Database Coverage (Minimum 40 pairs)

The built-in database includes at minimum the following clinically significant interactions:

### Anticoagulants (8 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Warfarin | Aspirin | MAJOR |
| Warfarin | Ibuprofen | MAJOR |
| Warfarin | Naproxen | MAJOR |
| Warfarin | Clopidogrel | MAJOR |
| Warfarin | Metronidazole | MAJOR |
| Warfarin | Fluconazole | MAJOR |
| Warfarin | Amiodarone | MAJOR |
| Heparin | Aspirin | MAJOR |

### Cardiac (6 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Digoxin | Amiodarone | MAJOR |
| Digoxin | Clarithromycin | MAJOR |
| ACE inhibitor | Potassium-sparing diuretic | MAJOR |
| ACE inhibitor | Spironolactone | MAJOR |
| Metoprolol | Verapamil | MAJOR |
| Amlodipine | Simvastatin | MODERATE |

### CNS (6 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| SSRI | MAOI | CONTRAINDICATED |
| SSRI | Tramadol | MAJOR |
| SSRI | Triptans | MODERATE |
| Benzodiazepine | Opioid | MAJOR |
| Lithium | NSAIDs | MAJOR |
| Valproate | Aspirin | MODERATE |

### Antibiotics & Antifungals (7 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Ciprofloxacin | Theophylline | MAJOR |
| Ciprofloxacin | Antacids | MODERATE |
| Metronidazole | Alcohol | MAJOR |
| Clarithromycin | Statins | MAJOR |
| Fluconazole | Midazolam | CONTRAINDICATED |
| Rifampicin | Oral contraceptives | MAJOR |
| Tetracycline | Antacids | MODERATE |

### Diabetes (4 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Metformin | Contrast media | MAJOR |
| Metformin | Alcohol | MODERATE |
| Insulin | Beta-blockers | MODERATE |
| Sulfonylurea | Fluconazole | MAJOR |

### Respiratory (3 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Theophylline | Ciprofloxacin | MAJOR |
| Theophylline | Erythromycin | MAJOR |
| Beta-agonist | Non-selective beta-blocker | MAJOR |

### Common OTC & Others (10 pairs)
| Drug 1 | Drug 2 | Severity |
|---|---|---|
| Ibuprofen | ACE inhibitor | MODERATE |
| Aspirin | Methotrexate | MAJOR |
| Aspirin | Corticosteroids | MODERATE |
| Simvastatin | Amiodarone | MAJOR |
| Clopidogrel | Omeprazole | MODERATE |
| Phenytoin | Valproate | MODERATE |
| Carbamazepine | Oral contraceptives | MAJOR |
| Lithium | Thiazide diuretics | MAJOR |
| Haloperidol | Lithium | MAJOR |
| Levodopa | Antipsychotics | MAJOR |
