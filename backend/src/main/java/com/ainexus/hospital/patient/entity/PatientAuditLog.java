package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @Column(name = "operation", length = 20, nullable = false)
    private String operation;

    // Intentionally not a FK — audit records must survive independently (HIPAA)
    @Column(name = "patient_id", length = 12, nullable = false)
    private String patientId;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    // Field names only — no PHI values stored
    @Column(name = "changed_fields", columnDefinition = "TEXT[]")
    private String[] changedFields;
}
