package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.dto.DashboardStatsResponse;
import com.ainexus.hospital.patient.dto.PatientRiskRow;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.intelligence.News2Calculator;
import com.ainexus.hospital.patient.intelligence.News2Result;
import com.ainexus.hospital.patient.repository.*;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.PatientRiskDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientRiskDashboardServiceTest {

    @Mock private ClinicalAlertRepository alertRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private VitalsRepository vitalsRepository;
    @Mock private MedicationRepository medicationRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private AllergyRepository allergyRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private News2Calculator news2Calculator;

    private PatientRiskDashboardService service;

    @BeforeEach
    void setUp() {
        service = new PatientRiskDashboardService(
                alertRepository, patientRepository, vitalsRepository,
                medicationRepository, problemRepository, allergyRepository,
                appointmentRepository, news2Calculator, new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        setAuth(role, "uid001");
    }

    private void setAuth(String role, String userId) {
        AuthContext.Holder.set(new AuthContext(userId, role.toLowerCase() + "1", role));
    }

    // -------------------------------------------------------------------------
    // Role guards
    // -------------------------------------------------------------------------

    @Test
    void getRiskRankedPatients_receptionistIsForbidden() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> service.getRiskRankedPatients(PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getStats_receptionistIsForbidden() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(service::getStats)
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // ADMIN sees all patients
    // -------------------------------------------------------------------------

    @Test
    void getRiskRankedPatients_adminSeesAllActivePatients() {
        setAuth("ADMIN");

        Patient p1 = patient("P2025001", "Alice", "Smith");
        Patient p2 = patient("P2025002", "Bob", "Jones");
        when(patientRepository.findAll()).thenReturn(List.of(p1, p2));
        when(alertRepository.getPatientAlertCounts(null)).thenReturn(List.of());

        stubBuildRow("P2025001");
        stubBuildRow("P2025002");

        Page<PatientRiskRow> page = service.getRiskRankedPatients(PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        // appointmentRepository.findDistinctPatientIdsByDoctorId must NOT be called for ADMIN
        verify(appointmentRepository, never()).findDistinctPatientIdsByDoctorId(anyString());
    }

    // -------------------------------------------------------------------------
    // DOCTOR sees only own patients
    // -------------------------------------------------------------------------

    @Test
    void getRiskRankedPatients_doctorScopedToOwnPatients() {
        setAuth("DOCTOR", "doc001");

        when(appointmentRepository.findDistinctPatientIdsByDoctorId("doc001"))
                .thenReturn(List.of("P2025001"));
        when(alertRepository.getPatientAlertCounts("doc001")).thenReturn(List.of());

        stubBuildRow("P2025001");

        Page<PatientRiskRow> page = service.getRiskRankedPatients(PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(patientRepository, never()).findAll();
    }

    // -------------------------------------------------------------------------
    // Sort order: criticalAlertCount DESC, news2Score DESC, warningAlertCount DESC
    // -------------------------------------------------------------------------

    @Test
    void getRiskRankedPatients_sortsByCriticalAlertCountDescending() {
        setAuth("ADMIN");

        Patient lowRisk  = patient("P2025001", "Alice", "Smith");
        Patient highRisk = patient("P2025002", "Bob", "Jones");
        when(patientRepository.findAll()).thenReturn(List.of(lowRisk, highRisk));

        // P2025002 has 3 critical alerts; P2025001 has 0
        ClinicalAlertRepository.PatientAlertCounts highCounts = alertCounts("P2025002", 3L, 0L);
        when(alertRepository.getPatientAlertCounts(null)).thenReturn(List.of(highCounts));

        stubBuildRowWithNews2("P2025001", null, "NO_DATA", 0L, 0L);
        stubBuildRowWithNews2("P2025002", 7, "HIGH",       3L, 1L);

        Page<PatientRiskRow> page = service.getRiskRankedPatients(PageRequest.of(0, 20));

        List<PatientRiskRow> rows = page.getContent();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).patientId()).isEqualTo("P2025002");
        assertThat(rows.get(1).patientId()).isEqualTo("P2025001");
    }

    // -------------------------------------------------------------------------
    // No vitals → news2Score null in row
    // -------------------------------------------------------------------------

    @Test
    void getRiskRankedPatients_noVitals_news2ScoreIsNull() {
        setAuth("ADMIN");

        Patient p = patient("P2025001", "Alice", "Smith");
        when(patientRepository.findAll()).thenReturn(List.of(p));
        when(alertRepository.getPatientAlertCounts(null)).thenReturn(List.of());

        when(patientRepository.findById("P2025001")).thenReturn(Optional.of(p));
        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc("P2025001"))
                .thenReturn(List.of());
        News2Result noData = new News2Result(null, "NO_DATA", null, null, List.of(), null, null, "No vitals");
        when(news2Calculator.compute(null)).thenReturn(noData);
        when(medicationRepository.findByPatientIdAndStatus("P2025001", MedicationStatus.ACTIVE))
                .thenReturn(List.of());
        when(problemRepository.findByPatientIdAndStatus("P2025001", ProblemStatus.ACTIVE))
                .thenReturn(List.of());
        when(allergyRepository.findByPatientIdAndActiveTrue("P2025001"))
                .thenReturn(List.of());
        when(appointmentRepository.findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(
                "P2025001", AppointmentStatus.COMPLETED))
                .thenReturn(Optional.empty());

        Page<PatientRiskRow> page = service.getRiskRankedPatients(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).news2Score()).isNull();
        assertThat(page.getContent().get(0).news2RiskLevel()).isEqualTo("NO_DATA");
    }

    // -------------------------------------------------------------------------
    // getStats
    // -------------------------------------------------------------------------

    @Test
    void getStats_returnsAggregatedCounts() {
        setAuth("ADMIN");

        when(patientRepository.countByStatus(PatientStatus.ACTIVE)).thenReturn(50L);
        when(alertRepository.countDistinctPatientsWithCriticalAlerts()).thenReturn(12L);
        when(alertRepository.countDistinctPatientsByAlertTypeAndStatus(
                AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE)).thenReturn(3L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(35L);
        when(alertRepository.countBySeverityAndStatus(AlertSeverity.CRITICAL, AlertStatus.ACTIVE))
                .thenReturn(15L);
        when(alertRepository.countBySeverityAndStatus(AlertSeverity.WARNING, AlertStatus.ACTIVE))
                .thenReturn(20L);
        when(alertRepository.countByAlertTypeGrouped(AlertStatus.ACTIVE)).thenReturn(List.of());

        DashboardStatsResponse stats = service.getStats();

        assertThat(stats.totalActivePatients()).isEqualTo(50L);
        assertThat(stats.patientsWithCriticalAlerts()).isEqualTo(12L);
        assertThat(stats.patientsWithHighNews2()).isEqualTo(3L);
        assertThat(stats.totalActiveAlerts()).isEqualTo(35L);
        assertThat(stats.totalCriticalAlerts()).isEqualTo(15L);
        assertThat(stats.totalWarningAlerts()).isEqualTo(20L);
        assertThat(stats.generatedAt()).isNotNull();
    }

    @Test
    void getStats_doctorCanAccess() {
        setAuth("DOCTOR");

        when(patientRepository.countByStatus(any())).thenReturn(0L);
        when(alertRepository.countDistinctPatientsWithCriticalAlerts()).thenReturn(0L);
        when(alertRepository.countDistinctPatientsByAlertTypeAndStatus(any(), any())).thenReturn(0L);
        when(alertRepository.countByStatus(any())).thenReturn(0L);
        when(alertRepository.countBySeverityAndStatus(any(), any())).thenReturn(0L);
        when(alertRepository.countByAlertTypeGrouped(any())).thenReturn(List.of());

        // Should not throw
        DashboardStatsResponse stats = service.getStats();
        assertThat(stats).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Patient patient(String id, String first, String last) {
        Patient p = new Patient();
        p.setPatientId(id);
        p.setFirstName(first);
        p.setLastName(last);
        p.setStatus(PatientStatus.ACTIVE);
        return p;
    }

    /** Stub all repo calls needed by buildRiskRow with minimal (zero) values. */
    private void stubBuildRow(String patientId) {
        stubBuildRowWithNews2(patientId, null, "NO_DATA", 0L, 0L);
    }

    private void stubBuildRowWithNews2(String patientId, Integer score, String riskLevel,
                                        long criticalCount, long warningCount) {
        Patient p = new Patient();
        p.setPatientId(patientId);
        p.setFirstName("Test");
        p.setLastName("Patient");
        p.setStatus(PatientStatus.ACTIVE);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(p));

        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId))
                .thenReturn(List.of());
        News2Result result = new News2Result(score, riskLevel, null, null, List.of(), null,
                OffsetDateTime.now(), null);
        when(news2Calculator.compute(null)).thenReturn(result);

        when(medicationRepository.findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE))
                .thenReturn(List.of());
        when(problemRepository.findByPatientIdAndStatus(patientId, ProblemStatus.ACTIVE))
                .thenReturn(List.of());
        when(allergyRepository.findByPatientIdAndActiveTrue(patientId))
                .thenReturn(List.of());
        when(appointmentRepository.findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(
                patientId, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.empty());
    }

    private ClinicalAlertRepository.PatientAlertCounts alertCounts(
            String patientId, long critical, long warning) {
        return new ClinicalAlertRepository.PatientAlertCounts() {
            @Override public String getPatientId() { return patientId; }
            @Override public Long getCriticalCount() { return critical; }
            @Override public Long getWarningCount()  { return warning; }
        };
    }
}
