package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.ClinicalNotes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicalNotesRepository extends JpaRepository<ClinicalNotes, String> {
}
