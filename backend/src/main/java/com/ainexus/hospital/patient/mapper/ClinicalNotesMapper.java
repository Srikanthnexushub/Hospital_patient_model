package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.ClinicalNotesResponse;
import com.ainexus.hospital.patient.entity.ClinicalNotes;
import org.springframework.stereotype.Component;

@Component
public class ClinicalNotesMapper {

    public ClinicalNotesResponse toResponse(ClinicalNotes notes, boolean includePrivateNotes) {
        return new ClinicalNotesResponse(
                notes.getAppointmentId(),
                notes.getChiefComplaint(),
                notes.getDiagnosis(),
                notes.getTreatment(),
                notes.getPrescription(),
                Boolean.TRUE.equals(notes.getFollowUpRequired()),
                notes.getFollowUpDays(),
                includePrivateNotes ? notes.getPrivateNotes() : null,
                notes.getCreatedBy(),
                notes.getCreatedAt(),
                notes.getUpdatedAt()
        );
    }
}
