package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.InteractionSeverity;

public record DrugInteractionDto(
        String drug1,
        String drug2,
        InteractionSeverity severity,
        String mechanism,
        String clinicalEffect,
        String recommendation
) {}
