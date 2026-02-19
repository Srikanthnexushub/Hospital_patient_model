package com.ainexus.hospital.patient.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException() {
        super("You do not have permission to perform this operation.");
    }
}
