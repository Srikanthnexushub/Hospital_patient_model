package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.LabResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    /** One-to-one: find the result for a given order. */
    Optional<LabResult> findByOrderId(UUID orderId);

    /** Paginated result history for a patient, most recent first. */
    Page<LabResult> findByPatientIdOrderByResultedAtDesc(String patientId, Pageable pageable);
}
