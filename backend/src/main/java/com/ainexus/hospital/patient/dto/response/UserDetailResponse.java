package com.ainexus.hospital.patient.dto.response;

import java.time.OffsetDateTime;

public record UserDetailResponse(
        String userId,
        String username,
        String role,
        String email,
        String department,
        String status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        String createdBy,
        Integer failedAttempts
) {}
