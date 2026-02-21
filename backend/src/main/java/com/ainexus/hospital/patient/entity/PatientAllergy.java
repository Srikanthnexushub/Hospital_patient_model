package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient_allergies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "substance", length = 200, nullable = false)
    private String substance;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private AllergyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private AllergySeverity severity;

    @Column(name = "reaction", columnDefinition = "TEXT", nullable = false)
    private String reaction;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (active == null) active = Boolean.TRUE;
    }
}
