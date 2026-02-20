package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    List<InvoicePayment> findByInvoiceId(String invoiceId);

    // Total collected per payment method for invoices created in date range
    @Query(value = """
            SELECT p.payment_method, COALESCE(SUM(p.amount), 0)
            FROM invoice_payments p
            JOIN invoices i ON i.invoice_id = p.invoice_id
            WHERE CAST(i.created_at AS date) BETWEEN :dateFrom AND :dateTo
            GROUP BY p.payment_method
            """, nativeQuery = true)
    List<Object[]> sumByPaymentMethodForDateRange(@Param("dateFrom") LocalDate dateFrom,
                                                   @Param("dateTo") LocalDate dateTo);

    // Total collected (all payments) for invoices in date range
    @Query(value = """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM invoice_payments p
            JOIN invoices i ON i.invoice_id = p.invoice_id
            WHERE CAST(i.created_at AS date) BETWEEN :dateFrom AND :dateTo
            """, nativeQuery = true)
    BigDecimal sumCollectedForDateRange(@Param("dateFrom") LocalDate dateFrom,
                                        @Param("dateTo") LocalDate dateTo);
}
