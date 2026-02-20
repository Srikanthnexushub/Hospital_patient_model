package com.ainexus.hospital.patient.dto.response;

import java.time.LocalTime;

public record TimeSlotResponse(
        LocalTime startTime,
        LocalTime endTime,
        boolean available,
        String appointmentId
) {}
