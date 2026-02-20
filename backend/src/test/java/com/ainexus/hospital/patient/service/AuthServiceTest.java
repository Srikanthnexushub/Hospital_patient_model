package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AuthAuditService;
import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.exception.AccountLockedException;
import com.ainexus.hospital.patient.mapper.StaffMapper;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.TokenBlacklistRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private HospitalUserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock private AuthAuditService authAuditService;
    @Mock private StaffMapper staffMapper;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret",
                "test-secret-key-must-be-at-least-32-chars");
        ReflectionTestUtils.setField(authService, "expirationHours", 8);
        ReflectionTestUtils.setField(authService, "lockoutMaxAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 15);
    }

    // ── (a) Successful login returns TokenResponse with correct claims ─────────

    @Test
    void login_validCredentials_returnsTokenResponse() {
        HospitalUser user = activeUser();
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        TokenResponse resp = authService.login(new LoginRequest("doctor1", "secret"), "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.token()).isNotBlank();
        assertThat(resp.userId()).isEqualTo("U2026001");
        assertThat(resp.username()).isEqualTo("doctor1");
        assertThat(resp.role()).isEqualTo("DOCTOR");
        assertThat(resp.expiresAt()).isNotNull();

        verify(authAuditService).writeAuthLog(eq("LOGIN_SUCCESS"), eq("U2026001"),
                isNull(), eq("SUCCESS"), eq("127.0.0.1"), isNull());
    }

    // ── (b) Wrong password increments failedAttempts ──────────────────────────

    @Test
    void login_wrongPassword_incrementsFailedAttempts() {
        HospitalUser user = activeUser();
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("doctor1", "wrongpass"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    // ── (c) 5th failure sets lockedUntil ──────────────────────────────────────

    @Test
    void login_fifthFailure_setsLockedUntil() {
        HospitalUser user = activeUser();
        user.setFailedAttempts(4); // already 4 failures; this is the 5th
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("doctor1", "wrongpass"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository).save(captor.capture());
        HospitalUser saved = captor.getValue();
        assertThat(saved.getFailedAttempts()).isEqualTo(5);
        assertThat(saved.getLockedUntil()).isNotNull();
        assertThat(saved.getLockedUntil()).isAfter(OffsetDateTime.now().plusMinutes(14));
        assertThat(saved.getLockedUntil()).isBefore(OffsetDateTime.now().plusMinutes(16));

        verify(authAuditService).writeAuthLog(eq("ACCOUNT_LOCKED"), eq("U2026001"),
                isNull(), eq("FAILURE"), eq("127.0.0.1"), isNull());
    }

    // ── (d) Login while locked throws AccountLockedException ─────────────────

    @Test
    void login_accountLocked_throwsAccountLockedException() {
        HospitalUser user = activeUser();
        user.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("doctor1", "anypass"), "127.0.0.1"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ── (e) Deactivated account throws BadCredentialsException (not revealing status) ──

    @Test
    void login_inactiveAccount_throwsBadCredentialsException() {
        HospitalUser user = activeUser();
        user.setStatus("INACTIVE");
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("doctor1", "secret"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ── (f) Lockout auto-clears after duration passes ─────────────────────────

    @Test
    void login_lockoutExpired_allowsLoginAgain() {
        HospitalUser user = activeUser();
        // lockedUntil is in the past — lockout expired
        user.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        user.setFailedAttempts(5);
        when(userRepository.findByUsernameIgnoreCase("doctor1"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        TokenResponse resp = authService.login(new LoginRequest("doctor1", "secret"), "127.0.0.1");

        assertThat(resp).isNotNull();
        ArgumentCaptor<HospitalUser> captor = ArgumentCaptor.forClass(HospitalUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    // ── Unknown user returns generic error ────────────────────────────────────

    @Test
    void login_unknownUser_throwsBadCredentialsException() {
        when(userRepository.findByUsernameIgnoreCase("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("unknown", "pass"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    // ── issueToken produces a valid JWT with jti ──────────────────────────────

    @Test
    void issueToken_producesJwtWithAllRequiredClaims() {
        HospitalUser user = activeUser();
        TokenResponse resp = authService.issueToken(user);

        assertThat(resp.token()).isNotBlank();
        // JWT has 3 dot-separated parts
        String[] parts = resp.token().split("\\.");
        assertThat(parts).hasSize(3);

        // Decode payload
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload).contains("\"sub\":\"U2026001\"");
        assertThat(payload).contains("\"username\":\"doctor1\"");
        assertThat(payload).contains("\"role\":\"DOCTOR\"");
        assertThat(payload).contains("\"jti\":");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private HospitalUser activeUser() {
        return HospitalUser.builder()
                .userId("U2026001")
                .username("doctor1")
                .passwordHash("$2a$12$hashedpassword")
                .role("DOCTOR")
                .status("ACTIVE")
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy("SYSTEM")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("SYSTEM")
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // US2 — Token Refresh scenarios (T034)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void refresh_returnsNewTokenWithDifferentJti() {
        // Set up AuthContext (normally done by JwtAuthFilter)
        com.ainexus.hospital.patient.security.AuthContext ctx =
                new com.ainexus.hospital.patient.security.AuthContext("U2026001", "doctor1", "DOCTOR");
        com.ainexus.hospital.patient.security.AuthContext.Holder.set(ctx);

        HospitalUser user = activeUser();
        when(userRepository.findById("U2026001")).thenReturn(java.util.Optional.of(user));

        try {
            TokenResponse resp = authService.refresh("127.0.0.1");

            assertThat(resp).isNotNull();
            assertThat(resp.token()).isNotBlank();
            assertThat(resp.userId()).isEqualTo("U2026001");

            verify(authAuditService).writeAuthLog(eq("TOKEN_REFRESH"), eq("U2026001"),
                    isNull(), eq("SUCCESS"), eq("127.0.0.1"), isNull());
        } finally {
            com.ainexus.hospital.patient.security.AuthContext.Holder.clear();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // US3 — Logout scenarios (T039)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void logout_savesBlacklistEntry() {
        // Build a real JWT to parse
        HospitalUser user = activeUser();
        TokenResponse tokenResp = authService.issueToken(user);

        // Set up AuthContext as JwtAuthFilter would
        com.ainexus.hospital.patient.security.AuthContext ctx =
                new com.ainexus.hospital.patient.security.AuthContext("U2026001", "doctor1", "DOCTOR");
        com.ainexus.hospital.patient.security.AuthContext.Holder.set(ctx);

        try {
            authService.logout(tokenResp.token(), "192.168.1.1");

            verify(tokenBlacklistRepository).save(argThat(entry ->
                    entry.getJti() != null && entry.getUserId().equals("U2026001")
                            && entry.getExpiresAt() != null));
            verify(authAuditService).writeAuthLog(eq("LOGOUT"), eq("U2026001"),
                    isNull(), eq("SUCCESS"), eq("192.168.1.1"), isNull());
        } finally {
            com.ainexus.hospital.patient.security.AuthContext.Holder.clear();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // US4 — getCurrentUser (T044)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void getCurrentUser_returnsProfileWithoutPasswordHash() {
        com.ainexus.hospital.patient.security.AuthContext ctx =
                new com.ainexus.hospital.patient.security.AuthContext("U2026001", "doctor1", "DOCTOR");
        com.ainexus.hospital.patient.security.AuthContext.Holder.set(ctx);

        HospitalUser user = activeUser();
        when(userRepository.findById("U2026001")).thenReturn(java.util.Optional.of(user));

        com.ainexus.hospital.patient.dto.response.UserProfileResponse expected =
                new com.ainexus.hospital.patient.dto.response.UserProfileResponse(
                        "U2026001", "doctor1", "DOCTOR", null, null, null);
        when(staffMapper.toProfileResponse(user)).thenReturn(expected);

        try {
            com.ainexus.hospital.patient.dto.response.UserProfileResponse result =
                    authService.getCurrentUser();

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo("U2026001");
            assertThat(result.username()).isEqualTo("doctor1");
            verify(staffMapper).toProfileResponse(user);
        } finally {
            com.ainexus.hospital.patient.security.AuthContext.Holder.clear();
        }
    }
}
