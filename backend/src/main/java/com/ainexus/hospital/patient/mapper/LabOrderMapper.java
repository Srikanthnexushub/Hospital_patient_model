package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.LabOrderResponse;
import com.ainexus.hospital.patient.dto.LabOrderSummaryResponse;
import com.ainexus.hospital.patient.entity.LabOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LabOrderMapper {

    LabOrderResponse toResponse(LabOrder order);

    /** Maps to the lightweight summary shape. hasResult is populated by the service. */
    @Mapping(target = "hasResult", constant = "false")
    LabOrderSummaryResponse toSummary(LabOrder order);
}
