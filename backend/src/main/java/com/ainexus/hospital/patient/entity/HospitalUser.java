package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "hospital_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalUser {

    @Id
    @Column(name = "user_id", length = 12, nullable = false)
    private String userId;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    /** BCrypt hash — NEVER expose in API responses or logs. */
    @Column(name = "password_hash", length = 72, nullable = false)
    private String passwordHash;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 50, nullable = false, updatable = false)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 50, nullable = false)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Returns true if the account status is ACTIVE. */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    /**
     * Returns true if the account is currently locked (lockedUntil is in the future).
     * A null lockedUntil means not locked.
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now());
    }
}
