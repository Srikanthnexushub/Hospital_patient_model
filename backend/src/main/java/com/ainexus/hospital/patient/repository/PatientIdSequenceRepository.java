package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.PatientIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientIdSequenceRepository extends JpaRepository<PatientIdSequence, Integer> {

    /**
     * Acquires a pessimistic write lock (SELECT FOR UPDATE) on the row for the
     * given year. Guarantees atomic ID generation under concurrent registrations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PatientIdSequence s WHERE s.year = :year")
    Optional<PatientIdSequence> findByYearForUpdate(@Param("year") Integer year);
}
