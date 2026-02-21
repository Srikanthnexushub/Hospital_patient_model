package com.ainexus.hospital.patient.exception;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException(String appointmentId) {
        super("Appointment not found: " + appointmentId);
    }
}
