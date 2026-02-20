package com.ainexus.hospital.patient.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        String doctorId,
        String doctorName,
        List<TimeSlotResponse> slots
) {}
