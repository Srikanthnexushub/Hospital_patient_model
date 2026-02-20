package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.AppointmentType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for dynamic Appointment queries.
 * Avoids Hibernate 6 / PostgreSQL JDBC parameter type issues that occur
 * when passing null LocalDate/enum values to IS NULL JPQL patterns.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    public static Specification<Appointment> search(
            String doctorId,
            String patientId,
            LocalDate date,
            LocalDate dateFrom,
            LocalDate dateTo,
            AppointmentStatus status,
            AppointmentType type) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (doctorId != null)  predicates.add(cb.equal(root.get("doctorId"),        doctorId));
            if (patientId != null) predicates.add(cb.equal(root.get("patientId"),       patientId));
            if (date != null)      predicates.add(cb.equal(root.get("appointmentDate"), date));
            if (dateFrom != null)  predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentDate"), dateFrom));
            if (dateTo != null)    predicates.add(cb.lessThanOrEqualTo(root.get("appointmentDate"),    dateTo));
            if (status != null)    predicates.add(cb.equal(root.get("status"),          status));
            if (type != null)      predicates.add(cb.equal(root.get("type"),            type));

            if (query != null) {
                query.orderBy(
                    cb.asc(root.get("appointmentDate")),
                    cb.asc(root.get("startTime"))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
