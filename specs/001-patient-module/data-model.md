# Data Model: Patient Module

**Feature**: `001-patient-module`
**Date**: 2026-02-19

---

## Entity Relationship Overview

```
PatientIdSequence (1) ──── generates ──── Patient (N)
Patient (1) ──── has many ──── PatientAuditLog (N)
```

---

## Entity 1: Patient

**Table**: `patients`
**JPA Entity**: `com.ainexus.hospital.patient.entity.Patient`

### Fields

| Java Field | Column | Type | Nullable | Constraints |
|---|---|---|---|---|
| `patientId` | `patient_id` | `VARCHAR(12)` | No | PK |
| `firstName` | `first_name` | `VARCHAR(50)` | No | NOT NULL |
| `lastName` | `last_name` | `VARCHAR(50)` | No | NOT NULL |
| `dateOfBirth` | `date_of_birth` | `DATE` | No | NOT NULL |
| `gender` | `gender` | `VARCHAR(10)` | No | CHECK IN ('MALE','FEMALE','OTHER') |
| `bloodGroup` | `blood_group` | `VARCHAR(10)` | No | DEFAULT 'UNKNOWN' |
| `phone` | `phone` | `VARCHAR(20)` | No | NOT NULL |
| `email` | `email` | `VARCHAR(100)` | Yes | — |
| `address` | `address` | `VARCHAR(200)` | Yes | — |
| `city` | `city` | `VARCHAR(100)` | Yes | — |
| `state` | `state` | `VARCHAR(100)` | Yes | — |
| `zipCode` | `zip_code` | `VARCHAR(20)` | Yes | — |
| `emergencyContactName` | `emergency_contact_name` | `VARCHAR(100)` | Yes | — |
| `emergencyContactPhone` | `emergency_contact_phone` | `VARCHAR(20)` | Yes | — |
| `emergencyContactRelationship` | `emergency_contact_relationship` | `VARCHAR(50)` | Yes | — |
| `knownAllergies` | `known_allergies` | `TEXT` | Yes | — |
| `chronicConditions` | `chronic_conditions` | `TEXT` | Yes | — |
| `status` | `status` | `VARCHAR(10)` | No | DEFAULT 'ACTIVE', CHECK IN ('ACTIVE','INACTIVE') |
| `createdAt` | `created_at` | `TIMESTAMPTZ` | No | DEFAULT NOW(), immutable |
| `createdBy` | `created_by` | `VARCHAR(100)` | No | NOT NULL |
| `updatedAt` | `updated_at` | `TIMESTAMPTZ` | No | DEFAULT NOW() |
| `updatedBy` | `updated_by` | `VARCHAR(100)` | No | NOT NULL |
| `version` | `version` | `INTEGER` | No | DEFAULT 0, JPA @Version |

### JPA Annotations

```java
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @Column(name = "patient_id", length = 12)
    private String patientId;

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10, nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 10, nullable = false)
    private BloodGroup bloodGroup;   // DEFAULT BloodGroup.UNKNOWN

    @Column(name = "status", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private PatientStatus status;    // DEFAULT PatientStatus.ACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // ... other fields
}
```

### Enumerations

```java
public enum Gender       { MALE, FEMALE, OTHER }
public enum BloodGroup   { A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG, UNKNOWN }
public enum PatientStatus { ACTIVE, INACTIVE }
```

*Note*: `BloodGroup` display values (`A+`, `A-`, etc.) are mapped via a `@JsonValue`
annotation or serializer. The `UNKNOWN` value is the default when no blood group is provided.

### Computed Field: age

Age is NOT stored. It is computed in `PatientMapper` (MapStruct) or the service layer:

```java
public int calculateAge(LocalDate dateOfBirth) {
    return Period.between(dateOfBirth, LocalDate.now()).getYears();
}
```

### State Machine

```
ACTIVE  ──[ADMIN: deactivate + confirm]──►  INACTIVE
INACTIVE ──[ADMIN: activate]───────────►  ACTIVE
```

---

## Entity 2: PatientIdSequence

**Table**: `patient_id_sequences`
**JPA Entity**: `com.ainexus.hospital.patient.entity.PatientIdSequence`

### Fields

| Java Field | Column | Type | Nullable | Constraints |
|---|---|---|---|---|
| `year` | `year` | `INTEGER` | No | PK |
| `lastSequence` | `last_sequence` | `INTEGER` | No | DEFAULT 0 |

### ID Generation Algorithm

```
BEGIN TRANSACTION;
  SELECT last_sequence FROM patient_id_sequences
    WHERE year = YEAR(NOW())
    FOR UPDATE;                        -- pessimistic row lock

  IF no row found:
    INSERT INTO patient_id_sequences (year, last_sequence) VALUES (YEAR, 1);
    nextSeq = 1;
  ELSE:
    UPDATE patient_id_sequences SET last_sequence = last_sequence + 1
    WHERE year = YEAR(NOW());
    nextSeq = last_sequence + 1;

  patientId = "P" + YEAR + LPAD(nextSeq, 3, '0');
  -- If nextSeq > 999: patientId = "P" + YEAR + nextSeq (no padding)
COMMIT;
```

---

## Entity 3: PatientAuditLog

**Table**: `patient_audit_log`
**JPA Entity**: `com.ainexus.hospital.patient.entity.PatientAuditLog`

### Fields

| Java Field | Column | Type | Nullable | Constraints |
|---|---|---|---|---|
| `id` | `id` | `BIGSERIAL` | No | PK, auto-increment |
| `timestamp` | `timestamp` | `TIMESTAMPTZ` | No | DEFAULT NOW() |
| `operation` | `operation` | `VARCHAR(20)` | No | CHECK IN ('REGISTER','UPDATE','DEACTIVATE','ACTIVATE') |
| `patientId` | `patient_id` | `VARCHAR(12)` | No | NOT NULL (no FK — audit is independent) |
| `performedBy` | `performed_by` | `VARCHAR(100)` | No | NOT NULL |
| `changedFields` | `changed_fields` | `TEXT[]` | Yes | null for non-UPDATE ops |

### Rules

- No `@OneToMany` FK from `Patient` — audit log is intentionally decoupled for
  HIPAA retention independence.
- No UPDATE or DELETE permitted via application code. Repository exposes only `save()`
  and query methods.
- Written in the same `@Transactional` block as the patient record change.

---

## DTOs

### Request DTOs

#### PatientRegistrationRequest

```java
public record PatientRegistrationRequest(
    @NotBlank @Size(max=50) String firstName,
    @NotBlank @Size(max=50) String lastName,
    @NotNull @Past LocalDate dateOfBirth,          // custom: not today, not > 150y
    @NotNull Gender gender,
    @NotNull @PhoneNumber String phone,             // custom @PhoneNumber validator
    @Email @Size(max=100) String email,             // optional
    @Size(max=200) String address,
    @Size(max=100) String city,
    @Size(max=100) String state,
    @Size(max=20) String zipCode,
    @Size(max=100) String emergencyContactName,
    @PhoneNumber String emergencyContactPhone,      // optional; required if name present
    @Size(max=50) String emergencyContactRelationship,
    BloodGroup bloodGroup,
    @Size(max=1000) String knownAllergies,
    @Size(max=1000) String chronicConditions
)
```

*Cross-field validation*: A class-level `@EmergencyContactPairing` constraint validator
checks that `emergencyContactName` and `emergencyContactPhone` are either both present
or both absent.

#### PatientUpdateRequest

Identical fields to `PatientRegistrationRequest` (all the same validation rules apply).
`patientId`, `createdAt`, `createdBy`, `version` are NOT in this DTO.

The `version` field is passed as a separate request parameter or header to support
optimistic locking conflict detection.

---

### Response DTOs

#### PatientSummaryResponse (list row)

```java
public record PatientSummaryResponse(
    String patientId,
    String firstName,
    String lastName,
    int age,                    // computed
    Gender gender,
    String phone,
    PatientStatus status
)
```

#### PatientResponse (full profile)

```java
public record PatientResponse(
    String patientId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    int age,                    // computed
    Gender gender,
    BloodGroup bloodGroup,
    String phone,
    String email,
    String address,
    String city,
    String state,
    String zipCode,
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelationship,
    String knownAllergies,
    String chronicConditions,
    PatientStatus status,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    Integer version             // included for optimistic lock in update request
)
```

#### PagedResponse<T>

```java
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
)
```

---

## Indexes

| Index Name | Table | Columns | Type |
|---|---|---|---|
| `idx_patients_status` | patients | `status` | B-Tree |
| `idx_patients_last_name` | patients | `last_name` | B-Tree |
| `idx_patients_first_name` | patients | `first_name` | B-Tree |
| `idx_patients_phone` | patients | `phone` | B-Tree |
| `idx_patients_email` | patients | `email` | B-Tree |
| `idx_patients_blood_group` | patients | `blood_group` | B-Tree |
| `idx_patients_gender` | patients | `gender` | B-Tree |
| `idx_patients_created_at` | patients | `created_at DESC` | B-Tree |
| `idx_patients_full_text` | patients | `to_tsvector('english', first_name\|\|' '\|\|last_name)` | GIN |
| `idx_audit_patient_id` | patient_audit_log | `patient_id` | B-Tree |
| `idx_audit_performed_by` | patient_audit_log | `performed_by` | B-Tree |
| `idx_audit_timestamp` | patient_audit_log | `timestamp DESC` | B-Tree |

---

## MapStruct Mapper: PatientMapper

```java
@Mapper(componentModel = "spring")
public interface PatientMapper {
    PatientSummaryResponse toSummary(Patient patient);
    PatientResponse toResponse(Patient patient);
    Patient toEntity(PatientRegistrationRequest request);
    void updateEntity(PatientUpdateRequest request, @MappingTarget Patient patient);

    default int toAge(LocalDate dateOfBirth) {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
```

---

## Custom Validators

### @PhoneNumber

Validates against the three accepted formats using a regex:
```
^(\+1-\d{3}-\d{3}-\d{4}|\(\d{3}\) \d{3}-\d{4}|\d{3}-\d{3}-\d{4})$
```

### DateOfBirth validation (custom constraint)

Beyond `@Past`, a custom `@ValidDateOfBirth` constraint checks:
- Date is not today
- Date is not more than 150 years in the past

### @EmergencyContactPairing (class-level constraint)

Validates that `emergencyContactName` and `emergencyContactPhone` are either both
non-null/non-blank or both null/blank.
