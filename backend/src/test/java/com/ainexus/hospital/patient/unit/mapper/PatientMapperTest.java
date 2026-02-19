package com.ainexus.hospital.patient.unit.mapper;

import com.ainexus.hospital.patient.dto.response.PatientResponse;
import com.ainexus.hospital.patient.dto.response.PatientSummaryResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.mapper.PatientMapperImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PatientMapperTest {

    private final PatientMapperImpl mapper = new PatientMapperImpl();

    private Patient buildPatient(LocalDate dob) {
        return Patient.builder()
                .patientId("P2026001")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(dob)
                .gender(Gender.FEMALE)
                .bloodGroup(BloodGroup.A_POS)
                .phone("555-123-4567")
                .status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .createdBy("receptionist1")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("receptionist1")
                .version(0)
                .build();
    }

    @Test
    void toResponse_computesAgeCorrectlyForPastBirthday() {
        // DOB: March 15, 1990. Today is Feb 19, 2026. Birthday not yet reached → age 35.
        LocalDate dob = LocalDate.of(1990, 3, 15);
        Patient patient = buildPatient(dob);

        PatientResponse response = mapper.toResponse(patient);

        assertThat(response.age()).isEqualTo(35);
        assertThat(response.patientId()).isEqualTo("P2026001");
        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.lastName()).isEqualTo("Smith");
    }

    @Test
    void toResponse_computesAgeCorrectlyOnBirthday() {
        // DOB = today: birthday just happened → age = 0 (or current year calculation)
        LocalDate today = LocalDate.now();
        Patient patient = buildPatient(today.minusYears(36));

        PatientResponse response = mapper.toResponse(patient);

        // On birthday, period is exactly N years
        assertThat(response.age()).isEqualTo(36);
    }

    @Test
    void toSummary_mapsSubsetFields() {
        Patient patient = buildPatient(LocalDate.of(1985, 6, 15));

        PatientSummaryResponse summary = mapper.toSummary(patient);

        assertThat(summary.patientId()).isEqualTo("P2026001");
        assertThat(summary.firstName()).isEqualTo("Jane");
        assertThat(summary.lastName()).isEqualTo("Smith");
        assertThat(summary.gender()).isEqualTo(Gender.FEMALE);
        assertThat(summary.phone()).isEqualTo("555-123-4567");
        assertThat(summary.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(summary.age()).isGreaterThan(0);
    }

    @Test
    void toAge_forSameDayBirthday_countsAsCompleteYear() {
        LocalDate today = LocalDate.now();
        LocalDate dob = today.minusYears(25);
        int age = mapper.toAge(dob);
        assertThat(age).isEqualTo(25);
    }

    @Test
    void toAge_forNullDateOfBirth_returnsZero() {
        assertThat(mapper.toAge(null)).isEqualTo(0);
    }
}
