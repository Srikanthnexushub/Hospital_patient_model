package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Seeds the default ADMIN account on first application startup.
 *
 * Runs after the full Spring ApplicationContext is assembled (after Flyway migrations).
 * Idempotent: no-ops if any hospital_users row already exists.
 *
 * HIPAA: Never logs the password. Only logs the username.
 * Configuration: ADMIN_INITIAL_PASSWORD env var has NO default — Spring refuses to
 * start if it is absent, satisfying FR-027.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    // No default — Spring throws IllegalArgumentException at startup if absent (FR-027)
    @Value("${app.auth.admin.initial-password}")
    private String adminInitialPassword;

    @Value("${app.auth.admin.username:admin}")
    private String adminUsername;

    private final HospitalUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffIdGeneratorService staffIdGeneratorService;

    public AdminSeeder(HospitalUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       StaffIdGeneratorService staffIdGeneratorService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffIdGeneratorService = staffIdGeneratorService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.debug("AdminSeeder: users already exist, skipping seed.");
            return;
        }

        String userId = staffIdGeneratorService.generateStaffId();
        String hashedPassword = passwordEncoder.encode(adminInitialPassword);

        HospitalUser admin = HospitalUser.builder()
                .userId(userId)
                .username(adminUsername)
                .passwordHash(hashedPassword)
                .role("ADMIN")
                .status("ACTIVE")
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy("SYSTEM")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("SYSTEM")
                .build();

        userRepository.save(admin);
        // HIPAA: log username only — NEVER log the password
        log.info("AdminSeeder: seed ADMIN account created for username: {}", adminUsername);
    }
}
