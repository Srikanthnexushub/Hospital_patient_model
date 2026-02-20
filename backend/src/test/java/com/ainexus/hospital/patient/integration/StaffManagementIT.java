package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.request.CreateUserRequest;
import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.request.UpdateUserRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.dto.response.UserDetailResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US5 — Staff Account Management (/api/v1/admin/users).
 * All operations require ADMIN role.
 */
class StaffManagementIT extends BaseIntegrationTest {

    @Autowired
    private HospitalUserRepository hospitalUserRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUpStaffTest() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE token_blacklist CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE hospital_users CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE staff_id_sequences CASCADE");

        // Seed an ADMIN user and get its token
        seedUser("testadmin", "Admin@123", "ADMIN");
        adminToken = loginAndGetToken("testadmin", "Admin@123");
    }

    // ── (a) POST creates user + 201 + Location header ─────────────────────────

    @Test
    void createUser_validRequest_returns201WithLocation() {
        CreateUserRequest req = new CreateUserRequest(
                "newdoctor", "Doctor@123", "DOCTOR", "doc@hospital.local", "Cardiology");

        ResponseEntity<UserDetailResponse> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"),
                HttpMethod.POST,
                new HttpEntity<>(req, adminHeaders()),
                UserDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        UserDetailResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.username()).isEqualTo("newdoctor");
        assertThat(body.role()).isEqualTo("DOCTOR");
        assertThat(body.status()).isEqualTo("ACTIVE");
    }

    // ── (b) POST duplicate username → 409 ─────────────────────────────────────

    @Test
    void createUser_duplicateUsername_returns409() {
        seedUser("existing1", "Pass@123", "NURSE");

        CreateUserRequest req = new CreateUserRequest(
                "existing1", "Doctor@123", "DOCTOR", null, null);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users"),
                HttpMethod.POST,
                new HttpEntity<>(req, adminHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── (c) GET list → paginated response ─────────────────────────────────────

    @Test
    void listUsers_returns200WithPaginatedResponse() {
        seedUser("doc1", "Doc@123", "DOCTOR");
        seedUser("nurse1", "Nur@123", "NURSE");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("content");
    }

    // ── (d) GET list with ?role=DOCTOR → filtered ─────────────────────────────

    @Test
    void listUsers_withRoleFilter_returnsFilteredResults() {
        seedUser("doc2", "Doc@123", "DOCTOR");
        seedUser("nurse2", "Nur@123", "NURSE");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users?role=DOCTOR"),
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("DOCTOR");
    }

    // ── (e) GET /{userId} → UserDetailResponse ─────────────────────────────────

    @Test
    void getUser_existingUser_returnsDetail() {
        HospitalUser user = seedUser("detail1", "Det@123", "RECEPTIONIST");

        ResponseEntity<UserDetailResponse> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + user.getUserId()),
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                UserDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().username()).isEqualTo("detail1");
    }

    // ── (f) GET /{userId} not found → 404 ─────────────────────────────────────

    @Test
    void getUser_notFound_returns404() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/U9999999"),
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── (g) PATCH updates fields ───────────────────────────────────────────────

    @Test
    void updateUser_validPatch_updates200() {
        HospitalUser user = seedUser("patch1", "Pat@123", "NURSE");

        UpdateUserRequest req = new UpdateUserRequest(null, "Pediatrics", null);
        HttpHeaders headers = adminHeaders();
        headers.set("If-Match", String.valueOf(user.getVersion() != null ? user.getVersion() : 0));

        ResponseEntity<UserDetailResponse> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + user.getUserId()),
                HttpMethod.PATCH,
                new HttpEntity<>(req, headers),
                UserDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().department()).isEqualTo("Pediatrics");
    }

    // ── (i) DELETE deactivates → 204 ──────────────────────────────────────────

    @Test
    void deactivateUser_returns204() {
        HospitalUser user = seedUser("del1", "Del@123", "RECEPTIONIST");

        ResponseEntity<Void> resp = restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + user.getUserId()),
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── (j) Deactivated user cannot login → 401 ───────────────────────────────

    @Test
    void deactivatedUser_cannotLogin() {
        HospitalUser user = seedUser("del2", "Del@123", "DOCTOR");

        // Deactivate the user
        restTemplate.exchange(
                baseUrl("/api/v1/admin/users/" + user.getUserId()),
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        // Try to login
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginResp = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"),
                new HttpEntity<>(new LoginRequest("del2", "Del@123"), headers),
                String.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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

    private HospitalUser seedUser(String username, String rawPassword, String role) {
        HospitalUser user = HospitalUser.builder()
                .userId("U2025" + String.format("%03d", (int)(Math.random() * 800) + 100))
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
