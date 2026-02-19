package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.BloodGroup;
import com.ainexus.hospital.patient.entity.Gender;
import com.ainexus.hospital.patient.validation.EmergencyContactPairing;
import com.ainexus.hospital.patient.validation.PhoneNumber;
import com.ainexus.hospital.patient.validation.ValidDateOfBirth;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@EmergencyContactPairing
public record PatientRegistrationRequest(

        @NotBlank(message = "First name is required.")
        @Size(max = 50, message = "First name must not exceed 50 characters.")
        @Pattern(regexp = "^[A-Za-z\\-\\'\\s]+$",
                 message = "First name may only contain letters, hyphens, apostrophes, and spaces.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 50, message = "Last name must not exceed 50 characters.")
        @Pattern(regexp = "^[A-Za-z\\-\\'\\s]+$",
                 message = "Last name may only contain letters, hyphens, apostrophes, and spaces.")
        String lastName,

        @NotNull(message = "Date of birth is required.")
        @ValidDateOfBirth
        LocalDate dateOfBirth,

        @NotNull(message = "Gender is required.")
        Gender gender,

        BloodGroup bloodGroup,   // Defaults to UNKNOWN in service if null

        @NotBlank(message = "Phone number is required.")
        @PhoneNumber
        String phone,

        @Email(message = "Please enter a valid email address.")
        @Size(max = 100, message = "Email address must not exceed 100 characters.")
        String email,

        @Size(max = 200)
        String address,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Size(max = 20)
        String zipCode,

        @Size(max = 100)
        String emergencyContactName,

        @PhoneNumber
        @Size(max = 20)
        String emergencyContactPhone,

        @Size(max = 50)
        String emergencyContactRelationship,

        @Size(max = 1000, message = "Known allergies must not exceed 1000 characters.")
        String knownAllergies,

        @Size(max = 1000, message = "Chronic conditions must not exceed 1000 characters.")
        String chronicConditions
) {}
