package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.InvoiceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceAuditLogRepository extends JpaRepository<InvoiceAuditLog, Long> {

    List<InvoiceAuditLog> findByInvoiceId(String invoiceId);
}
