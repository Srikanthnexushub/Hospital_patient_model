package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.AppointmentType;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentSummaryResponse(
        String appointmentId,
        String patientId,
        String patientName,
        String doctorId,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer durationMinutes,
        AppointmentType type,
        AppointmentStatus status
) {}
