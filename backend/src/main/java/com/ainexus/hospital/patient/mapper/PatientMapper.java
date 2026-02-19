package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.request.PatientRegistrationRequest;
import com.ainexus.hospital.patient.dto.request.PatientUpdateRequest;
import com.ainexus.hospital.patient.dto.response.PatientResponse;
import com.ainexus.hospital.patient.dto.response.PatientSummaryResponse;
import com.ainexus.hospital.patient.entity.Patient;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.Period;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PatientMapper {

    /** Map Patient entity → full PatientResponse DTO (includes computed age). */
    @Mapping(target = "age", expression = "java(toAge(patient.getDateOfBirth()))")
    PatientResponse toResponse(Patient patient);

    /** Map Patient entity → summary row for list view (includes computed age). */
    @Mapping(target = "age", expression = "java(toAge(patient.getDateOfBirth()))")
    PatientSummaryResponse toSummary(Patient patient);

    /**
     * Map registration request → Patient entity.
     * patientId, status, createdAt, createdBy, updatedAt, updatedBy are set in service.
     */
    @Mapping(target = "patientId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Patient toEntity(PatientRegistrationRequest request);

    /**
     * Merge update request fields onto an existing Patient entity in-place.
     * patientId, createdAt, createdBy, and version remain unchanged.
     */
    @Mapping(target = "patientId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(PatientUpdateRequest request, @MappingTarget Patient patient);

    /**
     * Compute age in complete years from date of birth to today.
     * Age for a patient born exactly today counts as 0 (birthday is today).
     */
    default int toAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
