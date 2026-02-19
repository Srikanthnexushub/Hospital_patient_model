package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.PatientAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientAuditLogRepository extends JpaRepository<PatientAuditLog, Long> {

    /** Retrieve all audit entries for a given patient, ordered by timestamp. */
    List<PatientAuditLog> findByPatientIdOrderByTimestampDesc(String patientId);

    /** Retrieve all audit entries performed by a specific user. */
    List<PatientAuditLog> findByPerformedByOrderByTimestampDesc(String performedBy);

    // NOTE: No delete or update methods are exposed — audit log is append-only.
    // deleteById() and delete() from JpaRepository are intentionally NOT used.
}
