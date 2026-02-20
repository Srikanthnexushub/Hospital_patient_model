package com.ainexus.hospital.patient.audit;

import com.ainexus.hospital.patient.entity.AuthAuditLog;
import com.ainexus.hospital.patient.repository.AuthAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Writes immutable audit log entries for every authentication and staff management event.
 *
 * HIPAA restriction: details field MUST NOT contain passwords, tokens, PHI, or secrets.
 * This service MUST be called within an active @Transactional scope.
 */
@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditLogRepository auditLogRepository;

    public AuthAuditService(AuthAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an authentication or staff management event.
     *
     * @param eventType     One of: LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, LOGOUT,
     *                      TOKEN_REFRESH, USER_CREATED, USER_UPDATED, USER_DEACTIVATED
     * @param actorUserId   Staff User ID performing the action (NOT username to avoid PHI risk)
     * @param targetUserId  Affected Staff User ID (null for self-actions like login/logout)
     * @param outcome       SUCCESS or FAILURE
     * @param ipAddress     Client IP address (nullable)
     * @param details       Optional context string — MUST NOT contain passwords, tokens, or PHI
     */
    public void writeAuthLog(String eventType,
                              String actorUserId,
                              String targetUserId,
                              String outcome,
                              String ipAddress,
                              String details) {
        AuthAuditLog entry = AuthAuditLog.builder()
                .timestamp(OffsetDateTime.now())
                .eventType(eventType)
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .outcome(outcome)
                .ipAddress(ipAddress)
                .details(details)
                .build();

        auditLogRepository.save(entry);
        log.debug("Auth audit: event={} actor={} outcome={}", eventType, actorUserId, outcome);
    }
}
