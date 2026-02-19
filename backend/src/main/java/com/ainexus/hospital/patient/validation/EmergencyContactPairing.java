package com.ainexus.hospital.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint: emergencyContactName and emergencyContactPhone
 * must either both be provided or both be absent.
 */
@Documented
@Constraint(validatedBy = EmergencyContactPairingValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EmergencyContactPairing {
    String message() default "Emergency contact name and phone must both be provided or both be absent.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
