package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.AllergyResponse;
import com.ainexus.hospital.patient.entity.PatientAllergy;
import org.springframework.stereotype.Component;

@Component
public class AllergyMapper {

    public AllergyResponse toResponse(PatientAllergy allergy) {
        return new AllergyResponse(
                allergy.getId(),
                allergy.getPatientId(),
                allergy.getSubstance(),
                allergy.getType(),
                allergy.getSeverity(),
                allergy.getReaction(),
                allergy.getOnsetDate(),
                allergy.getNotes(),
                allergy.getActive(),
                allergy.getCreatedBy(),
                allergy.getCreatedAt()
        );
    }
}
