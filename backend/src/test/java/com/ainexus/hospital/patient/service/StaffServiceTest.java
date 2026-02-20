package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AuthAuditService;
import com.ainexus.hospital.patient.dto.request.CreateUserRequest;
import com.ainexus.hospital.patient.dto.request.UpdateUserRequest;
import com.ainexus.hospital.patient.dto.response.UserDetailResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.UsernameConflictException;
import com.ainexus.hospital.patient.exception.VersionConflictException;
import com.ainexus.hospital.patient.mapper.StaffMapper;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock private HospitalUserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StaffIdGeneratorService staffIdGeneratorService;
    @Mock private AuthAuditService authAuditService;
    @Mock private StaffMapper staffMapper;

    @InjectMocks
    private StaffService staffService;

    // Use real RoleGuard so ADMIN context works
    private RoleGuard realRoleGuard = new RoleGuard();

    @BeforeEach
    void setUp() {
        // Inject the real RoleGuard
        org.springframework.test.util.ReflectionTestUtils.setField(
                staffService, "roleGuard", realRoleGuard);
        // Set up ADMIN context by default
        AuthContext.Holder.set(new AuthContext("U2026001", "admin", "ADMIN"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.Holder.clear();
    }

    // ── (a) createUser hashes password, generates userId, returns UserDetailResponse ──

    @Test
    void createUser_happyPath_savesAndReturnsDetail() {
        when(userRepository.existsByUsernameIgnoreCase("newdoc")).thenReturn(false);
        when(staffIdGeneratorService.generateStaffId()).thenReturn("U2026002");
        when(passwordEncoder.encode("Doctor@123")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse expected = new UserDetailResponse(
                "U2026002", "newdoc", "DOCTOR", null, null,
                "ACTIVE", null, OffsetDateTime.now(), "admin", 0);
        when(staffMapper.toDetailResponse(any())).thenReturn(expected);

        CreateUserRequest req = new CreateUserRequest("newdoc", "Doctor@123", "DOCTOR", null, null);
        UserDetailResponse result = staffService.createUser(req, "admin");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo("U2026002");

        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);
        verify(userRepository).save(captor.capture());
        HospitalUser saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(saved.getUserId()).isEqualTo("U2026002");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    // ── (b) createUser with duplicate username throws UsernameConflictException ──

    @Test
    void createUser_duplicateUsername_throwsUsernameConflictException() {
        when(userRepository.existsByUsernameIgnoreCase("existing")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest("existing", "Doctor@123", "DOCTOR", null, null);

        assertThatThrownBy(() -> staffService.createUser(req, "admin"))
                .isInstanceOf(UsernameConflictException.class)
                .hasMessageContaining("existing");
    }

    // ── (c) updateUser with matching version updates fields ──────────────────

    @Test
    void updateUser_matchingVersion_updatesFields() {
        HospitalUser user = activeUser("U2026003", 5);
        when(userRepository.findById("U2026003")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(staffMapper.toDetailResponse(any())).thenReturn(mockDetailResponse("U2026003"));

        UpdateUserRequest req = new UpdateUserRequest("new@hospital.local", "Cardiology", null);
        staffService.updateUser("U2026003", req, 5, "admin");

        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@hospital.local");
        assertThat(captor.getValue().getDepartment()).isEqualTo("Cardiology");
    }

    // ── (d) updateUser with wrong version throws VersionConflictException ────

    @Test
    void updateUser_wrongVersion_throwsVersionConflictException() {
        HospitalUser user = activeUser("U2026003", 5);
        when(userRepository.findById("U2026003")).thenReturn(Optional.of(user));

        UpdateUserRequest req = new UpdateUserRequest(null, "Cardiology", null);

        assertThatThrownBy(() -> staffService.updateUser("U2026003", req, 99, "admin"))
                .isInstanceOf(VersionConflictException.class);
    }

    // ── (e) deactivateUser sets status INACTIVE ───────────────────────────────

    @Test
    void deactivateUser_activeUser_setsInactive() {
        HospitalUser user = activeUser("U2026004", 0);
        when(userRepository.findById("U2026004")).thenReturn(Optional.of(user));

        staffService.deactivateUser("U2026004", "U2026001");

        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
    }

    // ── (f) ADMIN cannot deactivate their own account → ForbiddenException ───

    @Test
    void deactivateUser_selfDeactivation_throwsForbiddenException() {
        assertThatThrownBy(() -> staffService.deactivateUser("U2026001", "U2026001"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own account");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HospitalUser activeUser(String userId, Integer version) {
        HospitalUser user = HospitalUser.builder()
                .userId(userId)
                .username("user_" + userId)
                .passwordHash("$2a$12$hashed")
                .role("DOCTOR")
                .status("ACTIVE")
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy("SYSTEM")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("SYSTEM")
                .build();
        user.setVersion(version);
        return user;
    }

    private UserDetailResponse mockDetailResponse(String userId) {
        return new UserDetailResponse(userId, "user_" + userId, "DOCTOR",
                null, null, "ACTIVE", null, OffsetDateTime.now(), "admin", 0);
    }
}
