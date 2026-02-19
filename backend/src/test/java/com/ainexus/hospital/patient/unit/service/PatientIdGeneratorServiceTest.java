package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.entity.PatientIdSequence;
import com.ainexus.hospital.patient.repository.PatientIdSequenceRepository;
import com.ainexus.hospital.patient.service.PatientIdGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientIdGeneratorServiceTest {

    @Mock
    private PatientIdSequenceRepository sequenceRepository;

    @InjectMocks
    private PatientIdGeneratorService generatorService;

    private final int TEST_YEAR = 2026;

    @BeforeEach
    void setUp() {
        generatorService = new PatientIdGeneratorService(sequenceRepository);
    }

    @Test
    void firstRegistrationOfYear_generatesP2026001() {
        // First registration: no row exists for this year
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.empty());
        PatientIdSequence savedSeq = new PatientIdSequence(TEST_YEAR, 1);
        when(sequenceRepository.save(any())).thenReturn(savedSeq);

        String patientId = generatorService.generatePatientId(TEST_YEAR);

        assertThat(patientId).isEqualTo("P2026001");
        verify(sequenceRepository).findByYearForUpdate(TEST_YEAR);
    }

    @Test
    void secondRegistration_generatesP2026002() {
        PatientIdSequence existing = new PatientIdSequence(TEST_YEAR, 1);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenReturn(new PatientIdSequence(TEST_YEAR, 2));

        String patientId = generatorService.generatePatientId(TEST_YEAR);

        assertThat(patientId).isEqualTo("P2026002");
    }

    @Test
    void yearBoundaryReset_2027StartsAt001() {
        when(sequenceRepository.findByYearForUpdate(2027)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any())).thenReturn(new PatientIdSequence(2027, 1));

        String patientId = generatorService.generatePatientId(2027);

        assertThat(patientId).isEqualTo("P2027001");
    }

    @Test
    void sequenceAt999_generatesP2026999() {
        PatientIdSequence existing = new PatientIdSequence(TEST_YEAR, 998);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenReturn(new PatientIdSequence(TEST_YEAR, 999));

        String patientId = generatorService.generatePatientId(TEST_YEAR);

        assertThat(patientId).isEqualTo("P2026999");
    }

    @Test
    void sequenceExceeds999_expandsToFourDigits() {
        PatientIdSequence existing = new PatientIdSequence(TEST_YEAR, 999);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenReturn(new PatientIdSequence(TEST_YEAR, 1000));

        String patientId = generatorService.generatePatientId(TEST_YEAR);

        // No zero-padding beyond 999
        assertThat(patientId).isEqualTo("P20261000");
    }

    @Test
    void sequenceAt12_generatesZeroPaddedP2026012() {
        PatientIdSequence existing = new PatientIdSequence(TEST_YEAR, 11);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenReturn(new PatientIdSequence(TEST_YEAR, 12));

        String patientId = generatorService.generatePatientId(TEST_YEAR);

        assertThat(patientId).isEqualTo("P2026012");
    }
}
