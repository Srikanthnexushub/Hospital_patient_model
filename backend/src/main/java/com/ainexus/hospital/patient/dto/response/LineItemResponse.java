package com.ainexus.hospital.patient.dto.response;

import java.math.BigDecimal;

public record LineItemResponse(
        Long id,
        String serviceCode,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
