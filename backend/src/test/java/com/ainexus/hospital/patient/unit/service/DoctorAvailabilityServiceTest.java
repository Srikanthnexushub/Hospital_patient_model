package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.dto.response.AvailabilityResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.AppointmentType;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.AppointmentMapper;
import com.ainexus.hospital.patient.mapper.StaffMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.DoctorAvailabilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorAvailabilityServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private HospitalUserRepository hospitalUserRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentMapper mapper;
    @Mock private StaffMapper staffMapper;

    // Real RoleGuard reads from AuthContext.Holder (ThreadLocal)
    private final RoleGuard roleGuard = new RoleGuard();

    private DoctorAvailabilityService availabilityService;

    private static final String DOCTOR_ID = "D2026001";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 15);

    // Day boundaries defined by DoctorAvailabilityService: 08:00-18:00, 30-min slots = 20 slots
    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final int EXPECTED_SLOT_COUNT = 20;

    @BeforeEach
    void setUp() {
        availabilityService = new DoctorAvailabilityService(
                appointmentRepository, hospitalUserRepository, patientRepository,
                mapper, roleGuard, staffMapper
        );
    }

    @AfterEach
    void clearAuth() {
        AuthContext.Holder.clear();
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    private HospitalUser doctorUser(String doctorId, String username) {
        return HospitalUser.builder()
                .userId(doctorId)
                .username(username)
                .passwordHash("$2a$10$irrelevant")
                .role("DOCTOR")
                .status("ACTIVE")
                .version(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Appointment bookedAppointment(String appointmentId, LocalTime start, LocalTime end) {
        return Appointment.builder()
                .appointmentId(appointmentId)
                .patientId("P2026001")
                .doctorId(DOCTOR_ID)
                .appointmentDate(TEST_DATE)
                .startTime(start)
                .endTime(end)
                .durationMinutes(60)
                .type(AppointmentType.GENERAL_CONSULTATION)
                .status(AppointmentStatus.CONFIRMED)
                .reason("Consultation")
                .createdBy("receptionist1")
                .updatedBy("receptionist1")
                .version(0)
                .build();
    }

    // ── Test 1: No appointments → all 20 slots available ─────────────────────

    @Test
    void getAvailability_noAppointments_allSlotsAvailable() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(hospitalUserRepository.findById(DOCTOR_ID))
                .thenReturn(Optional.of(doctorUser(DOCTOR_ID, "dr.house")));
        when(appointmentRepository.findByDoctorIdAndAppointmentDate(DOCTOR_ID, TEST_DATE))
                .thenReturn(Collections.emptyList());

        AvailabilityResponse response = availabilityService.getAvailability(DOCTOR_ID, TEST_DATE);

        assertThat(response).isNotNull();
        assertThat(response.doctorId()).isEqualTo(DOCTOR_ID);
        assertThat(response.doctorName()).isEqualTo("dr.house");
        assertThat(response.date()).isEqualTo(TEST_DATE);
        assertThat(response.slots()).hasSize(EXPECTED_SLOT_COUNT);

        // Every slot must be available and have no appointmentId
        assertThat(response.slots()).allMatch(slot -> slot.available());
        assertThat(response.slots()).allMatch(slot -> slot.appointmentId() == null);

        // First slot starts at 08:00; last slot ends at 18:00
        assertThat(response.slots().get(0).startTime()).isEqualTo(DAY_START);
        assertThat(response.slots().get(EXPECTED_SLOT_COUNT - 1).endTime())
                .isEqualTo(LocalTime.of(18, 0));
    }

    // ── Test 2: One 60-min appointment at 09:00 blocks two 30-min slots ──────

    @Test
    void getAvailability_withOneHourAppointment_twoSlotsBusy() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        // Appointment 09:00-10:00 overlaps slots: [09:00-09:30] and [09:30-10:00]
        LocalTime apptStart = LocalTime.of(9, 0);
        LocalTime apptEnd   = LocalTime.of(10, 0);
        Appointment booked = bookedAppointment("APT20260001", apptStart, apptEnd);

        when(hospitalUserRepository.findById(DOCTOR_ID))
                .thenReturn(Optional.of(doctorUser(DOCTOR_ID, "dr.house")));
        when(appointmentRepository.findByDoctorIdAndAppointmentDate(DOCTOR_ID, TEST_DATE))
                .thenReturn(List.of(booked));

        AvailabilityResponse response = availabilityService.getAvailability(DOCTOR_ID, TEST_DATE);

        assertThat(response.slots()).hasSize(EXPECTED_SLOT_COUNT);

        // Slot 09:00-09:30 should be unavailable
        var slot0900 = response.slots().stream()
                .filter(s -> s.startTime().equals(LocalTime.of(9, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot0900.available()).isFalse();
        assertThat(slot0900.appointmentId()).isEqualTo("APT20260001");

        // Slot 09:30-10:00 should be unavailable
        var slot0930 = response.slots().stream()
                .filter(s -> s.startTime().equals(LocalTime.of(9, 30)))
                .findFirst()
                .orElseThrow();
        assertThat(slot0930.available()).isFalse();
        assertThat(slot0930.appointmentId()).isEqualTo("APT20260001");

        // Slot 10:00-10:30 should be available (appointment ends exactly at 10:00)
        var slot1000 = response.slots().stream()
                .filter(s -> s.startTime().equals(LocalTime.of(10, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot1000.available()).isTrue();

        // 20 slots total, 2 unavailable → 18 available
        long availableCount = response.slots().stream().filter(s -> s.available()).count();
        assertThat(availableCount).isEqualTo(18);
    }

    // ── Test 3: Doctor not found → ResourceNotFoundException ─────────────────

    @Test
    void getAvailability_doctorNotFound_throwsResourceNotFoundException() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.getAvailability(DOCTOR_ID, TEST_DATE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(DOCTOR_ID);
    }

    // ── Test 4: DOCTOR role cannot view another doctor's schedule ─────────────

    @Test
    void getSchedule_doctorCannotViewOthersSchedule_throwsForbidden() {
        // "doc1" is authenticated but requests schedule for a different doctorId "D2026002"
        AuthContext.Holder.set(new AuthContext("doc1", "dr.smith", "DOCTOR"));

        String otherDoctorId = "D2026002";

        assertThatThrownBy(() -> availabilityService.getSchedule(otherDoctorId, TEST_DATE, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own schedule");
    }

    // ── Bonus: DOCTOR can view own schedule ───────────────────────────────────

    @Test
    void getSchedule_doctorCanViewOwnSchedule_doesNotThrow() {
        AuthContext.Holder.set(new AuthContext(DOCTOR_ID, "dr.house", "DOCTOR"));

        when(hospitalUserRepository.findById(DOCTOR_ID))
                .thenReturn(Optional.of(doctorUser(DOCTOR_ID, "dr.house")));
        when(appointmentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        var result = availabilityService.getSchedule(DOCTOR_ID, TEST_DATE, 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEmpty();
    }

    // ── Bonus: RECEPTIONIST can view any doctor's availability ────────────────

    @Test
    void getAvailability_receptionistCanViewAnyDoctor_succeeds() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(hospitalUserRepository.findById(DOCTOR_ID))
                .thenReturn(Optional.of(doctorUser(DOCTOR_ID, "dr.house")));
        when(appointmentRepository.findByDoctorIdAndAppointmentDate(DOCTOR_ID, TEST_DATE))
                .thenReturn(Collections.emptyList());

        AvailabilityResponse response = availabilityService.getAvailability(DOCTOR_ID, TEST_DATE);
        assertThat(response).isNotNull();
        assertThat(response.slots()).hasSize(EXPECTED_SLOT_COUNT);
    }
}
