package com.ainexus.hospital.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint that ensures a RecordVitalsRequest has at least one
 * non-null measurement field. Applied to the request record class.
 */
@Documented
@Constraint(validatedBy = AtLeastOneVitalPresent.Validator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AtLeastOneVitalPresent {
    String message() default "At least one vital measurement must be provided.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<AtLeastOneVitalPresent, VitalsPayload> {
        @Override
        public boolean isValid(VitalsPayload value, ConstraintValidatorContext context) {
            if (value == null) return false;
            return value.bloodPressureSystolic() != null
                    || value.bloodPressureDiastolic() != null
                    || value.heartRate() != null
                    || value.temperature() != null
                    || value.weight() != null
                    || value.height() != null
                    || value.oxygenSaturation() != null
                    || value.respiratoryRate() != null;
        }
    }

    /** Interface that RecordVitalsRequest must implement for the validator. */
    interface VitalsPayload {
        Integer bloodPressureSystolic();
        Integer bloodPressureDiastolic();
        Integer heartRate();
        java.math.BigDecimal temperature();
        java.math.BigDecimal weight();
        java.math.BigDecimal height();
        Integer oxygenSaturation();
        Integer respiratoryRate();
    }
}
