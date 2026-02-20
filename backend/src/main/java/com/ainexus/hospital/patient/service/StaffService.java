package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AuthAuditService;
import com.ainexus.hospital.patient.dto.request.CreateUserRequest;
import com.ainexus.hospital.patient.dto.request.UpdateUserRequest;
import com.ainexus.hospital.patient.dto.response.UserDetailResponse;
import com.ainexus.hospital.patient.dto.response.UserSummaryResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.exception.UsernameConflictException;
import com.ainexus.hospital.patient.exception.VersionConflictException;
import com.ainexus.hospital.patient.mapper.StaffMapper;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff account management service (ADMIN only).
 *
 * All methods enforce ADMIN role via RoleGuard.requireRoles("ADMIN").
 * Passwords are hashed before storage; raw passwords are never logged or returned.
 */
@Service
@Transactional
public class StaffService {

    private final HospitalUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffIdGeneratorService staffIdGeneratorService;
    private final AuthAuditService authAuditService;
    private final StaffMapper staffMapper;
    private final RoleGuard roleGuard;

    public StaffService(HospitalUserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        StaffIdGeneratorService staffIdGeneratorService,
                        AuthAuditService authAuditService,
                        StaffMapper staffMapper,
                        RoleGuard roleGuard) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffIdGeneratorService = staffIdGeneratorService;
        this.authAuditService = authAuditService;
        this.staffMapper = staffMapper;
        this.roleGuard = roleGuard;
    }

    /**
     * Creates a new staff account.
     *
     * @param req               validated creation request
     * @param createdByUsername username of the ADMIN creating the account
     * @return UserDetailResponse for the new account
     */
    public UserDetailResponse createUser(CreateUserRequest req, String createdByUsername) {
        roleGuard.requireRoles("ADMIN");

        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new UsernameConflictException(req.username());
        }

        validatePasswordPolicy(req.password());

        String userId = staffIdGeneratorService.generateStaffId();
        String hashedPassword = passwordEncoder.encode(req.password());

        HospitalUser user = HospitalUser.builder()
                .userId(userId)
                .username(req.username())
                .passwordHash(hashedPassword)
                .role(req.role())
                .email(req.email() != null ? req.email().toLowerCase() : null)
                .department(req.department())
                .status("ACTIVE")
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy(createdByUsername)
                .updatedAt(OffsetDateTime.now())
                .updatedBy(createdByUsername)
                .build();

        HospitalUser saved = userRepository.save(user);

        authAuditService.writeAuthLog("USER_CREATED",
                AuthContext.Holder.get().getUserId(),
                saved.getUserId(),
                "SUCCESS", null, null);

        return staffMapper.toDetailResponse(saved);
    }

    /**
     * Lists staff accounts with optional role and status filters.
     */
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(String role, String status, Pageable pageable) {
        roleGuard.requireRoles("ADMIN");

        Page<HospitalUser> page;
        if (role != null && status != null) {
            page = userRepository.findByStatusAndRole(status, role, pageable);
        } else if (role != null) {
            page = userRepository.findByRole(role, pageable);
        } else if (status != null) {
            page = userRepository.findByStatus(status, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        return page.map(staffMapper::toSummaryResponse);
    }

    /**
     * Retrieves a single staff account by userId.
     *
     * @throws ResourceNotFoundException if userId is not found
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUserById(String userId) {
        roleGuard.requireRoles("ADMIN");

        HospitalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
        return staffMapper.toDetailResponse(user);
    }

    /**
     * Applies PATCH updates to a staff account.
     * Version check prevents concurrent modification.
     *
     * @param userId            target user ID
     * @param req               nullable-field update request
     * @param version           client-supplied version for optimistic locking
     * @param updatedByUsername username of the ADMIN making the change
     * @return updated UserDetailResponse
     */
    public UserDetailResponse updateUser(String userId,
                                         UpdateUserRequest req,
                                         Integer version,
                                         String updatedByUsername) {
        roleGuard.requireRoles("ADMIN");

        HospitalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));

        if (!user.getVersion().equals(version)) {
            throw new VersionConflictException(
                    "User record was modified by another request. Please reload and try again.");
        }

        // Track which fields changed for audit (names only, not values)
        List<String> changedFields = new ArrayList<>();

        if (req.email() != null) {
            user.setEmail(req.email().toLowerCase());
            changedFields.add("email");
        }
        if (req.department() != null) {
            user.setDepartment(req.department());
            changedFields.add("department");
        }
        if (req.role() != null) {
            user.setRole(req.role());
            changedFields.add("role");
        }

        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(updatedByUsername);

        HospitalUser saved = userRepository.save(user);

        authAuditService.writeAuthLog("USER_UPDATED",
                AuthContext.Holder.get().getUserId(),
                saved.getUserId(),
                "SUCCESS", null,
                changedFields.isEmpty() ? "no changes" : "fields: " + String.join(",", changedFields));

        return staffMapper.toDetailResponse(saved);
    }

    /**
     * Deactivates (soft-deletes) a staff account.
     * Idempotent: no-ops if account is already INACTIVE.
     * ADMIN cannot deactivate their own account.
     */
    public void deactivateUser(String userId, String requestingUserId) {
        roleGuard.requireRoles("ADMIN");

        if (userId.equals(requestingUserId)) {
            throw new ForbiddenException(
                    "An administrator cannot deactivate their own account.");
        }

        HospitalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));

        if ("INACTIVE".equals(user.getStatus())) {
            return; // idempotent
        }

        user.setStatus("INACTIVE");
        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(requestingUserId);
        userRepository.save(user);

        authAuditService.writeAuthLog("USER_DEACTIVATED",
                requestingUserId,
                userId,
                "SUCCESS", null, null);
    }

    /**
     * Validates password meets policy: min 8 chars, 1 uppercase, 1 lowercase, 1 digit.
     */
    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter, one lowercase letter, and one digit.");
        }
    }
}
