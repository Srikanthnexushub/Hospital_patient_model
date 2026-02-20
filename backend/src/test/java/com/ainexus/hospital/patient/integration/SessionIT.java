package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.dto.response.UserProfileResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US4 — Current User Profile (GET /api/v1/auth/me).
 */
class SessionIT extends BaseIntegrationTest {

    @Autowired
    private HospitalUserRepository hospitalUserRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpSessionTest() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE token_blacklist CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE hospital_users CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE staff_id_sequences CASCADE");
    }

    // ── (a) Valid token → 200 UserProfileResponse with all fields ─────────────

    @Test
    void me_validToken_returns200WithProfile() {
        HospitalUser user = seedUser("meuser1", "Pass@123", "NURSE", "ACTIVE");
        String token = loginAndGetToken("meuser1", "Pass@123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<UserProfileResponse> resp = restTemplate.exchange(
                baseUrl("/api/v1/auth/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserProfileResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.userId()).isEqualTo(user.getUserId());
        assertThat(body.username()).isEqualTo("meuser1");
        assertThat(body.role()).isEqualTo("NURSE");
    }

    // ── (b) passwordHash must NOT appear in response body ─────────────────────

    @Test
    void me_responseDoesNotContainPasswordHash() {
        seedUser("meuser2", "Pass@123", "DOCTOR", "ACTIVE");
        String token = loginAndGetToken("meuser2", "Pass@123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/auth/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("passwordHash");
        assertThat(resp.getBody()).doesNotContain("password_hash");
        assertThat(resp.getBody()).doesNotContain("$2a$");
    }

    // ── (c) No token → 401 ────────────────────────────────────────────────────

    @Test
    void me_noToken_returns401() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl("/api/v1/auth/me"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<TokenResponse> resp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                new HttpEntity<>(new LoginRequest(username, password), headers),
                TokenResponse.class);
        return resp.getBody().token();
    }

    private HospitalUser seedUser(String username, String rawPassword, String role, String status) {
        HospitalUser user = HospitalUser.builder()
                .userId("U2025" + String.format("%03d", (int)(Math.random() * 800) + 100))
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
}
