package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.MedicationStatus;
import com.ainexus.hospital.patient.entity.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<PatientMedication, UUID> {

    List<PatientMedication> findByPatientIdAndStatus(String patientId, MedicationStatus status);

    List<PatientMedication> findByPatientId(String patientId);

    Optional<PatientMedication> findByIdAndPatientId(UUID id, String patientId);
}
