package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.*;
import com.ainexus.hospital.patient.entity.LabOrderStatus;
import com.ainexus.hospital.patient.service.LabOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LabOrderController {

    private final LabOrderService labOrderService;

    public LabOrderController(LabOrderService labOrderService) {
        this.labOrderService = labOrderService;
    }

    /** US1 — Place a new lab order for a patient. Roles: DOCTOR, ADMIN. */
    @PostMapping("/patients/{patientId}/lab-orders")
    public ResponseEntity<LabOrderResponse> createLabOrder(
            @PathVariable String patientId,
            @Valid @RequestBody CreateLabOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labOrderService.createLabOrder(patientId, request));
    }

    /** US1 — List lab orders for a patient with optional status filter. Roles: DOCTOR, NURSE, ADMIN. */
    @GetMapping("/patients/{patientId}/lab-orders")
    public ResponseEntity<Page<LabOrderSummaryResponse>> getLabOrders(
            @PathVariable String patientId,
            @RequestParam(required = false) LabOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(labOrderService.getLabOrders(
                patientId, status,
                PageRequest.of(page, size, Sort.by("orderedAt").descending())));
    }

    /** US1 — Record a lab result for an order. Roles: NURSE, DOCTOR, ADMIN. */
    @PostMapping("/lab-orders/{orderId}/result")
    public ResponseEntity<LabResultResponse> recordLabResult(
            @PathVariable UUID orderId,
            @Valid @RequestBody RecordLabResultRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labOrderService.recordLabResult(orderId, request));
    }

    /** US1 — Paginated lab result history for a patient. Roles: DOCTOR, NURSE, ADMIN. */
    @GetMapping("/patients/{patientId}/lab-results")
    public ResponseEntity<Page<LabResultResponse>> getLabResults(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(labOrderService.getLabResults(
                patientId,
                PageRequest.of(page, size, Sort.by("resultedAt").descending())));
    }
}
