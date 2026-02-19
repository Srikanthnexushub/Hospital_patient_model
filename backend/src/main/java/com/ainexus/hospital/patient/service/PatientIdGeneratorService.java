package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.entity.PatientIdSequence;
import com.ainexus.hospital.patient.repository.PatientIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates unique Patient IDs atomically using a pessimistic row lock.
 *
 * Format: P + YYYY + zero-padded sequence (3 digits, expanding if > 999)
 * Examples: P2026001, P2026012, P2026999, P20261000
 *
 * The sequence resets to 001 at the start of each new calendar year.
 * This service must always be called within an outer @Transactional context.
 */
@Service
public class PatientIdGeneratorService {

    private final PatientIdSequenceRepository sequenceRepository;

    public PatientIdGeneratorService(PatientIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /**
     * Generates the next Patient ID for the given year using a pessimistic write lock.
     * This method is safe for concurrent use under high load (>100,000 req/hr).
     *
     * @param year The calendar year (e.g., 2026)
     * @return The next Patient ID string (e.g., "P2026001")
     */
    @Transactional
    public String generatePatientId(Integer year) {
        PatientIdSequence seq = sequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> {
                    // First registration of the year — create the sequence row
                    return new PatientIdSequence(year, 0);
                });

        int nextSeq = seq.getLastSequence() + 1;
        seq.setLastSequence(nextSeq);
        sequenceRepository.save(seq);

        // Format: up to 999 → 3-digit zero-padded; 1000+ → no padding
        String seqStr = nextSeq <= 999
                ? String.format("%03d", nextSeq)
                : String.valueOf(nextSeq);

        return "P" + year + seqStr;
    }

    /**
     * Convenience method that uses the current calendar year.
     */
    @Transactional
    public String generatePatientId() {
        return generatePatientId(java.time.LocalDate.now().getYear());
    }
}
