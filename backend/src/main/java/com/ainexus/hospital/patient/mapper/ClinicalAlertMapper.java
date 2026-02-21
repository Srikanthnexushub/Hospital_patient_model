package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.ClinicalAlertResponse;
import com.ainexus.hospital.patient.entity.ClinicalAlert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClinicalAlertMapper {

    /** Maps entity → DTO. patientName is intentionally omitted (populated by service). */
    @Mapping(target = "patientName", ignore = true)
    ClinicalAlertResponse toResponse(ClinicalAlert alert);
}
