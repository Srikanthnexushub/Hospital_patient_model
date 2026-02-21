package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.AlertSeverity;
import com.ainexus.hospital.patient.entity.AlertStatus;
import com.ainexus.hospital.patient.entity.AlertType;
import com.ainexus.hospital.patient.entity.ClinicalAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalAlertRepository extends JpaRepository<ClinicalAlert, UUID> {

    /** Deduplication check: is there already an ACTIVE alert of this type for this patient? */
    Optional<ClinicalAlert> findByPatientIdAndAlertTypeAndStatus(
            String patientId, AlertType alertType, AlertStatus status);

    /** Per-patient alert feed with optional status/severity filters. */
    @Query("""
            SELECT a FROM ClinicalAlert a
            WHERE a.patientId = :patientId
              AND (:status IS NULL OR a.status = :status)
              AND (:severity IS NULL OR a.severity = :severity)
            ORDER BY a.createdAt DESC
            """)
    Page<ClinicalAlert> findByPatientIdFiltered(
            @Param("patientId") String patientId,
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            Pageable pageable);

    /** Global alert feed — ADMIN/NURSE see all; DOCTOR is filtered by patient set from appointments. */
    @Query("""
            SELECT a FROM ClinicalAlert a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:severity IS NULL OR a.severity = :severity)
              AND (:doctorId IS NULL OR a.patientId IN (
                  SELECT DISTINCT ap.patientId FROM Appointment ap
                  WHERE ap.doctorId = :doctorId
              ))
            ORDER BY a.createdAt DESC
            """)
    Page<ClinicalAlert> findGlobalFiltered(
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            @Param("doctorId") String doctorId,
            Pageable pageable);

    /** Stats: count active alerts by severity and type. */
    long countByStatus(AlertStatus status);

    long countBySeverityAndStatus(AlertSeverity severity, AlertStatus status);

    @Query("""
            SELECT a.alertType AS alertType, COUNT(a) AS count
            FROM ClinicalAlert a
            WHERE a.status = :status
            GROUP BY a.alertType
            """)
    List<AlertTypeCount> countByAlertTypeGrouped(@Param("status") AlertStatus status);

    /** Module 6 Stats — count patients with an active alert of a specific type. */
    @Query("""
            SELECT COUNT(DISTINCT a.patientId) FROM ClinicalAlert a
            WHERE a.alertType = :alertType AND a.status = :status
            """)
    long countDistinctPatientsByAlertTypeAndStatus(
            @Param("alertType") AlertType alertType,
            @Param("status") AlertStatus status);

    /** Count patients with at least one active CRITICAL alert. */
    @Query("""
            SELECT COUNT(DISTINCT a.patientId) FROM ClinicalAlert a
            WHERE a.severity = 'CRITICAL' AND a.status = 'ACTIVE'
            """)
    long countDistinctPatientsWithCriticalAlerts();

    /** Dashboard: active alert counts per patient. */
    @Query("""
            SELECT a.patientId AS patientId,
                   SUM(CASE WHEN a.severity = 'CRITICAL' THEN 1 ELSE 0 END) AS criticalCount,
                   SUM(CASE WHEN a.severity = 'WARNING' THEN 1 ELSE 0 END) AS warningCount
            FROM ClinicalAlert a
            WHERE a.status = 'ACTIVE'
              AND (:doctorId IS NULL OR a.patientId IN (
                  SELECT DISTINCT ap.patientId FROM Appointment ap WHERE ap.doctorId = :doctorId
              ))
            GROUP BY a.patientId
            """)
    List<PatientAlertCounts> getPatientAlertCounts(@Param("doctorId") String doctorId);

    /** Projection for alert type count stats. */
    interface AlertTypeCount {
        String getAlertType();
        Long getCount();
    }

    /** Projection for per-patient alert counts used in risk dashboard. */
    interface PatientAlertCounts {
        String getPatientId();
        Long getCriticalCount();
        Long getWarningCount();
    }
}
