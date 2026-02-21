package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.BloodGroup;
import com.ainexus.hospital.patient.entity.Gender;
import com.ainexus.hospital.patient.entity.Patient;
import com.ainexus.hospital.patient.entity.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {

    /**
     * Case-insensitive partial search across firstName, lastName, phone, email,
     * and patient_id prefix. Optionally filters by status, gender, and bloodGroup.
     * Null filter values mean "no filter" (show all).
     */
    @Query("""
            SELECT p FROM Patient p
            WHERE (
                :query IS NULL OR :query = ''
                OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :query, '%'))
                OR p.phone            LIKE CONCAT('%', :query, '%')
                OR LOWER(p.email)     LIKE LOWER(CONCAT('%', :query, '%'))
                OR p.patientId        LIKE CONCAT(:query, '%')
            )
            AND (:status IS NULL OR p.status = :status)
            AND (:gender IS NULL OR p.gender = :gender)
            AND (:bloodGroup IS NULL OR p.bloodGroup = :bloodGroup)
            """)
    Page<Patient> search(
            @Param("query") String query,
            @Param("status") PatientStatus status,
            @Param("gender") Gender gender,
            @Param("bloodGroup") BloodGroup bloodGroup,
            Pageable pageable
    );

    /** Find first patient with given phone number (for duplicate check). */
    Optional<Patient> findFirstByPhone(String phone);

    /**
     * Find first patient with given phone, excluding a specific patient ID.
     * Used by the update form to avoid false duplicate warnings.
     */
    @Query("SELECT p FROM Patient p WHERE p.phone = :phone AND p.patientId <> :excludeId")
    Optional<Patient> findFirstByPhoneAndPatientIdNot(
            @Param("phone") String phone,
            @Param("excludeId") String excludePatientId
    );

    /** Module 6 Dashboard — count patients by status. */
    long countByStatus(PatientStatus status);

    // NOTE: No delete methods are exposed — hard deletes are FORBIDDEN (spec FR-034).
}
