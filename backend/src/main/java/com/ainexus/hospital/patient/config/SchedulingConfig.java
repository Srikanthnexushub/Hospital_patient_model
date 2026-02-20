package com.ainexus.hospital.patient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled annotation support.
 * Required for BlacklistCleanupService.purgeExpiredBlacklistEntries().
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
