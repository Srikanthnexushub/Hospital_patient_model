package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AuditService;
import com.ainexus.hospital.patient.dto.request.PatientRegistrationRequest;
import com.ainexus.hospital.patient.dto.request.PatientStatusChangeRequest;
import com.ainexus.hospital.patient.dto.request.PatientUpdateRequest;
import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.PatientNotFoundException;
import com.ainexus.hospital.patient.mapper.PatientMapper;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientIdGeneratorService idGeneratorService;
    private final PatientMapper patientMapper;
    private final AuditService auditService;
    private final RoleGuard roleGuard;

    // Micrometer counters
    private final Counter registrationsCounter;
    private final Counter searchesCounter;
    private final Counter updatesCounter;
    private final Counter statusChangesCounter;

    public PatientService(PatientRepository patientRepository,
                          PatientIdGeneratorService idGeneratorService,
                          PatientMapper patientMapper,
                          AuditService auditService,
                          RoleGuard roleGuard,
                          MeterRegistry meterRegistry) {
        this.patientRepository = patientRepository;
        this.idGeneratorService = idGeneratorService;
        this.patientMapper = patientMapper;
        this.auditService = auditService;
        this.roleGuard = roleGuard;

        this.registrationsCounter = Counter.builder("patient.registrations.total")
                .description("Total successful patient registrations").register(meterRegistry);
        this.searchesCounter = Counter.builder("patient.searches.total")
                .description("Total search operations executed").register(meterRegistry);
        this.updatesCounter = Counter.builder("patient.updates.total")
                .description("Total patient record updates").register(meterRegistry);
        this.statusChangesCounter = Counter.builder("patient.status_changes.total")
                .description("Total activate/deactivate operations").register(meterRegistry);
    }

    // ── US1: Register ─────────────────────────────────────────────────────────

    @Transactional
    public PatientRegistrationResponse registerPatient(PatientRegistrationRequest request) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");
        AuthContext auth = AuthContext.Holder.get();

        String patientId = idGeneratorService.generatePatientId();

        Patient patient = patientMapper.toEntity(request);
        patient.setPatientId(patientId);
        patient.setStatus(PatientStatus.ACTIVE);
        patient.setBloodGroup(request.bloodGroup() != null ? request.bloodGroup() : BloodGroup.UNKNOWN);

        OffsetDateTime now = OffsetDateTime.now();
        patient.setCreatedAt(now);
        patient.setCreatedBy(auth.getUsername());
        patient.setUpdatedAt(now);
        patient.setUpdatedBy(auth.getUsername());

        patientRepository.save(patient);
        auditService.writeAuditLog("REGISTER", patientId, auth.getUsername(), null);

        MDC.put("operation", "REGISTER_PATIENT");
        MDC.put("patientId", patientId);
        registrationsCounter.increment();

        return new PatientRegistrationResponse(
                patientId,
                "Patient registered successfully. Patient ID: " + patientId
        );
    }

    public DuplicatePhoneResponse checkDuplicatePhone(String phone, String excludePatientId) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");

        Optional<Patient> existing = excludePatientId != null
                ? patientRepository.findFirstByPhoneAndPatientIdNot(phone, excludePatientId)
                : patientRepository.findFirstByPhone(phone);

        return existing
                .map(p -> DuplicatePhoneResponse.found(p.getPatientId(), p.getFirstName(), p.getLastName()))
                .orElse(DuplicatePhoneResponse.noDuplicate());
    }

    // ── US2: Search ────────────────────────────────────────────────────────────

    public PagedResponse<PatientSummaryResponse> searchPatients(
            String query, String statusStr, String genderStr, String bloodGroupStr,
            int page, int size) {
        roleGuard.requireAuthenticated();

        PatientStatus status = resolveStatus(statusStr);
        Gender gender = resolveGender(genderStr);
        BloodGroup bloodGroup = resolveBloodGroup(bloodGroupStr);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Patient> patientPage = patientRepository.search(
                query != null && !query.isBlank() ? query : null,
                status, gender, bloodGroup, pageable
        );

        MDC.put("operation", "SEARCH_PATIENTS");
        searchesCounter.increment();

        List<PatientSummaryResponse> content = patientPage.getContent().stream()
                .map(patientMapper::toSummary).toList();

        return new PagedResponse<>(
                content,
                patientPage.getNumber(),
                patientPage.getSize(),
                patientPage.getTotalElements(),
                patientPage.getTotalPages(),
                patientPage.isFirst(),
                patientPage.isLast()
        );
    }

    // ── US3: Profile ───────────────────────────────────────────────────────────

    public PatientResponse getPatient(String patientId) {
        roleGuard.requireAuthenticated();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        MDC.put("operation", "GET_PATIENT");
        MDC.put("patientId", patientId);

        return patientMapper.toResponse(patient);
    }

    // ── US4: Update ────────────────────────────────────────────────────────────

    @Transactional
    public PatientResponse updatePatient(String patientId, PatientUpdateRequest request, Integer version) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");
        AuthContext auth = AuthContext.Holder.get();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        // Validate client version matches entity version (optimistic concurrency check)
        if (!patient.getVersion().equals(version)) {
            throw new ConflictException(
                    "Patient record was modified by another user. Please reload and try again.");
        }

        List<String> changedFields = computeChangedFields(patient, request);

        try {
            patientMapper.updateEntity(request, patient);
            patient.setBloodGroup(request.bloodGroup() != null ? request.bloodGroup() : BloodGroup.UNKNOWN);
            patient.setUpdatedAt(OffsetDateTime.now());
            patient.setUpdatedBy(auth.getUsername());

            Patient saved = patientRepository.save(patient);
            auditService.writeAuditLog("UPDATE", patientId, auth.getUsername(), changedFields);

            MDC.put("operation", "UPDATE_PATIENT");
            MDC.put("patientId", patientId);
            updatesCounter.increment();

            return patientMapper.toResponse(saved);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException(
                    "Patient record was modified by another user. Please reload and try again.");
        }
    }

    // ── US5: Status Management ──────────────────────────────────────────────────

    @Transactional
    public PatientStatusChangeResponse changePatientStatus(String patientId,
                                                            PatientStatusChangeRequest request) {
        roleGuard.requireRoles("ADMIN");
        AuthContext auth = AuthContext.Holder.get();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        PatientStatusChangeRequest.StatusAction action = request.action();
        PatientStatus currentStatus = patient.getStatus();

        if (action == PatientStatusChangeRequest.StatusAction.DEACTIVATE
                && currentStatus == PatientStatus.INACTIVE) {
            throw new ConflictException("Patient is already inactive.");
        }
        if (action == PatientStatusChangeRequest.StatusAction.ACTIVATE
                && currentStatus == PatientStatus.ACTIVE) {
            throw new ConflictException("Patient is already active.");
        }

        PatientStatus newStatus = action == PatientStatusChangeRequest.StatusAction.DEACTIVATE
                ? PatientStatus.INACTIVE : PatientStatus.ACTIVE;
        patient.setStatus(newStatus);
        patient.setUpdatedAt(OffsetDateTime.now());
        patient.setUpdatedBy(auth.getUsername());
        patientRepository.save(patient);

        String operation = action == PatientStatusChangeRequest.StatusAction.DEACTIVATE
                ? "DEACTIVATE" : "ACTIVATE";
        auditService.writeAuditLog(operation, patientId, auth.getUsername(), null);

        MDC.put("operation", "CHANGE_PATIENT_STATUS");
        MDC.put("patientId", patientId);
        statusChangesCounter.increment();

        String fullName = patient.getFirstName() + " " + patient.getLastName();
        String verb = newStatus == PatientStatus.INACTIVE ? "deactivated" : "activated";
        return new PatientStatusChangeResponse(
                patientId, newStatus,
                "Patient " + fullName + " has been " + verb + "."
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private PatientStatus resolveStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank() || "ALL".equalsIgnoreCase(statusStr)) return null;
        try { return PatientStatus.valueOf(statusStr.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Gender resolveGender(String genderStr) {
        if (genderStr == null || genderStr.isBlank() || "ALL".equalsIgnoreCase(genderStr)) return null;
        try { return Gender.valueOf(genderStr.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private BloodGroup resolveBloodGroup(String bgStr) {
        if (bgStr == null || bgStr.isBlank() || "ALL".equalsIgnoreCase(bgStr)) return null;
        try { return BloodGroup.valueOf(bgStr.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private List<String> computeChangedFields(Patient current, PatientUpdateRequest update) {
        List<String> changed = new ArrayList<>();
        if (!eq(current.getFirstName(), update.firstName())) changed.add("firstName");
        if (!eq(current.getLastName(), update.lastName())) changed.add("lastName");
        if (!eq(current.getDateOfBirth(), update.dateOfBirth())) changed.add("dateOfBirth");
        if (!eq(current.getGender(), update.gender())) changed.add("gender");
        if (!eq(current.getBloodGroup(), update.bloodGroup())) changed.add("bloodGroup");
        if (!eq(current.getPhone(), update.phone())) changed.add("phone");
        if (!eq(current.getEmail(), update.email())) changed.add("email");
        if (!eq(current.getAddress(), update.address())) changed.add("address");
        if (!eq(current.getCity(), update.city())) changed.add("city");
        if (!eq(current.getState(), update.state())) changed.add("state");
        if (!eq(current.getZipCode(), update.zipCode())) changed.add("zipCode");
        if (!eq(current.getEmergencyContactName(), update.emergencyContactName())) changed.add("emergencyContactName");
        if (!eq(current.getEmergencyContactPhone(), update.emergencyContactPhone())) changed.add("emergencyContactPhone");
        if (!eq(current.getEmergencyContactRelationship(), update.emergencyContactRelationship())) changed.add("emergencyContactRelationship");
        if (!eq(current.getKnownAllergies(), update.knownAllergies())) changed.add("knownAllergies");
        if (!eq(current.getChronicConditions(), update.chronicConditions())) changed.add("chronicConditions");
        return changed;
    }

    private boolean eq(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
