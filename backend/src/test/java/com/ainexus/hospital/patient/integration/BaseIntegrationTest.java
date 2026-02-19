package com.ainexus.hospital.patient.integration;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

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

    @BeforeEach
    void setUpBaseTest() {
        // Configure Apache HttpClient so TestRestTemplate supports PATCH (HttpURLConnection lacks it)
        restTemplate.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()));

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
