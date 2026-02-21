package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.InvoiceIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceIdSequenceRepository extends JpaRepository<InvoiceIdSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceIdSequence s WHERE s.year = :year")
    Optional<InvoiceIdSequence> findByYearForUpdate(@Param("year") Integer year);
}
