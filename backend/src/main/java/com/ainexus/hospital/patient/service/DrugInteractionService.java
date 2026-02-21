package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.DrugInteractionDto;
import com.ainexus.hospital.patient.dto.InteractionCheckRequest;
import com.ainexus.hospital.patient.dto.InteractionCheckResponse;
import com.ainexus.hospital.patient.dto.InteractionSummaryResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.intelligence.DrugInteractionDatabase;
import com.ainexus.hospital.patient.repository.AllergyRepository;
import com.ainexus.hospital.patient.repository.MedicationRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Drug interaction and allergy contraindication checker.
 *
 * <p>Uses a static in-memory {@link DrugInteractionDatabase} for O(1) lookups.
 * Allergy contraindication matching uses:
 * <ol>
 *   <li>Bidirectional case-insensitive substring match between drug name and allergy substance.</li>
 *   <li>A predefined cross-class reactivity map (e.g. penicillin allergy → amoxicillin).</li>
 * </ol>
 */
@Service
public class DrugInteractionService {

    /** Known cross-class drug allergies: allergy substance key → set of drugs it flags. */
    private static final Map<String, Set<String>> CROSS_CLASS_ALLERGY_MAP;

    static {
        CROSS_CLASS_ALLERGY_MAP = new HashMap<>();
        CROSS_CLASS_ALLERGY_MAP.put("penicillin", Set.of(
                "amoxicillin", "ampicillin", "amoxicillin/clavulanate",
                "piperacillin", "flucloxacillin", "dicloxacillin",
                "phenoxymethylpenicillin", "benzylpenicillin"));
        CROSS_CLASS_ALLERGY_MAP.put("sulfa", Set.of(
                "sulfamethoxazole", "sulfadiazine", "sulfasalazine",
                "trimethoprim/sulfamethoxazole", "co-trimoxazole"));
        CROSS_CLASS_ALLERGY_MAP.put("cephalosporin", Set.of(
                "cefalexin", "cefuroxime", "ceftriaxone", "cefotaxime",
                "ceftazidime", "cefixime"));
        CROSS_CLASS_ALLERGY_MAP.put("codeine", Set.of(
                "morphine", "tramadol", "oxycodone", "hydrocodone", "fentanyl", "buprenorphine"));
    }

    private final DrugInteractionDatabase interactionDatabase;
    private final MedicationRepository medicationRepository;
    private final AllergyRepository allergyRepository;
    private final ClinicalAlertService clinicalAlertService;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;

    public DrugInteractionService(DrugInteractionDatabase interactionDatabase,
                                  MedicationRepository medicationRepository,
                                  AllergyRepository allergyRepository,
                                  ClinicalAlertService clinicalAlertService,
                                  EmrAuditService emrAuditService,
                                  RoleGuard roleGuard) {
        this.interactionDatabase = interactionDatabase;
        this.medicationRepository = medicationRepository;
        this.allergyRepository = allergyRepository;
        this.clinicalAlertService = clinicalAlertService;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
    }

    // -------------------------------------------------------------------------
    // Interaction check for a specific drug
    // -------------------------------------------------------------------------

    @Transactional
    public InteractionCheckResponse checkInteraction(String patientId, InteractionCheckRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        String drugName = request.drugName();
        String normDrug = normalise(drugName);

        List<PatientMedication> activeMeds = medicationRepository
                .findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE);

        List<PatientAllergy> activeAllergies = allergyRepository
                .findByPatientIdAndActiveTrue(patientId);

        // Drug-drug interactions
        List<DrugInteractionDto> interactions = activeMeds.stream()
                .flatMap(med -> interactionDatabase
                        .findInteraction(normDrug, normalise(med.getMedicationName()))
                        .stream())
                .toList();

        // Allergy contraindications
        List<String> allergyContraindications = activeAllergies.stream()
                .filter(a -> isAllergyContraindicated(normDrug, normalise(a.getSubstance())))
                .map(a -> "Allergy to " + a.getSubstance() + " (cross-reaction with " + drugName + ")")
                .toList();

        // safe = no interactions at all AND no allergy contraindications
        boolean safe = interactions.isEmpty() && allergyContraindications.isEmpty();

        // Auto-create clinical alert only for MAJOR/CONTRAINDICATED interactions or allergy matches
        boolean alertNeeded = interactions.stream().anyMatch(i -> i.severity().triggersAlert())
                || !allergyContraindications.isEmpty();

        if (alertNeeded) {
            String alertDescription = buildAlertDescription(drugName, interactions, allergyContraindications);
            AlertType alertType = !allergyContraindications.isEmpty()
                    ? AlertType.ALLERGY_CONTRAINDICATION
                    : AlertType.DRUG_INTERACTION;
            clinicalAlertService.createAlert(
                    patientId, alertType, AlertSeverity.CRITICAL,
                    "Drug Safety Alert: " + drugName,
                    alertDescription,
                    "DrugInteractionService",
                    drugName);
        }

        emrAuditService.writeAuditLog(
                "DRUG_INTERACTION_CHECK", patientId, patientId,
                "CHECK", ctx.getUsername(),
                "drug=" + drugName + " safe=" + safe);

        return new InteractionCheckResponse(
                drugName, interactions, allergyContraindications, safe, OffsetDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Summary of all interaction risks across active medications
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InteractionSummaryResponse getInteractionSummary(String patientId) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");

        List<PatientMedication> activeMeds = medicationRepository
                .findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE);

        List<PatientAllergy> activeAllergies = allergyRepository
                .findByPatientIdAndActiveTrue(patientId);

        // Cross-check all pairs
        Set<DrugInteractionDto> interactions = new LinkedHashSet<>();
        for (int i = 0; i < activeMeds.size(); i++) {
            for (int j = i + 1; j < activeMeds.size(); j++) {
                interactionDatabase
                        .findInteraction(
                                normalise(activeMeds.get(i).getMedicationName()),
                                normalise(activeMeds.get(j).getMedicationName()))
                        .ifPresent(interactions::add);
            }
        }

        // Allergy contraindications across all active meds
        List<String> allergyContraindications = new ArrayList<>();
        for (PatientMedication med : activeMeds) {
            for (PatientAllergy allergy : activeAllergies) {
                if (isAllergyContraindicated(normalise(med.getMedicationName()), normalise(allergy.getSubstance()))) {
                    allergyContraindications.add(
                            "Patient allergic to " + allergy.getSubstance()
                                    + " — cross-reaction risk with " + med.getMedicationName());
                }
            }
        }

        boolean safe = interactions.stream().noneMatch(i -> i.severity().triggersAlert())
                && allergyContraindications.isEmpty();

        return new InteractionSummaryResponse(
                patientId,
                new ArrayList<>(interactions),
                allergyContraindications,
                safe,
                OffsetDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Determines if a drug triggers a contraindication given a patient's known allergy.
     * Uses both bidirectional substring match and cross-class allergy map.
     */
    private boolean isAllergyContraindicated(String normDrug, String normAllergy) {
        // Direct substring match (bidirectional)
        if (normDrug.contains(normAllergy) || normAllergy.contains(normDrug)) {
            return true;
        }
        // Cross-class allergy map: allergic to X → drugs in the class are flagged
        for (Map.Entry<String, Set<String>> entry : CROSS_CLASS_ALLERGY_MAP.entrySet()) {
            if (normAllergy.contains(entry.getKey()) && entry.getValue().contains(normDrug)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllergyContraindication(List<DrugInteractionDto> interactions) {
        return interactions.stream().anyMatch(i -> i.severity() == InteractionSeverity.CONTRAINDICATED);
    }

    private String buildAlertDescription(String drugName, List<DrugInteractionDto> interactions,
                                          List<String> allergyContraindications) {
        StringBuilder sb = new StringBuilder("Drug safety check for ").append(drugName).append(": ");
        if (!interactions.isEmpty()) {
            long majorCount = interactions.stream()
                    .filter(i -> i.severity().triggersAlert()).count();
            sb.append(majorCount).append(" major/contraindicated interaction(s) detected. ");
        }
        if (!allergyContraindications.isEmpty()) {
            sb.append(allergyContraindications.size()).append(" allergy contraindication(s) detected.");
        }
        return sb.toString();
    }

    private String normalise(String name) {
        return name == null ? "" : name.strip().toLowerCase();
    }
}
