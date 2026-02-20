package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.entity.InvoiceIdSequence;
import com.ainexus.hospital.patient.repository.InvoiceIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Generates unique Invoice IDs atomically using a pessimistic row lock.
 *
 * Format: INV + YYYY + zero-padded sequence (5 digits, expanding if > 99999)
 * Examples: INV2026000001, INV2026000099, INV202699999, INV202610000
 *
 * The sequence resets to 00001 at the start of each new calendar year.
 * Must always be called within an outer @Transactional context.
 */
@Service
public class InvoiceIdGeneratorService {

    private final InvoiceIdSequenceRepository sequenceRepository;

    public InvoiceIdGeneratorService(InvoiceIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String generateInvoiceId(Integer year) {
        InvoiceIdSequence seq = sequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> new InvoiceIdSequence(year, 0));

        int nextSeq = seq.getLastSequence() + 1;
        seq.setLastSequence(nextSeq);
        sequenceRepository.save(seq);

        String seqStr = nextSeq <= 999999
                ? String.format("%06d", nextSeq)
                : String.valueOf(nextSeq);

        return "INV" + year + seqStr;
    }

    @Transactional
    public String generateInvoiceId() {
        return generateInvoiceId(LocalDate.now().getYear());
    }
}
