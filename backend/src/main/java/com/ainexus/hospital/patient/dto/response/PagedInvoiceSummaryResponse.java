package com.ainexus.hospital.patient.dto.response;

import java.util.List;

public record PagedInvoiceSummaryResponse(
        List<InvoiceSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
