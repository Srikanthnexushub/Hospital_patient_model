package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests.
 *
 * Uses a shared Testcontainers PostgreSQL 15 container (started once per test run,
 * reused across all IT classes). Flyway migrations run automatically on container startup.
 *
 * Each test method starts with a clean database state (@BeforeEach truncates tables).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("hospital_patients_test")
                    .withUsername("test_user")
                    .withPassword("test_pass")
                    .withReuse(true);

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

    @BeforeEach
    void cleanDatabase() {
        // Clean in reverse FK dependency order
        jdbcTemplate.execute("TRUNCATE TABLE patient_audit_log RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE patient_id_sequences CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE patients CASCADE");
    }

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Generates a test JWT signed with the same secret configured in application-test.yml.
     * The secret matches the value used by JwtAuthFilter in the test Spring context.
     */
    protected String buildTestJwt(String role) {
        return io.jsonwebtoken.Jwts.builder()
                .subject("test-user-" + role.toLowerCase())
                .claim("username", role.toLowerCase() + "1")
                .claim("role", role)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-secret-key-must-be-at-least-32-chars".getBytes()))
                .compact();
    }
}
