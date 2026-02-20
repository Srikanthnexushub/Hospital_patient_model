package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US1 — Staff Login (POST /api/v1/auth/login).
 *
 * Tests confirm:
 * - Valid credentials → 200 + valid JWT
 * - Returned token accepted by frozen Patient Module (GET /api/v1/patients)
 * - Wrong password → 401
 * - 5 consecutive failures → 6th attempt returns 423
 * - Locked account with correct password → 423
 * - Deactivated account → 401
 * - Validation errors → 400 with fieldErrors
 */
class AuthIT extends BaseIntegrationTest {

    @Autowired
    private HospitalUserRepository hospitalUserRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpAuthTest() {
        // Clean auth tables in reverse dependency order
        jdbcTemplate.execute("TRUNCATE TABLE auth_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE token_blacklist CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE hospital_users CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE staff_id_sequences CASCADE");
    }

    // ── (a) Valid credentials → 200 + token with correct claims ──────────────

    @Test
    void login_validCredentials_returns200WithToken() {
        seedUser("nurse1", "Nurse@123", "NURSE", "ACTIVE");

        ResponseEntity<TokenResponse> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("nurse1", "Nurse@123"),
                TokenResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).isNotBlank();
        assertThat(body.username()).isEqualTo("nurse1");
        assertThat(body.role()).isEqualTo("NURSE");
        assertThat(body.userId()).startsWith("U");
        assertThat(body.expiresAt()).isNotNull();
    }

    // ── Patient Module compatibility: returned token accepted by GET /api/v1/patients ──

    @Test
    void login_returnedTokenAcceptedByPatientModule() {
        seedUser("receptionist1", "Recep@123", "RECEPTIONIST", "ACTIVE");

        ResponseEntity<TokenResponse> loginResp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("receptionist1", "Recep@123"),
                TokenResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResp.getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> patientsResp = restTemplate.exchange(
                baseUrl("/api/v1/patients"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        // Patient Module accepts the Auth Module JWT
        assertThat(patientsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── (b) Wrong password → 401 with standard error body ────────────────────

    @Test
    void login_wrongPassword_returns401() {
        seedUser("doctor1", "Doctor@123", "DOCTOR", "ACTIVE");

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("doctor1", "WRONG"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).contains("status");
    }

    // ── (c) 5 consecutive failures → 6th attempt returns 423 ─────────────────

    @Test
    void login_fiveConsecutiveFailures_sixthAttemptReturns423() {
        seedUser("admin1", "Admin@123", "ADMIN", "ACTIVE");

        // 5 wrong attempts
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity(
                    baseUrl("/api/v1/auth/login"),
                    loginBody("admin1", "WRONG"),
                    String.class);
        }

        // 6th attempt — should return 423 Locked
        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("admin1", "WRONG"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    }

    // ── (d) Locked account with correct password → 423 not 401 ───────────────

    @Test
    void login_lockedAccountWithCorrectPassword_returns423() {
        HospitalUser user = seedUser("locked1", "Lock@123", "NURSE", "ACTIVE");
        // Force lock
        user.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        user.setFailedAttempts(5);
        hospitalUserRepository.save(user);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("locked1", "Lock@123"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    }

    // ── (e) Deactivated account → 401 ────────────────────────────────────────

    @Test
    void login_deactivatedAccount_returns401() {
        seedUser("inactive1", "Inact@123", "DOCTOR", "INACTIVE");

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("inactive1", "Inact@123"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── (f) Missing username → 400 with fieldErrors ──────────────────────────

    @Test
    void login_missingUsername_returns400WithFieldErrors() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("", "somepass"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("fieldErrors");
    }

    // ── (g) Missing password → 400 with fieldErrors ──────────────────────────

    @Test
    void login_missingPassword_returns400WithFieldErrors() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                loginBody("user1", ""),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("fieldErrors");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpEntity<LoginRequest> loginBody(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new LoginRequest(username, password), headers);
    }

    private HospitalUser seedUser(String username, String rawPassword, String role, String status) {
        // Use a deterministic ID based on a test year to avoid conflicts
        String userId = "U2025" + String.format("%03d", (int) (Math.random() * 900) + 1);
        // Ensure uniqueness by checking
        while (hospitalUserRepository.existsById(userId)) {
            userId = "U2025" + String.format("%03d", (int) (Math.random() * 900) + 1);
        }
        HospitalUser user = HospitalUser.builder()
                .userId(userId)
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(status)
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy("test")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("test")
                .build();
        return hospitalUserRepository.save(user);
    }

    // ════════════════════════════════════════════════════════════════════════
    // US2 — Token Refresh (POST /api/v1/auth/refresh)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void refresh_validToken_returns200WithNewToken() {
        seedUser("refresh1", "Ref@123", "DOCTOR", "ACTIVE");
        String originalToken = loginAndGetToken("refresh1", "Ref@123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(originalToken);
        ResponseEntity<TokenResponse> resp = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                TokenResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).isNotBlank();
        assertThat(body.token()).isNotEqualTo(originalToken);
        assertThat(body.expiresAt()).isNotNull();
    }

    @Test
    void refresh_newTokenAcceptedByPatientModule() {
        seedUser("refresh2", "Ref@123", "RECEPTIONIST", "ACTIVE");
        String originalToken = loginAndGetToken("refresh2", "Ref@123");

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.setBearerAuth(originalToken);
        ResponseEntity<TokenResponse> refreshResp = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(refreshHeaders),
                TokenResponse.class);

        String newToken = refreshResp.getBody().token();

        HttpHeaders patientHeaders = new HttpHeaders();
        patientHeaders.setBearerAuth(newToken);
        ResponseEntity<String> patientsResp = restTemplate.exchange(
                baseUrl("/api/v1/patients"),
                HttpMethod.GET,
                new HttpEntity<>(patientHeaders),
                String.class);

        assertThat(patientsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refresh_noToken_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ════════════════════════════════════════════════════════════════════════
    // US3 — Logout (POST /api/v1/auth/logout)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void logout_validToken_returns204() {
        seedUser("logout1", "Log@123", "NURSE", "ACTIVE");
        String token = loginAndGetToken("logout1", "Log@123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Void> resp = restTemplate.exchange(
                baseUrl("/api/v1/auth/logout"),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_revokedTokenCannotBeUsedAgain() {
        seedUser("logout2", "Log@123", "NURSE", "ACTIVE");
        String token = loginAndGetToken("logout2", "Log@123");

        // Logout
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        restTemplate.exchange(baseUrl("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);

        // Try to use same token after logout → 401
        ResponseEntity<String> meResp = restTemplate.exchange(
                baseUrl("/api/v1/auth/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_revokedTokenCannotRefresh() {
        seedUser("logout3", "Log@123", "ADMIN", "ACTIVE");
        String token = loginAndGetToken("logout3", "Log@123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        restTemplate.exchange(baseUrl("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);

        // Try refresh with revoked token
        ResponseEntity<String> refreshResp = restTemplate.exchange(
                baseUrl("/api/v1/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String loginAndGetToken(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<TokenResponse> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                new HttpEntity<>(new LoginRequest(username, password), headers),
                TokenResponse.class);
        return resp.getBody().token();
    }
}
