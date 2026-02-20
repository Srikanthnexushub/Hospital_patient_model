package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.InvoiceDetailResponse;
import com.ainexus.hospital.patient.dto.response.InvoiceSummaryResponse;
import com.ainexus.hospital.patient.dto.response.LineItemResponse;
import com.ainexus.hospital.patient.dto.response.PaymentResponse;
import com.ainexus.hospital.patient.entity.Invoice;
import com.ainexus.hospital.patient.entity.InvoiceLineItem;
import com.ainexus.hospital.patient.entity.InvoicePayment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps Invoice entities to response DTOs.
 *
 * patientName, doctorName, appointmentDate, lineItems, and payments are resolved
 * in the service layer and passed in as parameters — entities carry only FK columns.
 */
@Component
public class InvoiceMapper {

    public InvoiceDetailResponse toDetailResponse(Invoice invoice,
                                                   String patientName,
                                                   String doctorName,
                                                   String appointmentDate,
                                                   List<LineItemResponse> lineItems,
                                                   List<PaymentResponse> payments) {
        return new InvoiceDetailResponse(
                invoice.getInvoiceId(),
                invoice.getAppointmentId(),
                appointmentDate,
                invoice.getPatientId(),
                patientName,
                invoice.getDoctorId(),
                doctorName,
                invoice.getStatus(),
                invoice.getTotalAmount(),
                invoice.getDiscountPercent(),
                invoice.getDiscountAmount(),
                invoice.getNetAmount(),
                invoice.getTaxRate(),
                invoice.getTaxAmount(),
                invoice.getAmountDue(),
                invoice.getAmountPaid(),
                invoice.getNotes(),
                invoice.getCancelReason(),
                invoice.getVersion(),
                invoice.getCreatedAt(),
                invoice.getCreatedBy(),
                invoice.getUpdatedAt(),
                invoice.getUpdatedBy(),
                lineItems,
                payments
        );
    }

    public InvoiceSummaryResponse toSummaryResponse(Invoice invoice, String patientName) {
        return new InvoiceSummaryResponse(
                invoice.getInvoiceId(),
                invoice.getAppointmentId(),
                invoice.getPatientId(),
                patientName,
                invoice.getDoctorId(),
                invoice.getStatus(),
                invoice.getTotalAmount(),
                invoice.getAmountDue(),
                invoice.getAmountPaid(),
                invoice.getCreatedAt()
        );
    }

    public LineItemResponse toLineItemResponse(InvoiceLineItem item) {
        return new LineItemResponse(
                item.getId(),
                item.getServiceCode(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }

    public PaymentResponse toPaymentResponse(InvoicePayment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getReferenceNumber(),
                payment.getNotes(),
                payment.getPaidAt(),
                payment.getRecordedBy()
        );
    }
}
