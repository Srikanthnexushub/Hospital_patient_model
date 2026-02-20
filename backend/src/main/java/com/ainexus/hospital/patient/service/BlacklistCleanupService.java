package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.repository.TokenBlacklistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Scheduled service that purges expired token blacklist entries.
 *
 * Entries with expires_at < NOW() are deleted every 15 minutes.
 * At steady state (~500 staff, 8h token lifetime), the blacklist holds
 * at most ~2,000 rows, making this cleanup trivially fast (AD-005).
 *
 * @EnableScheduling is in SchedulingConfig.
 */
@Service
public class BlacklistCleanupService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistCleanupService.class);

    private final TokenBlacklistRepository tokenBlacklistRepository;

    public BlacklistCleanupService(TokenBlacklistRepository tokenBlacklistRepository) {
        this.tokenBlacklistRepository = tokenBlacklistRepository;
    }

    /**
     * Purges expired token_blacklist rows every 15 minutes.
     * Cron: "0 *&#47;15 * * * *" fires at the top of every 15-minute interval.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void purgeExpiredBlacklistEntries() {
        OffsetDateTime cutoff = OffsetDateTime.now();
        tokenBlacklistRepository.deleteByExpiresAtBefore(cutoff);
        log.debug("BlacklistCleanupService: purged expired blacklist entries older than {}", cutoff);
    }
}
