package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.dto.News2ComponentScoreDto;
import com.ainexus.hospital.patient.dto.News2Response;
import com.ainexus.hospital.patient.entity.AlertSeverity;
import com.ainexus.hospital.patient.entity.AlertType;
import com.ainexus.hospital.patient.entity.PatientVitals;
import com.ainexus.hospital.patient.intelligence.News2Calculator;
import com.ainexus.hospital.patient.intelligence.News2ComponentScore;
import com.ainexus.hospital.patient.intelligence.News2Result;
import com.ainexus.hospital.patient.repository.VitalsRepository;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Computes NHS NEWS2 scores and fires clinical alerts for deteriorating patients.
 */
@Service
public class News2Service {

    private final VitalsRepository vitalsRepository;
    private final News2Calculator news2Calculator;
    private final ClinicalAlertService clinicalAlertService;
    private final RoleGuard roleGuard;

    public News2Service(VitalsRepository vitalsRepository,
                        News2Calculator news2Calculator,
                        ClinicalAlertService clinicalAlertService,
                        RoleGuard roleGuard) {
        this.vitalsRepository = vitalsRepository;
        this.news2Calculator = news2Calculator;
        this.clinicalAlertService = clinicalAlertService;
        this.roleGuard = roleGuard;
    }

    /**
     * Computes the NHS NEWS2 score from the patient's most recent vitals.
     * Auto-creates or auto-replaces clinical alerts for MEDIUM/HIGH risk.
     */
    @Transactional
    public News2Response getNews2Score(String patientId) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");

        PatientVitals latestVitals = vitalsRepository
                .findTop5ByPatientIdOrderByRecordedAtDesc(patientId)
                .stream()
                .findFirst()
                .orElse(null);

        News2Result result = news2Calculator.compute(latestVitals);

        // Fire alerts for significant risk levels (with deduplication in ClinicalAlertService)
        if ("HIGH".equals(result.riskLevel())) {
            clinicalAlertService.createAlert(
                    patientId,
                    AlertType.NEWS2_CRITICAL,
                    AlertSeverity.CRITICAL,
                    "NEWS2 Critical Risk (Score " + result.totalScore() + ")",
                    "Patient NEWS2 score is " + result.totalScore()
                            + " — " + result.recommendation(),
                    "News2Service",
                    String.valueOf(result.totalScore()));
        } else if ("MEDIUM".equals(result.riskLevel())) {
            clinicalAlertService.createAlert(
                    patientId,
                    AlertType.NEWS2_HIGH,
                    AlertSeverity.WARNING,
                    "NEWS2 Elevated Risk (Score " + result.totalScore() + ")",
                    "Patient NEWS2 score is " + result.totalScore()
                            + " — " + result.recommendation(),
                    "News2Service",
                    String.valueOf(result.totalScore()));
        }

        return toResponse(result);
    }

    private News2Response toResponse(News2Result result) {
        List<News2ComponentScoreDto> components = result.components().stream()
                .map(c -> new News2ComponentScoreDto(
                        c.parameter(), c.value(), c.score(), c.unit(), c.defaulted()))
                .toList();

        return new News2Response(
                result.totalScore(),
                result.riskLevel(),
                result.riskColour(),
                result.recommendation(),
                components,
                result.basedOnVitalsId(),
                result.computedAt(),
                result.message());
    }
}
