package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.entity.StaffIdSequence;
import com.ainexus.hospital.patient.repository.StaffIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Generates unique Staff User IDs atomically using a pessimistic row lock.
 *
 * Format: U + YYYY + zero-padded sequence (3 digits minimum, expanding past 999)
 * Examples: U2026001, U2026012, U2026999, U20261000
 *
 * Mirrors PatientIdGeneratorService exactly — same locking pattern, prefix "U" not "P".
 */
@Service
public class StaffIdGeneratorService {

    private final StaffIdSequenceRepository sequenceRepository;

    public StaffIdGeneratorService(StaffIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /**
     * Generates the next Staff User ID for the given year using a pessimistic write lock.
     *
     * @param year The calendar year (e.g., 2026)
     * @return The next Staff User ID string (e.g., "U2026001")
     */
    @Transactional
    public String generateStaffId(Integer year) {
        StaffIdSequence seq = sequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> new StaffIdSequence(year, 0));

        int nextSeq = seq.getLastSequence() + 1;
        seq.setLastSequence(nextSeq);
        sequenceRepository.save(seq);

        String seqStr = nextSeq <= 999
                ? String.format("%03d", nextSeq)
                : String.valueOf(nextSeq);

        return "U" + year + seqStr;
    }

    /**
     * Convenience method using the current calendar year.
     */
    @Transactional
    public String generateStaffId() {
        return generateStaffId(LocalDate.now().getYear());
    }
}
