package com.ainexus.hospital.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that the annotated String matches one of three US phone formats:
 * - +1-XXX-XXX-XXXX
 * - (XXX) XXX-XXXX
 * - XXX-XXX-XXXX
 *
 * Null values are considered valid (use @NotNull separately for mandatory fields).
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {
    String message() default "Phone number must match: +1-XXX-XXX-XXXX, (XXX) XXX-XXXX, or XXX-XXX-XXXX.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
