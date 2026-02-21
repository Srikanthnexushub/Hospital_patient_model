package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.ClinicalAlertResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.mapper.ClinicalAlertMapper;
import com.ainexus.hospital.patient.repository.ClinicalAlertRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.ClinicalAlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalAlertServiceTest {

    @Mock private ClinicalAlertRepository alertRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private ClinicalAlertMapper alertMapper;
    @Mock private EmrAuditService emrAuditService;

    private ClinicalAlertService service;

    private static final String PATIENT_ID = "P2025001";
    private static final UUID ALERT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClinicalAlertService(
                alertRepository, patientRepository, alertMapper,
                emrAuditService, new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    // -------------------------------------------------------------------------
    // createAlert
    // -------------------------------------------------------------------------

    @Test
    void createAlert_news2Type_autoDismissesExistingActiveAlert() {
        setAuth("DOCTOR");

        ClinicalAlert existing = stubAlert(AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE);
        when(alertRepository.findByPatientIdAndAlertTypeAndStatus(
                PATIENT_ID, AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        ClinicalAlert saved = stubAlert(AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE);
        when(alertRepository.save(any(ClinicalAlert.class))).thenReturn(saved);

        service.createAlert(PATIENT_ID, AlertType.NEWS2_CRITICAL, AlertSeverity.CRITICAL,
                "High NEWS2", "Score 8", "NEWS2Service", null);

        // First save: the dismissed existing alert; second save: the new alert
        ArgumentCaptor<ClinicalAlert> captor = ArgumentCaptor.forClass(ClinicalAlert.class);
        verify(alertRepository, times(2)).save(captor.capture());

        ClinicalAlert dismissed = captor.getAllValues().get(0);
        assertThat(dismissed.getStatus()).isEqualTo(AlertStatus.DISMISSED);
        assertThat(dismissed.getDismissReason()).isNotBlank();
    }

    @Test
    void createAlert_nonNews2Type_doesNotAutoDismiss() {
        setAuth("NURSE");

        ClinicalAlert saved = stubAlert(AlertType.LAB_CRITICAL, AlertStatus.ACTIVE);
        when(alertRepository.save(any(ClinicalAlert.class))).thenReturn(saved);

        service.createAlert(PATIENT_ID, AlertType.LAB_CRITICAL, AlertSeverity.CRITICAL,
                "Critical Lab", "K+ 7.2", "LabOrderService", "7.2");

        // findByPatientIdAndAlertTypeAndStatus must NOT be called for non-NEWS2 types
        verify(alertRepository, never()).findByPatientIdAndAlertTypeAndStatus(any(), any(), any());
        // Only one save: the new alert (no dismissal)
        verify(alertRepository, times(1)).save(any(ClinicalAlert.class));
    }

    @Test
    void createAlert_news2Type_noExistingAlert_savesDirectly() {
        setAuth("DOCTOR");

        when(alertRepository.findByPatientIdAndAlertTypeAndStatus(
                PATIENT_ID, AlertType.NEWS2_HIGH, AlertStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ClinicalAlert saved = stubAlert(AlertType.NEWS2_HIGH, AlertStatus.ACTIVE);
        when(alertRepository.save(any(ClinicalAlert.class))).thenReturn(saved);

        service.createAlert(PATIENT_ID, AlertType.NEWS2_HIGH, AlertSeverity.WARNING,
                "Medium NEWS2", "Score 4", "NEWS2Service", null);

        // Only one save (no existing to dismiss)
        verify(alertRepository, times(1)).save(any(ClinicalAlert.class));
    }

    // -------------------------------------------------------------------------
    // acknowledge
    // -------------------------------------------------------------------------

    @Test
    void acknowledge_setsAcknowledgedFieldsFromAuthContext() {
        setAuth("DOCTOR");

        ClinicalAlert alert = stubAlert(AlertType.LAB_CRITICAL, AlertStatus.ACTIVE);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(ClinicalAlert.class))).thenReturn(alert);

        ClinicalAlertResponse response = stubResponse();
        when(alertMapper.toResponse(any(ClinicalAlert.class))).thenReturn(response);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        service.acknowledge(ALERT_ID);

        ArgumentCaptor<ClinicalAlert> captor = ArgumentCaptor.forClass(ClinicalAlert.class);
        verify(alertRepository).save(captor.capture());

        ClinicalAlert saved = captor.getValue();
        assertThat(saved.getAcknowledgedBy()).isEqualTo("doctor1");
        assertThat(saved.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void acknowledge_forbiddenForReceptionist() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> service.acknowledge(ALERT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // dismiss
    // -------------------------------------------------------------------------

    @Test
    void dismiss_setsDismissedStatusAndReason() {
        setAuth("NURSE");

        ClinicalAlert alert = stubAlert(AlertType.NEWS2_CRITICAL, AlertStatus.ACTIVE);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(ClinicalAlert.class))).thenReturn(alert);

        ClinicalAlertResponse response = stubResponse();
        when(alertMapper.toResponse(any(ClinicalAlert.class))).thenReturn(response);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        service.dismiss(ALERT_ID, "Patient stable, alert resolved");

        ArgumentCaptor<ClinicalAlert> captor = ArgumentCaptor.forClass(ClinicalAlert.class);
        verify(alertRepository).save(captor.capture());

        ClinicalAlert saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.DISMISSED);
        assertThat(saved.getDismissReason()).isEqualTo("Patient stable, alert resolved");
        assertThat(saved.getDismissedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // getGlobalAlerts
    // -------------------------------------------------------------------------

    @Test
    void getGlobalAlerts_asDoctor_passesDoctorIdToRepository() {
        AuthContext.Holder.set(new AuthContext("doc001", "doctor1", "DOCTOR"));
        Pageable pageable = PageRequest.of(0, 20);

        when(alertRepository.findGlobalFiltered(null, null, "doc001", pageable))
                .thenReturn(Page.empty());

        service.getGlobalAlerts(null, null, pageable);

        verify(alertRepository).findGlobalFiltered(null, null, "doc001", pageable);
    }

    @Test
    void getGlobalAlerts_asAdmin_passesNullDoctorIdToRepository() {
        AuthContext.Holder.set(new AuthContext("adm001", "admin1", "ADMIN"));
        Pageable pageable = PageRequest.of(0, 20);

        when(alertRepository.findGlobalFiltered(null, null, null, pageable))
                .thenReturn(Page.empty());

        service.getGlobalAlerts(null, null, pageable);

        verify(alertRepository).findGlobalFiltered(null, null, null, pageable);
    }

    @Test
    void getGlobalAlerts_asNurse_passesNullDoctorIdToRepository() {
        AuthContext.Holder.set(new AuthContext("nur001", "nurse1", "NURSE"));
        Pageable pageable = PageRequest.of(0, 20);

        when(alertRepository.findGlobalFiltered(null, null, null, pageable))
                .thenReturn(Page.empty());

        service.getGlobalAlerts(null, null, pageable);

        verify(alertRepository).findGlobalFiltered(null, null, null, pageable);
    }

    @Test
    void getGlobalAlerts_enrichesPatientNamesFromBatchLoad() {
        AuthContext.Holder.set(new AuthContext("adm001", "admin1", "ADMIN"));
        Pageable pageable = PageRequest.of(0, 20);

        ClinicalAlert alert = stubAlert(AlertType.LAB_CRITICAL, AlertStatus.ACTIVE);

        when(alertRepository.findGlobalFiltered(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(alert)));

        ClinicalAlertResponse baseResponse = stubResponse();
        when(alertMapper.toResponse(alert)).thenReturn(baseResponse);

        Patient patient = new Patient();
        patient.setPatientId(PATIENT_ID);
        patient.setFirstName("John");
        patient.setLastName("Doe");
        when(patientRepository.findAllById(Set.of(PATIENT_ID)))
                .thenReturn(List.of(patient));

        Page<ClinicalAlertResponse> result = service.getGlobalAlerts(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).patientName()).isEqualTo("John Doe");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ClinicalAlert stubAlert(AlertType type, AlertStatus status) {
        ClinicalAlert a = new ClinicalAlert();
        a.setId(ALERT_ID);
        a.setPatientId(PATIENT_ID);
        a.setAlertType(type);
        a.setSeverity(AlertSeverity.CRITICAL);
        a.setTitle("Test alert");
        a.setDescription("Desc");
        a.setSource("Test");
        a.setStatus(status);
        a.setCreatedAt(OffsetDateTime.now());
        return a;
    }

    private ClinicalAlertResponse stubResponse() {
        return new ClinicalAlertResponse(
                ALERT_ID, PATIENT_ID, null,
                AlertType.LAB_CRITICAL, AlertSeverity.CRITICAL,
                "Test alert", "Desc", "Test", null,
                AlertStatus.ACTIVE, OffsetDateTime.now(),
                null, null, null, null);
    }
}
