package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.response.AppointmentSummaryResponse;
import com.ainexus.hospital.patient.dto.response.AvailabilityResponse;
import com.ainexus.hospital.patient.dto.response.PagedResponse;
import com.ainexus.hospital.patient.dto.response.UserSummaryResponse;
import com.ainexus.hospital.patient.service.DoctorAvailabilityService;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorScheduleController {

    private final DoctorAvailabilityService availabilityService;

    public DoctorScheduleController(DoctorAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    // ── List active doctors (for booking form doctor picker) ─────────────────

    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> listDoctors() {
        return ResponseEntity.ok(availabilityService.listActiveDoctors());
    }

    // ── US2: Doctor Schedule ──────────────────────────────────────────────────

    @GetMapping("/{doctorId}/schedule")
    public ResponseEntity<PagedResponse<AppointmentSummaryResponse>> getSchedule(
            @PathVariable String doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.ok(availabilityService.getSchedule(doctorId, date, page, size));
    }

    // ── US5: Doctor Availability ──────────────────────────────────────────────

    @GetMapping("/{doctorId}/availability")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable String doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.ok(availabilityService.getAvailability(doctorId, date));
    }
}
