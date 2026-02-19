package com.ainexus.hospital.patient.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Development-only authentication endpoint.
 * Issues JWTs for predefined test users so the frontend can operate without
 * a separate Auth Module.
 *
 * All users share the password "password".
 * Remove or gate behind a feature flag before production deployment.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class DevAuthController {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private static final Map<String, UserInfo> DEV_USERS = Map.of(
            "receptionist1", new UserInfo("U001", "receptionist1", "RECEPTIONIST"),
            "admin1",        new UserInfo("U002", "admin1",        "ADMIN"),
            "doctor1",       new UserInfo("U003", "doctor1",       "DOCTOR"),
            "nurse1",        new UserInfo("U004", "nurse1",        "NURSE")
    );

    @PostMapping("/dev-login")
    public ResponseEntity<?> devLogin(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username and password are required."));
        }

        UserInfo user = DEV_USERS.get(request.username());
        if (user == null || !"password".equals(request.password())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials."));
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();
        long eightHours = 8L * 60 * 60 * 1000;

        String token = Jwts.builder()
                .subject(user.userId())
                .claim("username", user.username())
                .claim("role", user.role())
                .issuedAt(new Date(now))
                .expiration(new Date(now + eightHours))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        return ResponseEntity.ok(Map.of(
                "token",    token,
                "username", user.username(),
                "role",     user.role(),
                "userId",   user.userId()
        ));
    }

    record LoginRequest(String username, String password) {}

    record UserInfo(String userId, String username, String role) {}
}
