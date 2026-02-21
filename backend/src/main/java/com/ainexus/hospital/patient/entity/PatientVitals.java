package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_vitals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientVitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "appointment_id", length = 14, nullable = false, unique = true)
    private String appointmentId;

    @Column(name = "patient_id", length = 14, nullable = false)
    private String patientId;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "height", precision = 5, scale = 1)
    private BigDecimal height;

    @Column(name = "oxygen_saturation")
    private Integer oxygenSaturation;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "recorded_by", length = 100, nullable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    private void prePersist() {
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }
}
