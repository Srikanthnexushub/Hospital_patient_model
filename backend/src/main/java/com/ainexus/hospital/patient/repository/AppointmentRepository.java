package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.AppointmentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String>,
        JpaSpecificationExecutor<Appointment> {

    /**
     * Conflict detection: finds appointments for the same doctor on the same date
     * whose time window overlaps [startTime, endTime).
     * Uses PESSIMISTIC_WRITE (SELECT FOR UPDATE) for atomic conflict detection.
     * Excludes CANCELLED and NO_SHOW appointments.
     * Pass null for excludeId when booking new appointments.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.appointmentDate = :date
              AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
              AND a.startTime < :endTime
              AND a.endTime > :startTime
              AND (:excludeId IS NULL OR a.appointmentId != :excludeId)
            """)
    List<Appointment> findOverlappingAppointments(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") String excludeId
    );

    /**
     * Appointments for availability grid: all appointments for a doctor on a given date.
     * Callers are responsible for filtering out CANCELLED/NO_SHOW statuses.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.appointmentDate = :date
            ORDER BY a.startTime ASC
            """)
    List<Appointment> findByDoctorIdAndAppointmentDate(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Today's appointments — shortcut endpoint.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.appointmentDate = :today
              AND (:doctorId IS NULL OR a.doctorId = :doctorId)
            ORDER BY a.startTime ASC
            """)
    Page<Appointment> findTodayAppointments(
            @Param("today") LocalDate today,
            @Param("doctorId") String doctorId,
            Pageable pageable
    );

    /**
     * Patient appointment history — sorted by appointmentDate DESC.
     * DOCTOR role passes doctorId to restrict to own appointments.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.patientId = :patientId
              AND (:doctorId IS NULL OR a.doctorId = :doctorId)
            ORDER BY a.appointmentDate DESC, a.startTime DESC
            """)
    Page<Appointment> findPatientAppointmentHistory(
            @Param("patientId") String patientId,
            @Param("doctorId") String doctorId,
            Pageable pageable
    );
}
