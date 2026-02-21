package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.entity.AppointmentIdSequence;
import com.ainexus.hospital.patient.repository.AppointmentIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates unique Appointment IDs atomically using a pessimistic row lock.
 *
 * Format: APT + YYYY + zero-padded sequence (4 digits, expanding if > 9999)
 * Examples: APT20260001, APT20260099, APT20269999, APT202610000
 *
 * The sequence resets to 0001 at the start of each new calendar year.
 * This service must always be called within an outer @Transactional context.
 */
@Service
public class AppointmentIdGeneratorService {

    private final AppointmentIdSequenceRepository sequenceRepository;

    public AppointmentIdGeneratorService(AppointmentIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String generateAppointmentId(Integer year) {
        AppointmentIdSequence seq = sequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> new AppointmentIdSequence(year, 0));

        int nextSeq = seq.getLastSequence() + 1;
        seq.setLastSequence(nextSeq);
        sequenceRepository.save(seq);

        String seqStr = nextSeq <= 9999
                ? String.format("%04d", nextSeq)
                : String.valueOf(nextSeq);

        return "APT" + year + seqStr;
    }

    @Transactional
    public String generateAppointmentId() {
        return generateAppointmentId(java.time.LocalDate.now().getYear());
    }
}
