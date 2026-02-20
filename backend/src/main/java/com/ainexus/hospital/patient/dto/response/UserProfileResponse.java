package com.ainexus.hospital.patient.dto.response;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        String userId,
        String username,
        String role,
        String email,
        String department,
        OffsetDateTime lastLoginAt
) {}
