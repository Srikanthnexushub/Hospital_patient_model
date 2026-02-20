package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.CreateProblemRequest;
import com.ainexus.hospital.patient.dto.request.UpdateProblemRequest;
import com.ainexus.hospital.patient.dto.response.ProblemResponse;
import com.ainexus.hospital.patient.service.ProblemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /** US2 — Add a problem to the patient's list. */
    @PostMapping
    public ResponseEntity<ProblemResponse> createProblem(
            @PathVariable String patientId,
            @Valid @RequestBody CreateProblemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemService.createProblem(patientId, request));
    }

    /** US2 — List problems; defaults to ACTIVE only. Use ?status=ALL for full list. */
    @GetMapping
    public ResponseEntity<List<ProblemResponse>> listProblems(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ResponseEntity.ok(problemService.listProblems(patientId, status));
    }

    /** US2 — Partial update of a problem. */
    @PatchMapping("/{problemId}")
    public ResponseEntity<ProblemResponse> updateProblem(
            @PathVariable String patientId,
            @PathVariable UUID problemId,
            @RequestBody UpdateProblemRequest request) {
        return ResponseEntity.ok(problemService.updateProblem(patientId, problemId, request));
    }
}
