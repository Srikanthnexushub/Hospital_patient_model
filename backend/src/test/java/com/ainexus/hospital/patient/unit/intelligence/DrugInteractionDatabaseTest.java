package com.ainexus.hospital.patient.unit.intelligence;

import com.ainexus.hospital.patient.dto.DrugInteractionDto;
import com.ainexus.hospital.patient.entity.InteractionSeverity;
import com.ainexus.hospital.patient.intelligence.DrugInteractionDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DrugInteractionDatabaseTest {

    private DrugInteractionDatabase db;

    @BeforeEach
    void setUp() {
        db = new DrugInteractionDatabase();
    }

    // -------------------------------------------------------------------------
    // Size / completeness
    // -------------------------------------------------------------------------

    @Test
    void database_containsAtLeast40Entries() {
        assertThat(db.size()).isGreaterThanOrEqualTo(40);
    }

    // -------------------------------------------------------------------------
    // findInteraction — exact pair lookup
    // -------------------------------------------------------------------------

    @Test
    void findInteraction_warfarinAspirin_returnsMajorInteraction() {
        Optional<DrugInteractionDto> result = db.findInteraction("warfarin", "aspirin");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.MAJOR);
    }

    @Test
    void findInteraction_aspirinWarfarin_bidirectional() {
        // reversed argument order must still find the entry
        Optional<DrugInteractionDto> fwd = db.findInteraction("warfarin", "aspirin");
        Optional<DrugInteractionDto> rev = db.findInteraction("aspirin", "warfarin");
        assertThat(fwd).isPresent();
        assertThat(rev).isPresent();
        assertThat(fwd.get().severity()).isEqualTo(rev.get().severity());
    }

    @Test
    void findInteraction_sstriMaoi_returnsContraindicated() {
        Optional<DrugInteractionDto> result = db.findInteraction("ssri", "maoi");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.CONTRAINDICATED);
    }

    @Test
    void findInteraction_sildenafil_nitrate_returnsContraindicated() {
        Optional<DrugInteractionDto> result = db.findInteraction("sildenafil", "nitrate");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.CONTRAINDICATED);
    }

    @Test
    void findInteraction_digoxinAmiodarone_returnsMajor() {
        Optional<DrugInteractionDto> result = db.findInteraction("digoxin", "amiodarone");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.MAJOR);
    }

    @Test
    void findInteraction_metforminContrast_returnsMajor() {
        Optional<DrugInteractionDto> result = db.findInteraction("metformin", "contrast");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.MAJOR);
    }

    @Test
    void findInteraction_ciprofloxacinAntacids_returnsModerate() {
        Optional<DrugInteractionDto> result = db.findInteraction("ciprofloxacin", "antacids");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.MODERATE);
    }

    @Test
    void findInteraction_unknownPair_returnsEmpty() {
        Optional<DrugInteractionDto> result = db.findInteraction("penicillin", "paracetamol");
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Case-insensitive lookup
    // -------------------------------------------------------------------------

    @Test
    void findInteraction_caseInsensitive_warfarinUppercase() {
        Optional<DrugInteractionDto> result = db.findInteraction("WARFARIN", "ASPIRIN");
        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(InteractionSeverity.MAJOR);
    }

    @Test
    void findInteraction_caseInsensitive_mixedCase() {
        Optional<DrugInteractionDto> result = db.findInteraction("Digoxin", "Amiodarone");
        assertThat(result).isPresent();
    }

    // -------------------------------------------------------------------------
    // findInteractionsFor — list lookup
    // -------------------------------------------------------------------------

    @Test
    void findInteractionsFor_warfarin_returnsMultipleEntries() {
        List<DrugInteractionDto> results = db.findInteractionsFor("warfarin");
        // warfarin interacts with: ibuprofen, naproxen, aspirin, clopidogrel,
        //   amiodarone, fluconazole, metronidazole, rifampicin
        assertThat(results).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void findInteractionsFor_warfarin_includesAspirinEntry() {
        List<DrugInteractionDto> results = db.findInteractionsFor("warfarin");
        assertThat(results).anyMatch(d ->
                (d.drug1().equals("warfarin") && d.drug2().equals("aspirin"))
                || (d.drug1().equals("aspirin") && d.drug2().equals("warfarin")));
    }

    @Test
    void findInteractionsFor_aspirin_includesWarfarinEntry_bidirectional() {
        // aspirin lookup must also return the warfarin+aspirin entry
        List<DrugInteractionDto> results = db.findInteractionsFor("aspirin");
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(d ->
                d.drug1().equals("warfarin") || d.drug2().equals("warfarin"));
    }

    @Test
    void findInteractionsFor_caseInsensitive_WARFARIN() {
        List<DrugInteractionDto> upper = db.findInteractionsFor("WARFARIN");
        List<DrugInteractionDto> lower = db.findInteractionsFor("warfarin");
        assertThat(upper).hasSameSizeAs(lower);
    }

    @Test
    void findInteractionsFor_unknownDrug_returnsEmptyList() {
        List<DrugInteractionDto> results = db.findInteractionsFor("unknowndrugxyz");
        assertThat(results).isEmpty();
    }

    // -------------------------------------------------------------------------
    // triggersAlert helper on InteractionSeverity
    // -------------------------------------------------------------------------

    @Test
    void triggersAlert_majorAndContraindicated_returnTrue() {
        assertThat(InteractionSeverity.MAJOR.triggersAlert()).isTrue();
        assertThat(InteractionSeverity.CONTRAINDICATED.triggersAlert()).isTrue();
    }

    @Test
    void triggersAlert_minorAndModerate_returnFalse() {
        assertThat(InteractionSeverity.MINOR.triggersAlert()).isFalse();
        assertThat(InteractionSeverity.MODERATE.triggersAlert()).isFalse();
    }

    // -------------------------------------------------------------------------
    // DTO field completeness
    // -------------------------------------------------------------------------

    @Test
    void findInteraction_dto_hasAllFields() {
        DrugInteractionDto dto = db.findInteraction("warfarin", "aspirin").orElseThrow();
        assertThat(dto.drug1()).isNotBlank();
        assertThat(dto.drug2()).isNotBlank();
        assertThat(dto.severity()).isNotNull();
        assertThat(dto.mechanism()).isNotBlank();
        assertThat(dto.clinicalEffect()).isNotBlank();
        assertThat(dto.recommendation()).isNotBlank();
    }
}
