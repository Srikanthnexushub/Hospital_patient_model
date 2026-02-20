package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.AppointmentResponse;
import com.ainexus.hospital.patient.dto.response.AppointmentSummaryResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import org.springframework.stereotype.Component;

/**
 * Maps Appointment entities to response DTOs.
 *
 * patientName and doctorName are not stored on the Appointment entity — they
 * are resolved in the service layer from the patients / hospital_users tables
 * and passed in as parameters to avoid N+1 queries or cross-entity coupling.
 */
@Component
public class AppointmentMapper {

    public AppointmentResponse toResponse(Appointment a, String patientName, String doctorName) {
        return new AppointmentResponse(
                a.getAppointmentId(),
                a.getPatientId(),
                patientName,
                a.getDoctorId(),
                doctorName,
                a.getAppointmentDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getDurationMinutes(),
                a.getType(),
                a.getStatus(),
                a.getReason(),
                a.getNotes(),
                a.getCancelReason(),
                a.getVersion(),
                a.getCreatedAt(),
                a.getCreatedBy(),
                a.getUpdatedAt(),
                a.getUpdatedBy()
        );
    }

    public AppointmentSummaryResponse toSummary(Appointment a, String patientName, String doctorName) {
        return new AppointmentSummaryResponse(
                a.getAppointmentId(),
                a.getPatientId(),
                patientName,
                a.getDoctorId(),
                doctorName,
                a.getAppointmentDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getDurationMinutes(),
                a.getType(),
                a.getStatus()
        );
    }
}
