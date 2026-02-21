package com.ainexus.hospital.patient.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "invoice_id_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceIdSequence {

    @Id
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
