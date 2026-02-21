package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.AppointmentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentAuditLogRepository extends JpaRepository<AppointmentAuditLog, Long> {

    List<AppointmentAuditLog> findByAppointmentIdOrderByPerformedAtDesc(String appointmentId);
}
