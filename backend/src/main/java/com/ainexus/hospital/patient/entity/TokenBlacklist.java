package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Represents a revoked JWT entry in the blacklist.
 * Keyed by jti (JWT ID claim — UUID4 string).
 * Entries are purged by BlacklistCleanupService once expires_at passes.
 */
@Entity
@Table(name = "token_blacklist")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {

    @Id
    @Column(name = "jti", length = 36, nullable = false)
    private String jti;

    @Column(name = "user_id", length = 12, nullable = false)
    private String userId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    @Builder.Default
    private OffsetDateTime revokedAt = OffsetDateTime.now();
}
