package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.ProblemResponse;
import com.ainexus.hospital.patient.entity.PatientProblem;
import org.springframework.stereotype.Component;

@Component
public class ProblemMapper {

    public ProblemResponse toResponse(PatientProblem entity) {
        return new ProblemResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getIcdCode(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getOnsetDate(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }
}
