package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

    List<AuthAuditLog> findByActorUserIdOrderByTimestampDesc(String actorUserId);
}
