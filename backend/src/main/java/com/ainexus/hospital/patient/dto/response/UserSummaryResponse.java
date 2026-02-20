package com.ainexus.hospital.patient.dto.response;

import java.time.OffsetDateTime;

public record UserSummaryResponse(
        String userId,
        String username,
        String role,
        String department,
        String status,
        OffsetDateTime lastLoginAt
) {}
