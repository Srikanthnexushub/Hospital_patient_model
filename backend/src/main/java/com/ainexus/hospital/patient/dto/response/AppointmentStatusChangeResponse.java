package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.AppointmentStatus;

public record AppointmentStatusChangeResponse(
        String appointmentId,
        AppointmentStatus previousStatus,
        AppointmentStatus newStatus,
        String message
) {}
