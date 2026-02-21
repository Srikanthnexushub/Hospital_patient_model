package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AllergyRepository extends JpaRepository<PatientAllergy, UUID> {

    List<PatientAllergy> findByPatientIdAndActiveTrue(String patientId);

    List<PatientAllergy> findByPatientId(String patientId);

    Optional<PatientAllergy> findByIdAndPatientId(UUID id, String patientId);
}
