package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.dto.News2Response;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.intelligence.News2Calculator;
import com.ainexus.hospital.patient.intelligence.News2ComponentScore;
import com.ainexus.hospital.patient.intelligence.News2Result;
import com.ainexus.hospital.patient.repository.VitalsRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.ClinicalAlertService;
import com.ainexus.hospital.patient.service.News2Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class News2ServiceTest {

    @Mock private VitalsRepository vitalsRepository;
    @Mock private News2Calculator news2Calculator;
    @Mock private ClinicalAlertService clinicalAlertService;

    private News2Service news2Service;

    private static final String PATIENT_ID = "P2025001";

    @BeforeEach
    void setUp() {
        news2Service = new News2Service(vitalsRepository, news2Calculator, clinicalAlertService, new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    @Test
    void getNews2Score_highRisk_createsNews2CriticalAlert() {
        setAuth("DOCTOR");
        PatientVitals vitals = stubVitals();
        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(PATIENT_ID))
                .thenReturn(List.of(vitals));

        News2Result highResult = new News2Result(9, "HIGH", "red",
                "Emergency clinical assessment required immediately",
                List.of(), 1L, OffsetDateTime.now(), null);
        when(news2Calculator.compute(vitals)).thenReturn(highResult);

        ClinicalAlert alert = new ClinicalAlert();
        alert.setId(java.util.UUID.randomUUID());
        when(clinicalAlertService.createAlert(
                eq(PATIENT_ID), eq(AlertType.NEWS2_CRITICAL), eq(AlertSeverity.CRITICAL),
                any(), any(), any(), any())).thenReturn(alert);

        News2Response response = news2Service.getNews2Score(PATIENT_ID);

        assertThat(response.riskLevel()).isEqualTo("HIGH");
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), eq(AlertType.NEWS2_CRITICAL), eq(AlertSeverity.CRITICAL),
                any(), any(), any(), any());
    }

    @Test
    void getNews2Score_mediumRisk_createsNews2HighAlert() {
        setAuth("NURSE");
        PatientVitals vitals = stubVitals();
        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(PATIENT_ID))
                .thenReturn(List.of(vitals));

        News2Result mediumResult = new News2Result(5, "MEDIUM", "orange",
                "Urgent review within 1 hour",
                List.of(), 1L, OffsetDateTime.now(), null);
        when(news2Calculator.compute(vitals)).thenReturn(mediumResult);

        ClinicalAlert alert = new ClinicalAlert();
        alert.setId(java.util.UUID.randomUUID());
        when(clinicalAlertService.createAlert(
                eq(PATIENT_ID), eq(AlertType.NEWS2_HIGH), eq(AlertSeverity.WARNING),
                any(), any(), any(), any())).thenReturn(alert);

        News2Response response = news2Service.getNews2Score(PATIENT_ID);

        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), eq(AlertType.NEWS2_HIGH), eq(AlertSeverity.WARNING),
                any(), any(), any(), any());
    }

    @Test
    void getNews2Score_lowRisk_doesNotCreateAlert() {
        setAuth("DOCTOR");
        PatientVitals vitals = stubVitals();
        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(PATIENT_ID))
                .thenReturn(List.of(vitals));

        News2Result lowResult = new News2Result(0, "LOW", "green",
                "Routine ward monitoring",
                List.of(), 1L, OffsetDateTime.now(), null);
        when(news2Calculator.compute(vitals)).thenReturn(lowResult);

        news2Service.getNews2Score(PATIENT_ID);

        verify(clinicalAlertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getNews2Score_noVitals_returnsNoDataAndNoAlert() {
        setAuth("DOCTOR");
        when(vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(PATIENT_ID))
                .thenReturn(List.of());

        News2Result noDataResult = new News2Result(null, "NO_DATA", null, null,
                List.of(), null, OffsetDateTime.now(), "No vitals on record");
        when(news2Calculator.compute(null)).thenReturn(noDataResult);

        News2Response response = news2Service.getNews2Score(PATIENT_ID);

        assertThat(response.riskLevel()).isEqualTo("NO_DATA");
        assertThat(response.message()).isEqualTo("No vitals on record");
        verify(clinicalAlertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getNews2Score_receptionistRole_throwsForbiddenException() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> news2Service.getNews2Score(PATIENT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    private PatientVitals stubVitals() {
        PatientVitals v = new PatientVitals();
        v.setId(1L);
        v.setPatientId(PATIENT_ID);
        v.setRespiratoryRate(16);
        v.setOxygenSaturation(98);
        v.setBloodPressureSystolic(120);
        v.setHeartRate(72);
        v.setTemperature(new BigDecimal("37.0"));
        v.setRecordedAt(OffsetDateTime.now());
        return v;
    }
}
