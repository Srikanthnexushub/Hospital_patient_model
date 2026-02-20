package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks the last-used sequence number for Staff User ID generation, per calendar year.
 * Mirrors PatientIdSequence — uses SELECT FOR UPDATE for atomic increment.
 */
@Entity
@Table(name = "staff_id_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffIdSequence {

    @Id
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
