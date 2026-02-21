package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.dto.AlertTypeCountDto;
import com.ainexus.hospital.patient.dto.DashboardStatsResponse;
import com.ainexus.hospital.patient.dto.PatientRiskRow;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.intelligence.News2Calculator;
import com.ainexus.hospital.patient.intelligence.News2Result;
import com.ainexus.hospital.patient.repository.*;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Risk-ranked patient dashboard service.
 *
 * <p>Aggregates clinical alert counts, NEWS2 scores, and patient record counts to produce
 * a danger-ranked view of all patients (ADMIN) or a doctor's own patient population (DOCTOR).
 * Sort order: criticalAlertCount DESC → news2Score DESC NULLS LAST → warningAlertCount DESC.
 */
@Service
public class PatientRiskDashboardService {

    private final ClinicalAlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final VitalsRepository vitalsRepository;
    private final MedicationRepository medicationRepository;
    private final ProblemRepository problemRepository;
    private final AllergyRepository allergyRepository;
    private final AppointmentRepository appointmentRepository;
    private final News2Calculator news2Calculator;
    private final RoleGuard roleGuard;

    public PatientRiskDashboardService(ClinicalAlertRepository alertRepository,
                                        PatientRepository patientRepository,
                                        VitalsRepository vitalsRepository,
                                        MedicationRepository medicationRepository,
                                        ProblemRepository problemRepository,
                                        AllergyRepository allergyRepository,
                                        AppointmentRepository appointmentRepository,
                                        News2Calculator news2Calculator,
                                        RoleGuard roleGuard) {
        this.alertRepository = alertRepository;
        this.patientRepository = patientRepository;
        this.vitalsRepository = vitalsRepository;
        this.medicationRepository = medicationRepository;
        this.problemRepository = problemRepository;
        this.allergyRepository = allergyRepository;
        this.appointmentRepository = appointmentRepository;
        this.news2Calculator = news2Calculator;
        this.roleGuard = roleGuard;
    }

    // -------------------------------------------------------------------------
    // Risk-ranked patient list
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PatientRiskRow> getRiskRankedPatients(Pageable pageable) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();
        String doctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        // Determine patient ID scope
        List<String> patientIds = resolvePatientIds(doctorId);

        // Batch-load alert counts for efficiency
        Map<String, ClinicalAlertRepository.PatientAlertCounts> alertCountMap =
                alertRepository.getPatientAlertCounts(doctorId).stream()
                        .collect(Collectors.toMap(
                                ClinicalAlertRepository.PatientAlertCounts::getPatientId,
                                c -> c));

        // Build rows
        List<PatientRiskRow> rows = patientIds.stream()
                .map(pid -> buildRiskRow(pid, alertCountMap))
                .filter(Objects::nonNull)
                .sorted(riskComparator())
                .collect(Collectors.toList());

        // Manual pagination over the in-memory sorted list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        List<PatientRiskRow> pageContent = (start < rows.size()) ? rows.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, rows.size());
    }

    // -------------------------------------------------------------------------
    // Stats snapshot
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        roleGuard.requireRoles("DOCTOR", "ADMIN");

        long totalActivePatients = patientRepository.countByStatus(PatientStatus.ACTIVE);
        long patientsWithCriticalAlerts = alertRepository.countDistinctPatientsWithCriticalAlerts();
        long patientsWithHighNews2 = alertRepository.countDistinctPatientsByAlertTypeAndStatus(
                AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE);
        long totalActiveAlerts = alertRepository.countByStatus(AlertStatus.ACTIVE);
        long totalCriticalAlerts = alertRepository.countBySeverityAndStatus(AlertSeverity.CRITICAL, AlertStatus.ACTIVE);
        long totalWarningAlerts = alertRepository.countBySeverityAndStatus(AlertSeverity.WARNING, AlertStatus.ACTIVE);

        List<AlertTypeCountDto> alertsByType = alertRepository
                .countByAlertTypeGrouped(AlertStatus.ACTIVE).stream()
                .map(p -> new AlertTypeCountDto(p.getAlertType(), p.getCount()))
                .toList();

        return new DashboardStatsResponse(
                totalActivePatients,
                patientsWithCriticalAlerts,
                patientsWithHighNews2,
                totalActiveAlerts,
                totalCriticalAlerts,
                totalWarningAlerts,
                alertsByType,
                OffsetDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> resolvePatientIds(String doctorId) {
        if (doctorId != null) {
            return appointmentRepository.findDistinctPatientIdsByDoctorId(doctorId);
        }
        return patientRepository.findAll().stream()
                .filter(p -> p.getStatus() == PatientStatus.ACTIVE)
                .map(Patient::getPatientId)
                .collect(Collectors.toList());
    }

    private PatientRiskRow buildRiskRow(String patientId,
                                         Map<String, ClinicalAlertRepository.PatientAlertCounts> alertCountMap) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return null;

        ClinicalAlertRepository.PatientAlertCounts counts = alertCountMap.get(patientId);
        long criticalCount = counts != null ? counts.getCriticalCount() : 0L;
        long warningCount  = counts != null ? counts.getWarningCount()  : 0L;

        PatientVitals latestVitals = vitalsRepository
                .findTop5ByPatientIdOrderByRecordedAtDesc(patientId)
                .stream().findFirst().orElse(null);

        News2Result news2 = news2Calculator.compute(latestVitals);

        long medCount     = medicationRepository.findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE).size();
        long probCount    = problemRepository.findByPatientIdAndStatus(patientId, ProblemStatus.ACTIVE).size();
        long allergyCount = allergyRepository.findByPatientIdAndActiveTrue(patientId).size();

        OffsetDateTime lastVitalsAt = latestVitals != null ? latestVitals.getRecordedAt() : null;

        java.time.LocalDate lastVisitDate = appointmentRepository
                .findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(patientId, AppointmentStatus.COMPLETED)
                .map(Appointment::getAppointmentDate)
                .orElse(null);

        String bloodGroup = patient.getBloodGroup() != null
                ? patient.getBloodGroup().getDisplayValue()
                : null;

        return new PatientRiskRow(
                patientId,
                patient.getFirstName() + " " + patient.getLastName(),
                bloodGroup,
                "NO_DATA".equals(news2.riskLevel()) ? null : news2.totalScore(),
                news2.riskLevel(),
                news2.riskColour(),
                criticalCount, warningCount,
                medCount, probCount, allergyCount,
                lastVitalsAt, lastVisitDate);
    }

    private Comparator<PatientRiskRow> riskComparator() {
        return Comparator
                .comparingLong(PatientRiskRow::criticalAlertCount).reversed()
                .thenComparing(Comparator.comparingInt(
                        (PatientRiskRow r) -> r.news2Score() != null ? r.news2Score() : -1).reversed())
                .thenComparing(Comparator.comparingLong(PatientRiskRow::warningAlertCount).reversed());
    }
}
