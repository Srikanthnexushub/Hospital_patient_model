package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.entity.AppointmentIdSequence;
import com.ainexus.hospital.patient.repository.AppointmentIdSequenceRepository;
import com.ainexus.hospital.patient.service.AppointmentIdGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentIdGeneratorServiceTest {

    @Mock
    private AppointmentIdSequenceRepository sequenceRepository;

    private AppointmentIdGeneratorService generatorService;

    private static final int TEST_YEAR = 2026;

    @BeforeEach
    void setUp() {
        generatorService = new AppointmentIdGeneratorService(sequenceRepository);
    }

    // ── Test 1: First appointment of the year — no row exists yet ─────────────

    @Test
    void generateAppointmentId_firstOfYear_generatesAPT20260001() {
        // No existing row for this year → generates sequence 1 → "APT20260001"
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId(TEST_YEAR);

        assertThat(result).isEqualTo("APT20260001");
        verify(sequenceRepository).findByYearForUpdate(TEST_YEAR);
        verify(sequenceRepository).save(any(AppointmentIdSequence.class));
    }

    // ── Test 2: Existing row with lastSequence=5 → next is 6 ─────────────────

    @Test
    void generateAppointmentId_existing_incrementsSequence() {
        AppointmentIdSequence existingSeq = new AppointmentIdSequence(TEST_YEAR, 5);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR))
                .thenReturn(Optional.of(existingSeq));
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId(TEST_YEAR);

        assertThat(result).isEqualTo("APT20260006");
        assertThat(existingSeq.getLastSequence()).isEqualTo(6);
    }

    // ── Test 3: lastSequence=9999 → next is 10000 (no padding past 9999) ─────

    @Test
    void generateAppointmentId_past9999_noZeroPadding() {
        AppointmentIdSequence existingSeq = new AppointmentIdSequence(TEST_YEAR, 9999);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR))
                .thenReturn(Optional.of(existingSeq));
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId(TEST_YEAR);

        // Beyond 9999: no zero-padding, just the raw number
        assertThat(result).isEqualTo("APT202610000");
        assertThat(existingSeq.getLastSequence()).isEqualTo(10000);
    }

    // ── Test 4: No-arg overload delegates to current year ────────────────────

    @Test
    void generateAppointmentId_noArgUsesCurrentYear() {
        int currentYear = LocalDate.now().getYear();

        when(sequenceRepository.findByYearForUpdate(currentYear)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId();

        // Must start with "APT" + current year and end with "0001" (first of year)
        assertThat(result).startsWith("APT" + currentYear);
        assertThat(result).endsWith("0001");

        verify(sequenceRepository).findByYearForUpdate(currentYear);
    }

    // ── Boundary: sequence at 999 → 1000 (zero-padded, 4 digits) ────────────

    @Test
    void generateAppointmentId_sequence999_returnsFourDigits() {
        AppointmentIdSequence existingSeq = new AppointmentIdSequence(TEST_YEAR, 998);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR))
                .thenReturn(Optional.of(existingSeq));
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId(TEST_YEAR);

        assertThat(result).isEqualTo("APT20260999");
    }

    // ── Boundary: 4-digit zero-padded at exactly 1000 ─────────────────────

    @Test
    void generateAppointmentId_sequence1000_stillFourDigits() {
        AppointmentIdSequence existingSeq = new AppointmentIdSequence(TEST_YEAR, 999);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR))
                .thenReturn(Optional.of(existingSeq));
        when(sequenceRepository.save(any(AppointmentIdSequence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String result = generatorService.generateAppointmentId(TEST_YEAR);

        // 1000 is exactly 4 digits — format "%04d" still works (produces "1000")
        assertThat(result).isEqualTo("APT20261000");
    }
}
