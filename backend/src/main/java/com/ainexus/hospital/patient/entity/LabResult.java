package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "order_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "value", columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range_low", precision = 10, scale = 3)
    private BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high", precision = 10, scale = 3)
    private BigDecimal referenceRangeHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "interpretation", length = 30, nullable = false)
    private LabResultInterpretation interpretation;

    @Column(name = "result_notes", columnDefinition = "TEXT")
    private String resultNotes;

    @Column(name = "resulted_by", length = 100, nullable = false)
    private String resultedBy;

    @Column(name = "resulted_at", nullable = false)
    private OffsetDateTime resultedAt;

    @PrePersist
    private void prePersist() {
        if (resultedAt == null) resultedAt = OffsetDateTime.now();
    }
}
