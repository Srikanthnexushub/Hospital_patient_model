package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient_medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "medication_name", length = 200, nullable = false)
    private String medicationName;

    @Column(name = "generic_name", length = 200)
    private String genericName;

    @Column(name = "dosage", length = 100, nullable = false)
    private String dosage;

    @Column(name = "frequency", length = 100, nullable = false)
    private String frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 20, nullable = false)
    private MedicationRoute route;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "indication", columnDefinition = "TEXT")
    private String indication;

    @Column(name = "prescribed_by", length = 100, nullable = false)
    private String prescribedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MedicationStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = MedicationStatus.ACTIVE;
    }
}
