package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.AppointmentType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateAppointmentRequest(

        @FutureOrPresent(message = "appointmentDate must not be in the past")
        LocalDate appointmentDate,

        LocalTime startTime,

        Integer durationMinutes,

        AppointmentType type,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes
) {}
