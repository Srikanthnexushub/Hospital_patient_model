package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.StaffIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffIdSequenceRepository extends JpaRepository<StaffIdSequence, Integer> {

    /**
     * Acquires a pessimistic write lock (SELECT FOR UPDATE) on the row for the
     * given year. Guarantees atomic Staff ID generation under concurrent requests.
     * Mirrors PatientIdSequenceRepository.findByYearForUpdate().
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StaffIdSequence s WHERE s.year = :year")
    Optional<StaffIdSequence> findByYearForUpdate(@Param("year") Integer year);
}
