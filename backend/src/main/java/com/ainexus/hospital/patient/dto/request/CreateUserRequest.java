package com.ainexus.hospital.patient.dto.request;

import jakarta.validation.constraints.*;

public record CreateUserRequest(

        @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
                 message = "Username may only contain letters, digits, underscores, and hyphens")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Role must not be blank")
        @Pattern(regexp = "^(RECEPTIONIST|DOCTOR|NURSE|ADMIN)$",
                 message = "Role must be RECEPTIONIST, DOCTOR, NURSE, or ADMIN")
        String role,

        @Email(message = "Email must be a valid address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @Size(max = 100, message = "Department must not exceed 100 characters")
        String department
) {}
