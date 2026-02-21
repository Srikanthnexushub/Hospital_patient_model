package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.LabOrder;
import com.ainexus.hospital.patient.entity.LabOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {

    /** All lab orders for a patient, most recent first. */
    Page<LabOrder> findByPatientIdOrderByOrderedAtDesc(String patientId, Pageable pageable);

    /** Lab orders for a patient filtered by status, most recent first. */
    Page<LabOrder> findByPatientIdAndStatusOrderByOrderedAtDesc(
            String patientId, LabOrderStatus status, Pageable pageable);

    /** Load a specific order scoped to a patient (prevents cross-patient access). */
    Optional<LabOrder> findByIdAndPatientId(UUID id, String patientId);
}
