package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.Invoice;
import com.ainexus.hospital.patient.entity.InvoiceStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for dynamic Invoice queries.
 */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {}

    public static Specification<Invoice> search(
            String patientId,
            String appointmentId,
            String doctorId,
            InvoiceStatus status,
            LocalDate dateFrom,
            LocalDate dateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null)     predicates.add(cb.equal(root.get("patientId"),     patientId));
            if (appointmentId != null) predicates.add(cb.equal(root.get("appointmentId"), appointmentId));
            if (doctorId != null)      predicates.add(cb.equal(root.get("doctorId"),      doctorId));
            if (status != null)        predicates.add(cb.equal(root.get("status"),        status));
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
