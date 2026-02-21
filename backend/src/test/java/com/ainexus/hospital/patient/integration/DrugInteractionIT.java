package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("clinical-intelligence")
@DisplayName("US3 — Drug Interaction & Allergy Contraindication Checker")
class DrugInteractionIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setupPatient() {
        patientId = seedPatient("Drug", "Checker");
    }

    // ── POST /interaction-check — role guards ─────────────────────────────────

    @Test
    @DisplayName("NURSE cannot call interaction-check — 403")
    void interactionCheck_nurse_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "aspirin"), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RECEPTIONIST cannot call interaction-check — 403")
    void interactionCheck_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "aspirin"), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── MAJOR drug-drug interaction ───────────────────────────────────────────

    @Test
    @DisplayName("Aspirin + Warfarin (active med) → MAJOR interaction, safe=false, alert created")
    void interactionCheck_aspirinWarfarin_majorInteraction() {
        seedActiveMedication(patientId, "warfarin");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "aspirin"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(false);

        var interactions = (java.util.List<?>) resp.getBody().get("interactions");
        assertThat(interactions).isNotEmpty();

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'DRUG_INTERACTION'",
                Integer.class, patientId);
        assertThat(alertCount).isGreaterThan(0);
    }

    // ── CONTRAINDICATED drug ──────────────────────────────────────────────────

    @Test
    @DisplayName("SSRI + MAOI (active med) → CONTRAINDICATED, safe=false, alert created")
    void interactionCheck_ssriMaoi_contraindicated() {
        seedActiveMedication(patientId, "maoi");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "ssri"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(false);

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'DRUG_INTERACTION'",
                Integer.class, patientId);
        assertThat(alertCount).isGreaterThan(0);
    }

    // ── Allergy contraindication — direct match ───────────────────────────────

    @Test
    @DisplayName("Amoxicillin with amoxicillin allergy → allergy contraindication, ALLERGY_CONTRAINDICATION alert")
    void interactionCheck_directAllergyMatch_contraindicated() {
        seedActiveAllergy(patientId, "amoxicillin");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "amoxicillin"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(false);

        var allergyContras = (java.util.List<?>) resp.getBody().get("allergyContraindications");
        assertThat(allergyContras).isNotEmpty();

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'ALLERGY_CONTRAINDICATION'",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(1);
    }

    // ── Cross-class allergy (penicillin → amoxicillin) ────────────────────────

    @Test
    @DisplayName("Amoxicillin prescribed to penicillin-allergic patient → cross-class allergy flag")
    void interactionCheck_crossClassAllergy_penicillinAmoxicillin() {
        seedActiveAllergy(patientId, "penicillin");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "amoxicillin"), authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(false);

        var allergyContras = (java.util.List<?>) resp.getBody().get("allergyContraindications");
        assertThat(allergyContras).isNotEmpty();
    }

    // ── Safe combo ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unknown drug with no interactions/allergies → safe=true, no alert")
    void interactionCheck_safeDrug_returnsTrue() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("drugName", "paracetamol"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(true);
        assertThat((java.util.List<?>) resp.getBody().get("interactions")).isEmpty();
        assertThat((java.util.List<?>) resp.getBody().get("allergyContraindications")).isEmpty();

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ?",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(0);
    }

    // ── GET /interaction-summary ──────────────────────────────────────────────

    @Test
    @DisplayName("NURSE can access interaction-summary — 200")
    void interactionSummary_nurse_returns200() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(true);
    }

    @Test
    @DisplayName("Summary with known interacting meds → interaction found")
    void interactionSummary_interactingMeds_returnsInteraction() {
        seedActiveMedication(patientId, "warfarin");
        seedActiveMedication(patientId, "aspirin");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("safe")).isEqualTo(false);

        var interactions = (java.util.List<?>) resp.getBody().get("interactions");
        assertThat(interactions).isNotEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void seedActiveMedication(String pid, String drugName) {
        jdbcTemplate.update("""
                INSERT INTO patient_medications
                  (id, patient_id, medication_name, dosage, frequency, route,
                   start_date, prescribed_by, status, created_at)
                VALUES (gen_random_uuid(), ?, ?, '100mg', 'OD', 'ORAL',
                        CURRENT_DATE - 10, 'doctor1', 'ACTIVE', NOW())
                """, pid, drugName);
    }

    private void seedActiveAllergy(String pid, String substance) {
        jdbcTemplate.update("""
                INSERT INTO patient_allergies
                  (id, patient_id, substance, type, severity, reaction,
                   active, created_by, created_at)
                VALUES (gen_random_uuid(), ?, ?, 'DRUG', 'SEVERE', 'Anaphylaxis',
                        TRUE, 'doctor1', NOW())
                """, pid, substance);
    }
}
