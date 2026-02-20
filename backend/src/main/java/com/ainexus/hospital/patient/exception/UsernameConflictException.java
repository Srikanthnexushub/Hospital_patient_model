package com.ainexus.hospital.patient.exception;

/**
 * Thrown when creating a staff account with a username that already exists.
 * Maps to HTTP 409 Conflict.
 */
public class UsernameConflictException extends RuntimeException {

    public UsernameConflictException(String username) {
        super("Username already exists: " + username);
    }
}
