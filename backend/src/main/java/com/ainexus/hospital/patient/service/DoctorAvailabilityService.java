package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.dto.response.AppointmentSummaryResponse;
import com.ainexus.hospital.patient.dto.response.AvailabilityResponse;
import com.ainexus.hospital.patient.dto.response.TimeSlotResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.AppointmentMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.dto.response.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * US5: Doctor availability — computes 20 × 30-minute slots (08:00–18:00).
 * US2: Doctor schedule — all appointments for a doctor on a given date.
 */
@Service
public class DoctorAvailabilityService {

    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END   = LocalTime.of(18, 0);
    private static final int SLOT_MINUTES    = 30;

    private final AppointmentRepository appointmentRepository;
    private final HospitalUserRepository hospitalUserRepository;
    private final PatientRepository patientRepository;
    private final AppointmentMapper mapper;
    private final RoleGuard roleGuard;

    public DoctorAvailabilityService(AppointmentRepository appointmentRepository,
                                     HospitalUserRepository hospitalUserRepository,
                                     PatientRepository patientRepository,
                                     AppointmentMapper mapper,
                                     RoleGuard roleGuard) {
        this.appointmentRepository = appointmentRepository;
        this.hospitalUserRepository = hospitalUserRepository;
        this.patientRepository = patientRepository;
        this.mapper = mapper;
        this.roleGuard = roleGuard;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(String doctorId, LocalDate date) {
        roleGuard.requireAuthenticated();

        HospitalUser doctor = hospitalUserRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        // Fetch all appointments for this doctor/date, then exclude terminal statuses in Java
        // (avoids JPQL string-literal enum comparison which can behave unexpectedly in Hibernate 6)
        List<Appointment> booked = appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, date)
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED
                          && a.getStatus() != AppointmentStatus.NO_SHOW)
                .toList();

        List<TimeSlotResponse> slots = new ArrayList<>();
        LocalTime current = DAY_START;

        while (current.isBefore(DAY_END)) {
            LocalTime slotEnd = current.plusMinutes(SLOT_MINUTES);
            final LocalTime slotStart = current;

            // Find if any booking overlaps this 30-min slot
            Appointment blockingAppt = booked.stream()
                    .filter(a -> a.getStartTime().isBefore(slotEnd) && a.getEndTime().isAfter(slotStart))
                    .findFirst()
                    .orElse(null);

            if (blockingAppt != null) {
                slots.add(new TimeSlotResponse(slotStart, slotEnd, false, blockingAppt.getAppointmentId()));
            } else {
                slots.add(new TimeSlotResponse(slotStart, slotEnd, true, null));
            }

            current = slotEnd;
        }

        return new AvailabilityResponse(date, doctorId, doctor.getUsername(), slots);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentSummaryResponse> getSchedule(String doctorId, LocalDate date, int page, int size) {
        roleGuard.requireAuthenticated();
        size = Math.min(size, 100);

        // DOCTOR role can only view own schedule
        AuthContext ctx = AuthContext.Holder.get();
        if ("DOCTOR".equals(ctx.getRole()) && !ctx.getUserId().equals(doctorId)) {
            throw new ForbiddenException("Doctors can only view their own schedule.");
        }

        // Validate doctor exists
        hospitalUserRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        Page<Appointment> resultPage = appointmentRepository.searchAppointments(
                doctorId, null, date, null, null, null, null,
                PageRequest.of(page, size));

        List<AppointmentSummaryResponse> content = resultPage.getContent().stream()
                .map(a -> {
                    String patientName = resolvePatientName(a.getPatientId());
                    String doctorName  = resolveDoctorName(a.getDoctorId());
                    return mapper.toSummary(a, patientName, doctorName);
                })
                .toList();

        return new PagedResponse<>(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages(),
                resultPage.isFirst(), resultPage.isLast());
    }

    private String resolvePatientName(String patientId) {
        return patientRepository.findById(patientId)
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Unknown");
    }

    private String resolveDoctorName(String doctorId) {
        return hospitalUserRepository.findById(doctorId)
                .map(HospitalUser::getUsername)
                .orElse("Unknown");
    }
}
