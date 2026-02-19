package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "patient_id_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdSequence {

    @Id
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
