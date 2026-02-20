package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.PatientVitals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VitalsRepository extends JpaRepository<PatientVitals, Long> {

    /** Used for upsert: find existing record for this appointment (at most one). */
    Optional<PatientVitals> findByAppointmentId(String appointmentId);

    /** Paginated vitals history for a patient, most-recent first. */
    Page<PatientVitals> findByPatientIdOrderByRecordedAtDesc(String patientId, Pageable pageable);

    /** Used by MedicalSummaryService to get the 5 most recent vitals. */
    List<PatientVitals> findTop5ByPatientIdOrderByRecordedAtDesc(String patientId);
}
