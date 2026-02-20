package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.dto.response.MedicalSummaryResponse;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MedicalSummaryService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProblemService problemService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final VitalsService vitalsService;
    private final RoleGuard roleGuard;

    public MedicalSummaryService(PatientRepository patientRepository,
                                 AppointmentRepository appointmentRepository,
                                 ProblemService problemService,
                                 MedicationService medicationService,
                                 AllergyService allergyService,
                                 VitalsService vitalsService,
                                 RoleGuard roleGuard) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.problemService = problemService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.vitalsService = vitalsService;
        this.roleGuard = roleGuard;
    }

    @Transactional(readOnly = true)
    public MedicalSummaryResponse getMedicalSummary(String patientId) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        LocalDate lastVisitDate = appointmentRepository
                .findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(patientId, AppointmentStatus.COMPLETED)
                .map(a -> a.getAppointmentDate())
                .orElse(null);

        long totalVisits = appointmentRepository.countByPatientId(patientId);

        return new MedicalSummaryResponse(
                problemService.getActiveProblems(patientId),
                medicationService.getActiveMedications(patientId),
                allergyService.getActiveAllergies(patientId),
                vitalsService.getTop5VitalsByPatient(patientId),
                lastVisitDate,
                totalVisits
        );
    }
}
