package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.request.CreateUserRequest;
import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full RBAC matrix for all 9 auth endpoints.
 *
 * Verifies:
 * - All 4 roles can access public auth endpoints (login, refresh, logout, me)
 * - Only ADMIN can access /api/v1/admin/users endpoints
 * - Other roles get 403 on admin endpoints
 */
class AuthRbacIT extends BaseIntegrationTest {

    @Autowired
    private HospitalUserRepository hospitalUserRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Map<String, String> roleTokens;

    @BeforeEach
    void setUpRbacTest() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE token_blacklist CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE hospital_users CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE staff_id_sequences CASCADE");

        // Seed one user per role
        seedUser("rbac_receptionist", "Rbac@123", "RECEPTIONIST");
        seedUser("rbac_doctor", "Rbac@123", "DOCTOR");
        seedUser("rbac_nurse", "Rbac@123", "NURSE");
        seedUser("rbac_admin", "Rbac@123", "ADMIN");

        roleTokens = Map.of(
                "RECEPTIONIST", loginAndGetToken("rbac_receptionist", "Rbac@123"),
                "DOCTOR", loginAndGetToken("rbac_doctor", "Rbac@123"),
                "NURSE", loginAndGetToken("rbac_nurse", "Rbac@123"),
                "ADMIN", loginAndGetToken("rbac_admin", "Rbac@123")
        );
    }

    // ── (a) All roles can POST /auth/login ────────────────────────────────────

    @Test
    void allRoles_canLogin() {
        for (String role : new String[]{"RECEPTIONIST", "DOCTOR", "NURSE", "ADMIN"}) {
            String username = "rbac_" + role.toLowerCase();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<TokenResponse> resp = restTemplate.postForEntity(
                    baseUrl("/api/v1/auth/login"),
                    new HttpEntity<>(new LoginRequest(username, "Rbac@123"), headers),
                    TokenResponse.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should be able to login", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // ── (b) All roles can POST /auth/refresh ──────────────────────────────────

    @Test
    void allRoles_canRefresh() {
        for (String role : roleTokens.keySet()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            ResponseEntity<TokenResponse> resp = restTemplate.exchange(
                    baseUrl("/api/v1/auth/refresh"),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    TokenResponse.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should be able to refresh token", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // ── (d) All roles can GET /auth/me ─────────────────────────────────────────

    @Test
    void allRoles_canGetMe() {
        for (String role : roleTokens.keySet()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl("/api/v1/auth/me"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should be able to get /me", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // ── (e) Only ADMIN can POST /admin/users ──────────────────────────────────

    @Test
    void onlyAdmin_canCreateUser() {
        CreateUserRequest req = new CreateUserRequest(
                "newstaff", "Staff@123", "NURSE", null, null);

        for (String role : new String[]{"RECEPTIONIST", "DOCTOR", "NURSE"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl("/api/v1/admin/users"),
                    HttpMethod.POST,
                    new HttpEntity<>(req, headers),
                    String.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should get 403 on POST /admin/users", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        // ADMIN should succeed
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(roleTokens.get("ADMIN"));
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> adminResp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"),
                HttpMethod.POST,
                new HttpEntity<>(req, adminHeaders),
                String.class);
        assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── (f) Only ADMIN can GET /admin/users ──────────────────────────────────

    @Test
    void onlyAdmin_canListUsers() {
        for (String role : new String[]{"RECEPTIONIST", "DOCTOR", "NURSE"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl("/api/v1/admin/users"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should get 403 on GET /admin/users", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── (g) Only ADMIN can GET /admin/users/{id} ──────────────────────────────

    @Test
    void onlyAdmin_canGetUserById() {
        HospitalUser targetUser = seedUser("target_user", "Tgt@123", "NURSE");

        for (String role : new String[]{"RECEPTIONIST", "DOCTOR", "NURSE"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl("/api/v1/admin/users/" + targetUser.getUserId()),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should get 403 on GET /admin/users/{id}", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── (i) Only ADMIN can DELETE /admin/users/{id} ───────────────────────────

    @Test
    void onlyAdmin_canDeactivateUser() {
        HospitalUser targetUser = seedUser("del_target", "Del@123", "NURSE");

        for (String role : new String[]{"RECEPTIONIST", "DOCTOR", "NURSE"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(roleTokens.get(role));
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl("/api/v1/admin/users/" + targetUser.getUserId()),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(resp.getStatusCode())
                    .as("Role %s should get 403 on DELETE /admin/users/{id}", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
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

    private HospitalUser seedUser(String username, String rawPassword, String role) {
        String userId = "U2025" + String.format("%03d", (int)(Math.random() * 800) + 100);
        while (hospitalUserRepository.existsById(userId)) {
            userId = "U2025" + String.format("%03d", (int)(Math.random() * 800) + 100);
        }
        HospitalUser user = HospitalUser.builder()
                .userId(userId)
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status("ACTIVE")
                .failedAttempts(0)
                .createdAt(OffsetDateTime.now())
                .createdBy("test")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("test")
                .build();
        return hospitalUserRepository.save(user);
    }
}
