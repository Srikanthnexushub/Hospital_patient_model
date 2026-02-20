package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.MedicationResponse;
import com.ainexus.hospital.patient.entity.PatientMedication;
import org.springframework.stereotype.Component;

@Component
public class MedicationMapper {

    public MedicationResponse toResponse(PatientMedication medication) {
        return new MedicationResponse(
                medication.getId(),
                medication.getPatientId(),
                medication.getMedicationName(),
                medication.getGenericName(),
                medication.getDosage(),
                medication.getFrequency(),
                medication.getRoute(),
                medication.getStartDate(),
                medication.getEndDate(),
                medication.getIndication(),
                medication.getPrescribedBy(),
                medication.getStatus(),
                medication.getNotes(),
                medication.getCreatedAt(),
                medication.getUpdatedAt()
        );
    }
}
