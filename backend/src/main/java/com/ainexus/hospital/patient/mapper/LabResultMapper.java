package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.LabResultResponse;
import com.ainexus.hospital.patient.entity.LabResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LabResultMapper {

    /**
     * Maps entity to response. testName, alertCreated, and alertId are derived data
     * that the service layer injects after mapping.
     */
    @Mapping(target = "testName", ignore = true)
    @Mapping(target = "alertCreated", constant = "false")
    @Mapping(target = "alertId", ignore = true)
    LabResultResponse toResponse(LabResult result);
}
