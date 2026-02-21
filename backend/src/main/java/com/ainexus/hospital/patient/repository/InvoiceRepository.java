package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.Invoice;
import com.ainexus.hospital.patient.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByAppointmentId(String appointmentId);

    boolean existsByAppointmentId(String appointmentId);

    // Financial report aggregate: counts and amount totals for a date range
    @Query(value = """
            SELECT
                COALESCE(SUM(total_amount), 0)                                                        AS totalInvoiced,
                COALESCE(SUM(CASE WHEN status IN ('ISSUED','PARTIALLY_PAID') THEN amount_due ELSE 0 END), 0) AS totalOutstanding,
                COALESCE(SUM(CASE WHEN status = 'WRITTEN_OFF' THEN net_amount ELSE 0 END), 0)         AS totalWrittenOff,
                COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN total_amount ELSE 0 END), 0)         AS totalCancelled,
                COUNT(*)                                                                               AS invoiceCount,
                COUNT(CASE WHEN status = 'PAID' THEN 1 END)                                          AS paidCount,
                COUNT(CASE WHEN status = 'PARTIALLY_PAID' THEN 1 END)                                AS partialCount
            FROM invoices
            WHERE CAST(created_at AS date) BETWEEN :dateFrom AND :dateTo
            """, nativeQuery = true)
    List<Object[]> getFinancialSummary(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    // Overdue: ISSUED or PARTIALLY_PAID where linked appointment date is before today
    @Query(value = """
            SELECT COUNT(*)
            FROM invoices i
            JOIN appointments a ON a.appointment_id = i.appointment_id
            WHERE i.status IN ('ISSUED', 'PARTIALLY_PAID')
              AND a.appointment_date < CURRENT_DATE
              AND CAST(i.created_at AS date) BETWEEN :dateFrom AND :dateTo
            """, nativeQuery = true)
    Long countOverdue(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
