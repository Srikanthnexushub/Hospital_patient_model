package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.entity.InvoiceIdSequence;
import com.ainexus.hospital.patient.repository.InvoiceIdSequenceRepository;
import com.ainexus.hospital.patient.service.InvoiceIdGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceIdGeneratorServiceTest {

    @Mock
    private InvoiceIdSequenceRepository sequenceRepository;

    private InvoiceIdGeneratorService generatorService;

    private static final int TEST_YEAR = 2026;

    @BeforeEach
    void setUp() {
        generatorService = new InvoiceIdGeneratorService(sequenceRepository);
    }

    @Test
    void firstInvoiceOfYear_generatesINV2026000001() {
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV2026000001");
        verify(sequenceRepository).findByYearForUpdate(TEST_YEAR);
        verify(sequenceRepository).save(any());
    }

    @Test
    void secondInvoice_incrementsSequence() {
        InvoiceIdSequence existing = new InvoiceIdSequence(TEST_YEAR, 1);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV2026000002");
    }

    @Test
    void yearBoundaryReset_2027StartsAt000001() {
        when(sequenceRepository.findByYearForUpdate(2027)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(2027);

        assertThat(invoiceId).isEqualTo("INV2027000001");
    }

    @Test
    void sequenceAt99999_generates6DigitPaddedId() {
        InvoiceIdSequence existing = new InvoiceIdSequence(TEST_YEAR, 99998);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV2026099999");
    }

    @Test
    void sequenceAt999999_generates6DigitPaddedId() {
        InvoiceIdSequence existing = new InvoiceIdSequence(TEST_YEAR, 999998);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV2026999999");
    }

    @Test
    void sequenceExceeds999999_expandsBeyond6Digits() {
        InvoiceIdSequence existing = new InvoiceIdSequence(TEST_YEAR, 999999);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV20261000000");
    }

    @Test
    void smallSequenceNumber_isZeroPaddedTo5Digits() {
        InvoiceIdSequence existing = new InvoiceIdSequence(TEST_YEAR, 11);
        when(sequenceRepository.findByYearForUpdate(TEST_YEAR)).thenReturn(Optional.of(existing));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String invoiceId = generatorService.generateInvoiceId(TEST_YEAR);

        assertThat(invoiceId).isEqualTo("INV2026000012");
    }
}
