package com.ainexus.hospital.patient.dto;

public record News2ComponentScoreDto(
        String parameter,
        String value,
        int score,
        String unit,
        boolean defaulted
) {}
