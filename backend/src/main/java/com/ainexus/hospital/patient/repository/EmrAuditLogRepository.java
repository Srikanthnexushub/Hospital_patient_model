package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.EmrAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmrAuditLogRepository extends JpaRepository<EmrAuditLog, Long> {
}
