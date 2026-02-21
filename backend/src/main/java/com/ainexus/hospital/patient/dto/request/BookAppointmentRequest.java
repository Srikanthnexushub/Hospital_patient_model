package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.AppointmentType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookAppointmentRequest(

        @NotBlank(message = "patientId is required")
        String patientId,

        @NotBlank(message = "doctorId is required")
        String doctorId,

        @NotNull(message = "appointmentDate is required")
        @FutureOrPresent(message = "appointmentDate must not be in the past")
        LocalDate appointmentDate,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "durationMinutes is required")
        Integer durationMinutes,

        @NotNull(message = "type is required")
        AppointmentType type,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes
) {}
