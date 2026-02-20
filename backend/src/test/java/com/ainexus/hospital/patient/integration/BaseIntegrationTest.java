package com.ainexus.hospital.patient.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Base class for all integration tests.
 *
 * Uses a singleton Testcontainers PostgreSQL 15 container started once via a static
 * initializer block. This guarantees a single fixed port for the entire JVM, so the
 * cached Spring ApplicationContext is never invalidated between IT classes.
 * Flyway migrations run automatically on first container startup.
 *
 * Each test method starts with a clean database state (@BeforeEach truncates tables).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("hospital_patients_test")
                .withUsername("test_user")
                .withPassword("test_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUpBaseTest() {
        // Configure Apache HttpClient so TestRestTemplate supports PATCH (HttpURLConnection lacks it)
        restTemplate.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()));

        // Clean in reverse FK dependency order
        // Module 4 billing tables (dependent on appointments and patients)
        jdbcTemplate.execute("TRUNCATE TABLE invoice_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE invoice_payments RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE invoice_line_items RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE invoice_id_sequences CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE invoices CASCADE");

        // Module 3 tables (dependent on appointments and patients)
        jdbcTemplate.execute("TRUNCATE TABLE clinical_notes CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE appointment_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE appointment_id_sequences CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE appointments CASCADE");

        // Module 1 / Module 2 tables
        jdbcTemplate.execute("TRUNCATE TABLE auth_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE token_blacklist CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE hospital_users CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE patient_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE patient_id_sequences CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE patients CASCADE");
    }

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Generates a test JWT.
     * For DOCTOR role, userId = "U2025001" (matches seedDoctor userId).
     * For other roles, userId = "{role.lowercase}001" (short, fits VARCHAR(12)).
     */
    protected String buildTestJwt(String role) {
        String userId = "DOCTOR".equals(role) ? "U2025001" : role.toLowerCase().substring(0, Math.min(4, role.length())) + "001";
        String username = role.toLowerCase() + "1";
        return buildTestJwtWithUserId(role, userId, username);
    }

    /**
     * Generates a test JWT with explicit userId and username.
     * Use this when the JWT userId must match a seeded hospital_users row.
     */
    protected String buildTestJwtWithUserId(String role, String userId, String username) {
        return io.jsonwebtoken.Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-secret-key-must-be-at-least-32-chars".getBytes()))
                .compact();
    }

    // ── Module 3 seed helpers ─────────────────────────────────────────────────

    /**
     * Seeds an ACTIVE doctor into hospital_users.
     * userId = "U2025001" — matches buildTestJwt("DOCTOR") subject.
     */
    protected String seedDoctor(String username) {
        return seedDoctorWithId("U2025001", username);
    }

    /**
     * Seeds an ACTIVE doctor with a specific userId (for multi-doctor tests).
     * userId must be ≤ 12 chars to fit VARCHAR(12) PK.
     */
    protected String seedDoctorWithId(String userId, String username) {
        jdbcTemplate.update("""
                INSERT INTO hospital_users
                  (user_id, username, password_hash, role, status, version, created_at, updated_at)
                VALUES (?, ?, '$2a$10$placeholder', 'DOCTOR', 'ACTIVE', 0, NOW(), NOW())
                ON CONFLICT (user_id) DO NOTHING
                """, userId, username);
        return userId;
    }

    /**
     * Seeds an ACTIVE patient with a P2025xxx-style ID.
     * Uses year 2025 so ID generator (producing P2026xxx) won't conflict.
     */
    protected String seedPatient(String firstName, String lastName) {
        // Generate a stable ID based on name hash to avoid collisions in the same test
        int hash = Math.abs((firstName + lastName).hashCode()) % 900 + 1;
        String patientId = String.format("P2025%03d", hash);
        jdbcTemplate.update("""
                INSERT INTO patients
                  (patient_id, first_name, last_name, date_of_birth, gender, phone,
                   blood_group, status, version, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, '1990-01-01', 'MALE', '9999999999', 'A_POS', 'ACTIVE', 0, NOW(), NOW(), 'test', 'test')
                ON CONFLICT (patient_id) DO NOTHING
                """, patientId, firstName, lastName);
        return patientId;
    }

    /**
     * Books a 30-minute appointment via REST and returns the appointmentId.
     * Uses RECEPTIONIST token.
     */
    protected String bookAppointment(String patientId, String doctorId, LocalDate date, LocalTime startTime) {
        return bookAppointment(patientId, doctorId, date, startTime, 30);
    }

    /**
     * Books an appointment with the given duration via REST and returns the appointmentId.
     * Uses RECEPTIONIST token.
     */
    protected String bookAppointment(String patientId, String doctorId, LocalDate date, LocalTime startTime, int durationMinutes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buildTestJwt("RECEPTIONIST"));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "appointmentDate", date.toString(),
                "startTime", startTime.toString(),
                "durationMinutes", durationMinutes,
                "type", "GENERAL_CONSULTATION",
                "reason", "Test appointment"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        return (String) response.getBody().get("appointmentId");
    }

    protected HttpHeaders authHeaders(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buildTestJwt(role));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders authHeadersForUser(String role, String userId, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buildTestJwtWithUserId(role, userId, username));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── Module 4 seed helpers ─────────────────────────────────────────────────

    /**
     * Seeds a COMPLETED appointment directly in the DB and returns its ID.
     * Uses a 2025 appointment ID to avoid conflicts with the ID generator (APT2026xxx).
     * doctorId should be the userId of a seeded doctor (e.g. "U2025001").
     */
    protected String seedAppointment(String patientId, String doctorId, LocalDate date) {
        int hash = Math.abs((patientId + doctorId + date).hashCode()) % 9000 + 1000;
        String apptId = "APT2025" + String.format("%04d", hash % 10000);
        jdbcTemplate.update("""
                INSERT INTO appointments
                  (appointment_id, patient_id, doctor_id, appointment_date, start_time, end_time,
                   duration_minutes, type, status, reason, version, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, '09:00', '09:30', 30, 'GENERAL_CONSULTATION', 'COMPLETED', 'Test', 0, NOW(), NOW(), 'test', 'test')
                ON CONFLICT (appointment_id) DO NOTHING
                """, apptId, patientId, doctorId, date);
        return apptId;
    }

    /**
     * Creates an invoice via REST (RECEPTIONIST token) and returns the invoiceId.
     * The appointment must already exist in the DB.
     */
    protected String createInvoice(String appointmentId, double unitPrice) {
        Map<String, Object> body = Map.of(
                "appointmentId", appointmentId,
                "lineItems", List.of(Map.of(
                        "description", "Test Service",
                        "quantity", 1,
                        "unitPrice", unitPrice
                ))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")),
                Map.class);
        return (String) resp.getBody().get("invoiceId");
    }

    /**
     * Directly updates invoice status in the DB to the given status string.
     * Useful for setting up test preconditions without going through the API.
     */
    protected void setInvoiceStatus(String invoiceId, String status) {
        jdbcTemplate.update("UPDATE invoices SET status = ?, updated_at = NOW() WHERE invoice_id = ?",
                status, invoiceId);
    }
}
