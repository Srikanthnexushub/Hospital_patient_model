package com.ainexus.hospital.patient.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class ValidDateOfBirthValidator implements ConstraintValidator<ValidDateOfBirth, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null handled by @NotNull
        }

        LocalDate today = LocalDate.now();

        if (!value.isBefore(today)) {
            // Today or future date
            String message = value.isEqual(today)
                    ? "Date of birth cannot be today."
                    : "Date of birth cannot be a future date.";
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            return false;
        }

        if (value.isBefore(today.minusYears(150))) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Date of birth must be within the last 150 years."
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
