package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "emr_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmrAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_type", length = 20, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 50, nullable = false)
    private String entityId;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "action", length = 30, nullable = false)
    private String action;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}
