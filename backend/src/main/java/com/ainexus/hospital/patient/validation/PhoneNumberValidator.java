package com.ainexus.hospital.patient.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    // Accepted formats: +1-XXX-XXX-XXXX | (XXX) XXX-XXXX | XXX-XXX-XXXX
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(\\+1-\\d{3}-\\d{3}-\\d{4}|\\(\\d{3}\\) \\d{3}-\\d{4}|\\d{3}-\\d{3}-\\d{4})$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // null/blank handled by @NotNull / @NotBlank
        }
        return PHONE_PATTERN.matcher(value).matches();
    }
}
