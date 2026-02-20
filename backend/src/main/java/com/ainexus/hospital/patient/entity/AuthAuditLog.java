package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Immutable audit record for all authentication and staff management events.
 * Separate from PatientAuditLog — different domain and compliance queries.
 * Application code MUST NOT issue UPDATE or DELETE on this entity.
 *
 * HIPAA: details field MUST NOT contain passwords, tokens, PHI, or secrets.
 */
@Entity
@Table(name = "auth_audit_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @Column(name = "event_type", length = 30, nullable = false)
    private String eventType;

    @Column(name = "actor_user_id", length = 12, nullable = false)
    private String actorUserId;

    @Column(name = "target_user_id", length = 12)
    private String targetUserId;

    @Column(name = "outcome", length = 10, nullable = false)
    private String outcome;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}
