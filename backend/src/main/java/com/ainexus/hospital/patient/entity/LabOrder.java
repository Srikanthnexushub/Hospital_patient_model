package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "appointment_id", length = 14)
    private String appointmentId;

    @Column(name = "test_name", length = 200, nullable = false)
    private String testName;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private LabOrderCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20, nullable = false)
    @Builder.Default
    private LabOrderPriority priority = LabOrderPriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private LabOrderStatus status = LabOrderStatus.PENDING;

    @Column(name = "ordered_by", length = 100, nullable = false)
    private String orderedBy;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @PrePersist
    private void prePersist() {
        if (orderedAt == null) orderedAt = OffsetDateTime.now();
        if (status == null) status = LabOrderStatus.PENDING;
        if (priority == null) priority = LabOrderPriority.ROUTINE;
    }
}
