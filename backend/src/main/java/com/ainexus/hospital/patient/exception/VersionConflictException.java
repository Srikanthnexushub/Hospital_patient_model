package com.ainexus.hospital.patient.exception;

/**
 * Thrown when an update is attempted with a stale resource version.
 * Maps to HTTP 409 Conflict.
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }
}
