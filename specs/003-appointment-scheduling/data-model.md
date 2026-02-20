# Data Model: Appointment Scheduling Module

**Branch**: `003-appointment-scheduling`
**Phase**: 1 — Design
**Date**: 2026-02-20

---

## Overview

Four new tables added via Flyway V8–V11. Zero changes to existing tables (V1–V7 frozen).

### Relationships

```
patients (existing)
    │ 1
    │ ∞
appointments ──────────────── appointment_audit_log
    │ 1                               (no FK — HIPAA)
    │ 1
clinical_notes

hospital_users (existing)
    │ 1 (doctorId reference — no FK constraint, service validates)
    │ ∞
appointments
```

---

## Entity 1: Appointment

**Table**: `appointments`
**Flyway**: V8

### Fields

| Column | Type | Nullable | Constraints | Notes |
|--------|------|----------|-------------|-------|
| `appointment_id` | `VARCHAR(14)` | NOT NULL | PK | `APT` + year + 4-digit zero-padded seq |
| `patient_id` | `VARCHAR(12)` | NOT NULL | FK → patients(patient_id) | Must be ACTIVE patient |
| `doctor_id` | `VARCHAR(12)` | NOT NULL | — | Validated against hospital_users at booking time; no FK (avoids cross-schema coupling) |
| `appointment_date` | `DATE` | NOT NULL | — | Hospital local date |
| `start_time` | `TIME` | NOT NULL | — | Hospital local time; must be 08:00–18:00 |
| `end_time` | `TIME` | NOT NULL | computed | `start_time + duration_minutes` — stored for efficient overlap queries |
| `duration_minutes` | `INTEGER` | NOT NULL | `IN (15,30,45,60,90,120)` | |
| `type` | `VARCHAR(30)` | NOT NULL | `IN (GENERAL_CONSULTATION, FOLLOW_UP, SPECIALIST, EMERGENCY, ROUTINE_CHECKUP, PROCEDURE)` | |
| `status` | `VARCHAR(20)` | NOT NULL | `DEFAULT 'SCHEDULED'`, see allowed values | State machine enforced in service |
| `reason` | `TEXT` | NULLABLE | — | Free-text visit reason |
| `notes` | `TEXT` | NULLABLE | — | Admin/scheduling notes (not clinical) |
| `cancel_reason` | `TEXT` | NULLABLE | — | Required when status = CANCELLED |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | `DEFAULT NOW()` | Immutable |
| `created_by` | `VARCHAR(50)` | NOT NULL | — | Username of booking staff |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | `DEFAULT NOW()` | Updated on every mutation |
| `updated_by` | `VARCHAR(50)` | NOT NULL | — | Username of last actor |
| `version` | `INTEGER` | NOT NULL | `DEFAULT 0` | JPA `@Version` for optimistic locking |

### Status Values (State Machine)

```
SCHEDULED ──→ CONFIRMED      (RECEPTIONIST, ADMIN)
          └──→ CANCELLED      (RECEPTIONIST, ADMIN, DOCTOR)

CONFIRMED ──→ CHECKED_IN     (RECEPTIONIST, NURSE, ADMIN)
          ├──→ CANCELLED      (RECEPTIONIST, ADMIN, DOCTOR)
          └──→ NO_SHOW        (RECEPTIONIST, ADMIN)

CHECKED_IN ─→ IN_PROGRESS    (DOCTOR, ADMIN)

IN_PROGRESS → COMPLETED      (DOCTOR, ADMIN)
           └→ CANCELLED      (DOCTOR, ADMIN)

COMPLETED   — terminal state
CANCELLED   — terminal state (soft-kept, never deleted)
NO_SHOW     — terminal state (soft-kept, never deleted)

ADMIN escape hatch: ANY → CANCELLED
```

### Validation Rules

- `appointmentDate` must not be in the past at booking time
- `startTime` must be between 08:00 and 17:30 (latest start for 30-min slot to end by 18:00; EMERGENCY type may be outside hours)
- `endTime = startTime + durationMinutes` — must not exceed 18:00 (except EMERGENCY)
- No overlapping appointment for same `doctorId` + `appointmentDate` window (status not CANCELLED/NO_SHOW)
- Patient must have `status = ACTIVE`
- Doctor must exist in `hospital_users` with `role = DOCTOR` and `status = ACTIVE`

### Indexes

- `idx_appointments_doctor_date` on `(doctor_id, appointment_date)` — supports schedule and conflict queries
- `idx_appointments_patient_id` on `(patient_id)` — supports patient history
- `idx_appointments_status` on `(status)` — supports status-filtered list queries
- `idx_appointments_date_status` on `(appointment_date, status)` — supports today's schedule

---

## Entity 2: AppointmentIdSequence

**Table**: `appointment_id_sequences`
**Flyway**: V9

### Fields

| Column | Type | Nullable | Constraints | Notes |
|--------|------|----------|-------------|-------|
| `year` | `INTEGER` | NOT NULL | PK | Calendar year (e.g., 2026) |
| `last_sequence` | `INTEGER` | NOT NULL | `DEFAULT 0` | Incremented atomically via `SELECT FOR UPDATE` |

### Notes

- Mirrors `patient_id_sequences` exactly (proven pattern)
- Row is created on first booking of each year
- `SELECT FOR UPDATE` in `AppointmentIdGeneratorService` prevents duplicate IDs under concurrent load

---

## Entity 3: AppointmentAuditLog

**Table**: `appointment_audit_log`
**Flyway**: V10

### Fields

| Column | Type | Nullable | Constraints | Notes |
|--------|------|----------|-------------|-------|
| `id` | `BIGSERIAL` | NOT NULL | PK | Auto-generated — never exposed in API |
| `appointment_id` | `VARCHAR(14)` | NOT NULL | NOT a FK | Survives independently of the appointment record (HIPAA) |
| `action` | `VARCHAR(20)` | NOT NULL | See allowed values | |
| `from_status` | `VARCHAR(20)` | NULLABLE | — | Null for CREATE actions |
| `to_status` | `VARCHAR(20)` | NULLABLE | — | Null for non-status actions |
| `performed_by` | `VARCHAR(50)` | NOT NULL | — | Username of actor |
| `performed_at` | `TIMESTAMPTZ` | NOT NULL | `DEFAULT NOW()` | Wall clock at time of action |
| `details` | `TEXT` | NULLABLE | — | Free-text context (e.g., cancel reason, changed fields) |

### Action Values

`CREATE`, `UPDATE`, `CONFIRM`, `CHECK_IN`, `START`, `COMPLETE`, `CANCEL`, `NO_SHOW`, `NOTE_ADDED`, `NOTE_UPDATED`

### Notes

- Append-only: application code MUST NOT issue UPDATE or DELETE on this table
- No FK to `appointments` (HIPAA retention independence — audit survives potential future data purges)
- Retained indefinitely (HIPAA 6-year minimum for healthcare records)

---

## Entity 4: ClinicalNotes

**Table**: `clinical_notes`
**Flyway**: V11

### Fields

| Column | Type | Nullable | Constraints | Notes |
|--------|------|----------|-------------|-------|
| `appointment_id` | `VARCHAR(14)` | NOT NULL | PK, FK → appointments(appointment_id) | One-to-one with appointment |
| `chief_complaint` | `TEXT` | NULLABLE | Encrypted (AES-256-GCM) | PHI — encrypted at rest |
| `diagnosis` | `TEXT` | NULLABLE | Encrypted (AES-256-GCM) | PHI — encrypted at rest |
| `treatment` | `TEXT` | NULLABLE | Encrypted (AES-256-GCM) | PHI — encrypted at rest |
| `prescription` | `TEXT` | NULLABLE | Encrypted (AES-256-GCM) | PHI — encrypted at rest |
| `follow_up_required` | `BOOLEAN` | NOT NULL | `DEFAULT FALSE` | Not encrypted |
| `follow_up_days` | `INTEGER` | NULLABLE | — | Null if `follow_up_required = false` |
| `private_notes` | `TEXT` | NULLABLE | Encrypted (AES-256-GCM) | DOCTOR + ADMIN only; NEVER returned to RECEPTIONIST/NURSE |
| `created_by` | `VARCHAR(50)` | NOT NULL | — | Username of creating doctor |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | `DEFAULT NOW()` | Immutable |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | `DEFAULT NOW()` | Updated on each edit |

### Encryption Details

- Algorithm: `AES/GCM/NoPadding` (authenticated encryption)
- Key: 256-bit (32 bytes), base64-encoded in env var `APP_NOTES_ENCRYPTION_KEY`
- IV: 12 bytes randomly generated per field encryption, prepended to ciphertext
- Storage: Base64-encoded `(IV || ciphertext)` in `TEXT` column
- JPA mapping: `@Convert(converter = NotesEncryptionConverter.class)` on each encrypted field

### Access Control

| Role | Can write | Can read all fields | Can read privateNotes |
|------|-----------|--------------------|-----------------------|
| DOCTOR (own patient's appointment) | ✅ | ✅ | ✅ |
| ADMIN | ✅ | ✅ | ✅ |
| RECEPTIONIST | ❌ | ✅ (except privateNotes) | ❌ |
| NURSE | ❌ | ✅ (except privateNotes) | ❌ |

### Validation Rules

- `followUpDays` must be null when `followUpRequired = false`
- `followUpDays` must be ≥ 1 when `followUpRequired = true`
- Only allowed when parent appointment `status IN (IN_PROGRESS, COMPLETED)`
- Only the assigned doctor (`doctorId` matches `ctx.getUserId()`) or ADMIN may write

---

## JPA Entity Relationships

```java
// Appointment.java
@OneToOne(mappedBy = "appointment", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
private ClinicalNotes clinicalNotes;

// ClinicalNotes.java
@Id
@Column(name = "appointment_id")
private String appointmentId;

@OneToOne(fetch = FetchType.LAZY)
@MapsId
@JoinColumn(name = "appointment_id")
private Appointment appointment;
```

---

## Cross-Module References (no FK constraints)

| Field | References | Validation | FK? |
|-------|-----------|------------|-----|
| `appointments.patient_id` | `patients.patient_id` | Service validates patient exists + ACTIVE | ✅ FK |
| `appointments.doctor_id` | `hospital_users.user_id` | Service validates user exists, role=DOCTOR, status=ACTIVE | ❌ No FK (cross-module decoupling) |
| `appointment_audit_log.appointment_id` | `appointments.appointment_id` | N/A — audit survives independently | ❌ No FK |
