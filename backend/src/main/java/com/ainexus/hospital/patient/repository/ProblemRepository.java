package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.PatientProblem;
import com.ainexus.hospital.patient.entity.ProblemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<PatientProblem, UUID> {

    List<PatientProblem> findByPatientIdAndStatus(String patientId, ProblemStatus status);

    List<PatientProblem> findByPatientId(String patientId);

    Optional<PatientProblem> findByIdAndPatientId(UUID id, String patientId);
}
