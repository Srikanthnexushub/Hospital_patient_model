package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.intelligence.DrugInteractionDatabase;
import com.ainexus.hospital.patient.repository.AllergyRepository;
import com.ainexus.hospital.patient.repository.MedicationRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.ClinicalAlertService;
import com.ainexus.hospital.patient.service.DrugInteractionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrugInteractionServiceTest {

    @Mock private DrugInteractionDatabase interactionDatabase;
    @Mock private MedicationRepository medicationRepository;
    @Mock private AllergyRepository allergyRepository;
    @Mock private ClinicalAlertService clinicalAlertService;
    @Mock private EmrAuditService emrAuditService;

    private DrugInteractionService service;

    private static final String PATIENT_ID = "P2025001";

    @BeforeEach
    void setUp() {
        service = new DrugInteractionService(
                interactionDatabase, medicationRepository, allergyRepository,
                clinicalAlertService, emrAuditService, new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    // -------------------------------------------------------------------------
    // checkInteraction — role guards
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_nurseIsForbidden() {
        setAuth("NURSE");
        assertThatThrownBy(() -> service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("aspirin")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void checkInteraction_receptionistIsForbidden() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("aspirin")))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // checkInteraction — MAJOR interaction
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_majorInteraction_safeIsFalse_andAlertFired() {
        setAuth("DOCTOR");

        PatientMedication warfarin = activeMed("warfarin");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(warfarin));
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        DrugInteractionDto majorInteraction = new DrugInteractionDto(
                "aspirin", "warfarin", InteractionSeverity.MAJOR,
                "Mechanism", "Increased bleeding", "Avoid");
        when(interactionDatabase.findInteraction("aspirin", "warfarin"))
                .thenReturn(Optional.of(majorInteraction));

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("aspirin"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.interactions()).hasSize(1);
        assertThat(resp.interactions().get(0).severity()).isEqualTo(InteractionSeverity.MAJOR);
        assertThat(resp.allergyContraindications()).isEmpty();

        ArgumentCaptor<AlertType> typeCaptor = ArgumentCaptor.forClass(AlertType.class);
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), typeCaptor.capture(), eq(AlertSeverity.CRITICAL),
                anyString(), anyString(), anyString(), anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(AlertType.DRUG_INTERACTION);
    }

    // -------------------------------------------------------------------------
    // checkInteraction — CONTRAINDICATED interaction
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_contraindicatedInteraction_alertFired() {
        setAuth("DOCTOR");

        PatientMedication maoi = activeMed("maoi");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(maoi));
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        DrugInteractionDto contraindicated = new DrugInteractionDto(
                "ssri", "maoi", InteractionSeverity.CONTRAINDICATED,
                "Serotonin syndrome", "Life-threatening", "Contraindicated");
        when(interactionDatabase.findInteraction("ssri", "maoi"))
                .thenReturn(Optional.of(contraindicated));

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("ssri"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.interactions().get(0).severity()).isEqualTo(InteractionSeverity.CONTRAINDICATED);
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), eq(AlertType.DRUG_INTERACTION), eq(AlertSeverity.CRITICAL),
                anyString(), anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // checkInteraction — MODERATE interaction (no alert)
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_moderateInteraction_safeIsFalse_butNoAlertFired() {
        setAuth("DOCTOR");

        PatientMedication antacids = activeMed("antacids");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(antacids));
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        DrugInteractionDto moderate = new DrugInteractionDto(
                "ciprofloxacin", "antacids", InteractionSeverity.MODERATE,
                "Chelation", "Reduced absorption", "Separate by 2h");
        when(interactionDatabase.findInteraction("ciprofloxacin", "antacids"))
                .thenReturn(Optional.of(moderate));

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("ciprofloxacin"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.interactions()).hasSize(1);
        // MODERATE does not trigger an alert
        verify(clinicalAlertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // checkInteraction — safe (no interaction, no allergy)
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_noInteractionNoAllergy_safeIsTrue_noAlertFired() {
        setAuth("DOCTOR");

        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of());
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("paracetamol"));

        assertThat(resp.safe()).isTrue();
        assertThat(resp.interactions()).isEmpty();
        assertThat(resp.allergyContraindications()).isEmpty();
        verify(clinicalAlertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // checkInteraction — allergy contraindication (direct substring match)
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_directAllergyMatch_safeIsFalse_allergyAlertFired() {
        setAuth("DOCTOR");

        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of());

        PatientAllergy amoxAlly = allergy("amoxicillin");
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of(amoxAlly));

        // No drug-drug interactions
        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("amoxicillin"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.allergyContraindications()).hasSize(1);
        assertThat(resp.allergyContraindications().get(0)).contains("amoxicillin");

        ArgumentCaptor<AlertType> typeCaptor = ArgumentCaptor.forClass(AlertType.class);
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), typeCaptor.capture(), eq(AlertSeverity.CRITICAL),
                anyString(), anyString(), anyString(), anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(AlertType.ALLERGY_CONTRAINDICATION);
    }

    // -------------------------------------------------------------------------
    // checkInteraction — cross-class allergy (penicillin → amoxicillin)
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_crossClassAllergyPenicillinToAmoxicillin_detected() {
        setAuth("DOCTOR");

        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of());

        PatientAllergy penicillinAllergy = allergy("penicillin");
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of(penicillinAllergy));

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("amoxicillin"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.allergyContraindications()).isNotEmpty();
        assertThat(resp.allergyContraindications().get(0)).contains("penicillin");
    }

    // -------------------------------------------------------------------------
    // checkInteraction — allergy alert type takes precedence over drug interaction type
    // -------------------------------------------------------------------------

    @Test
    void checkInteraction_bothAllergyAndInteraction_alertTypeIsAllergyContraindication() {
        setAuth("DOCTOR");

        PatientMedication warfarin = activeMed("warfarin");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(warfarin));

        PatientAllergy aspirinAllergy = allergy("aspirin");
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of(aspirinAllergy));

        DrugInteractionDto major = new DrugInteractionDto(
                "aspirin", "warfarin", InteractionSeverity.MAJOR,
                "Mechanism", "Bleeding", "Avoid");
        when(interactionDatabase.findInteraction("aspirin", "warfarin"))
                .thenReturn(Optional.of(major));

        InteractionCheckResponse resp = service.checkInteraction(PATIENT_ID,
                new InteractionCheckRequest("aspirin"));

        assertThat(resp.safe()).isFalse();
        assertThat(resp.interactions()).hasSize(1);
        assertThat(resp.allergyContraindications()).hasSize(1);

        ArgumentCaptor<AlertType> typeCaptor = ArgumentCaptor.forClass(AlertType.class);
        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), typeCaptor.capture(), eq(AlertSeverity.CRITICAL),
                anyString(), anyString(), anyString(), anyString());
        // Allergy takes precedence
        assertThat(typeCaptor.getValue()).isEqualTo(AlertType.ALLERGY_CONTRAINDICATION);
    }

    // -------------------------------------------------------------------------
    // getInteractionSummary — role access
    // -------------------------------------------------------------------------

    @Test
    void getInteractionSummary_nurseCanAccess() {
        setAuth("NURSE");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of());
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        InteractionSummaryResponse resp = service.getInteractionSummary(PATIENT_ID);
        assertThat(resp).isNotNull();
        assertThat(resp.safe()).isTrue();
    }

    @Test
    void getInteractionSummary_receptionistIsForbidden() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> service.getInteractionSummary(PATIENT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // getInteractionSummary — pairwise check
    // -------------------------------------------------------------------------

    @Test
    void getInteractionSummary_twoMeds_pairwiseCheckPerformed() {
        setAuth("DOCTOR");

        PatientMedication warfarin = activeMed("warfarin");
        PatientMedication aspirin  = activeMed("aspirin");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(warfarin, aspirin));
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        DrugInteractionDto major = new DrugInteractionDto(
                "aspirin", "warfarin", InteractionSeverity.MAJOR,
                "Mechanism", "Bleeding", "Avoid");
        when(interactionDatabase.findInteraction("warfarin", "aspirin"))
                .thenReturn(Optional.of(major));

        InteractionSummaryResponse resp = service.getInteractionSummary(PATIENT_ID);

        assertThat(resp.interactions()).hasSize(1);
        assertThat(resp.safe()).isFalse();
    }

    @Test
    void getInteractionSummary_noMeds_returnsEmptySafe() {
        setAuth("ADMIN");
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of());
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of());

        InteractionSummaryResponse resp = service.getInteractionSummary(PATIENT_ID);

        assertThat(resp.interactions()).isEmpty();
        assertThat(resp.allergyContraindications()).isEmpty();
        assertThat(resp.safe()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PatientMedication activeMed(String name) {
        PatientMedication m = new PatientMedication();
        m.setId(UUID.randomUUID());
        m.setPatientId(PATIENT_ID);
        m.setMedicationName(name);
        m.setDosage("100mg");
        m.setFrequency("OD");
        m.setRoute(MedicationRoute.ORAL);
        m.setStartDate(LocalDate.now().minusDays(10));
        m.setPrescribedBy("doctor1");
        m.setStatus(MedicationStatus.ACTIVE);
        m.setCreatedAt(OffsetDateTime.now());
        return m;
    }

    private PatientAllergy allergy(String substance) {
        PatientAllergy a = new PatientAllergy();
        a.setId(UUID.randomUUID());
        a.setPatientId(PATIENT_ID);
        a.setSubstance(substance);
        a.setType(AllergyType.DRUG);
        a.setSeverity(AllergySeverity.SEVERE);
        a.setReaction("Anaphylaxis");
        a.setActive(true);
        a.setCreatedBy("doctor1");
        a.setCreatedAt(OffsetDateTime.now());
        return a;
    }
}
