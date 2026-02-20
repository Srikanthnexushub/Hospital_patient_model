package com.ainexus.hospital.patient.dto.response;

import java.time.Instant;

public record TokenResponse(
        String token,
        String userId,
        String username,
        String role,
        Instant expiresAt
) {}
