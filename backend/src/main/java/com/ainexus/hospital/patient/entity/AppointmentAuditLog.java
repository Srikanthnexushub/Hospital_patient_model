package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appointment_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "appointment_id", length = 14, nullable = false)
    private String appointmentId;

    @Column(name = "action", length = 30, nullable = false)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20, nullable = false)
    private String toStatus;

    @Column(name = "performed_by", length = 50, nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}
