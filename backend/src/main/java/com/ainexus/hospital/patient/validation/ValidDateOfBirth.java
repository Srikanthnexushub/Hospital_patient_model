package com.ainexus.hospital.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a LocalDate is a valid date of birth:
 * - Not today
 * - Not a future date
 * - Not more than 150 years in the past
 */
@Documented
@Constraint(validatedBy = ValidDateOfBirthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateOfBirth {
    String message() default "Please enter a valid date of birth.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
