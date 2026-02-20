package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, String> {

    /**
     * Deletes all blacklist entries whose expiry has passed.
     * Called by BlacklistCleanupService every 15 minutes.
     */
    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
