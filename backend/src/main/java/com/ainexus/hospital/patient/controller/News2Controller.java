package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.News2Response;
import com.ainexus.hospital.patient.service.News2Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class News2Controller {

    private final News2Service news2Service;

    public News2Controller(News2Service news2Service) {
        this.news2Service = news2Service;
    }

    /** US2 — Compute and return the NHS NEWS2 Early Warning Score for a patient. */
    @GetMapping("/patients/{patientId}/news2")
    public ResponseEntity<News2Response> getNews2Score(@PathVariable String patientId) {
        return ResponseEntity.ok(news2Service.getNews2Score(patientId));
    }
}
