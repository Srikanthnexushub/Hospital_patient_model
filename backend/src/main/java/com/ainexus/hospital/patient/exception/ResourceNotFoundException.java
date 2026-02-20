package com.ainexus.hospital.patient.exception;

/**
 * Thrown when a requested resource (staff user, etc.) is not found.
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
