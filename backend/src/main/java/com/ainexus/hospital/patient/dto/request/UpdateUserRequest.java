package com.ainexus.hospital.patient.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All fields are optional — only non-null fields are applied (PATCH semantics).
 */
public record UpdateUserRequest(

        @Email(message = "Email must be a valid address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @Size(max = 100, message = "Department must not exceed 100 characters")
        String department,

        @Pattern(regexp = "^(RECEPTIONIST|DOCTOR|NURSE|ADMIN)$",
                 message = "Role must be RECEPTIONIST, DOCTOR, NURSE, or ADMIN")
        String role
) {}
