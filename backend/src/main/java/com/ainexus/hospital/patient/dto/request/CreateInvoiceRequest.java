package com.ainexus.hospital.patient.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateInvoiceRequest(
        @NotBlank(message = "Appointment ID is required")
        String appointmentId,

        @NotEmpty(message = "At least one line item is required")
        @Valid
        List<LineItemRequest> lineItems,

        @DecimalMin(value = "0.00", message = "Discount percent must be 0 or greater")
        @DecimalMax(value = "100.00", message = "Discount percent cannot exceed 100")
        BigDecimal discountPercent,

        String notes
) {
    public BigDecimal effectiveDiscountPercent() {
        return discountPercent != null ? discountPercent : BigDecimal.ZERO;
    }
}
