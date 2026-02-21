package com.ainexus.hospital.patient.entity;

import com.ainexus.hospital.patient.validation.NotesEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "clinical_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalNotes {

    @Id
    @Column(name = "appointment_id", length = 14, nullable = false)
    private String appointmentId;

    @Convert(converter = NotesEncryptionConverter.class)
    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Convert(converter = NotesEncryptionConverter.class)
    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Convert(converter = NotesEncryptionConverter.class)
    @Column(name = "treatment", columnDefinition = "TEXT")
    private String treatment;

    @Convert(converter = NotesEncryptionConverter.class)
    @Column(name = "prescription", columnDefinition = "TEXT")
    private String prescription;

    @Column(name = "follow_up_required", nullable = false)
    @Builder.Default
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_days")
    private Integer followUpDays;

    @Convert(converter = NotesEncryptionConverter.class)
    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
