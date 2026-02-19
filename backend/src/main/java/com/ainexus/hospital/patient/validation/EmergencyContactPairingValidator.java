package com.ainexus.hospital.patient.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EmergencyContactPairingValidator
        implements ConstraintValidator<EmergencyContactPairing, HasEmergencyContact> {

    @Override
    public boolean isValid(HasEmergencyContact request, ConstraintValidatorContext context) {
        if (request == null) return true;

        boolean hasName = isPresent(request.emergencyContactName());
        boolean hasPhone = isPresent(request.emergencyContactPhone());

        if (hasName && !hasPhone) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Emergency contact phone is required when a contact name is provided."
            ).addPropertyNode("emergencyContactPhone").addConstraintViolation();
            return false;
        }

        if (!hasName && hasPhone) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Emergency contact name is required when a contact phone is provided."
            ).addPropertyNode("emergencyContactName").addConstraintViolation();
            return false;
        }

        return true;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
