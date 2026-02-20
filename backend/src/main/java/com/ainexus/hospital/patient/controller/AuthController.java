package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.dto.response.UserProfileResponse;
import com.ainexus.hospital.patient.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * POST /api/v1/auth/login   — issue JWT (public)
 * POST /api/v1/auth/refresh — issue fresh JWT (requires valid token)
 * POST /api/v1/auth/logout  — revoke token (requires valid token)
 * GET  /api/v1/auth/me      — current user profile (requires valid token)
 *
 * All endpoints at /api/v1/auth/** are permitAll() in SecurityConfig;
 * /refresh, /logout, and /me require a valid Bearer token enforced by
 * BlacklistCheckFilter + JwtAuthFilter.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Staff login, token refresh, logout, and profile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/login
     * Authenticates a staff member and issues a JWT.
     */
    @PostMapping("/login")
    @Operation(summary = "Staff login", description = "Authenticate with username and password, receive a JWT")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ipAddress = extractClientIp(httpRequest);
        TokenResponse response = authService.login(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     * Issues a new JWT using the current (non-expired, non-revoked) token.
     * Requires: Authorization: Bearer <valid-token>
     */
    @PostMapping("/refresh")
    @Operation(summary = "Token refresh", description = "Issue a new JWT using the current valid token")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest httpRequest) {
        String ipAddress = extractClientIp(httpRequest);
        TokenResponse response = authService.refresh(ipAddress);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * Revokes the current token by adding its jti to the blacklist.
     * Returns 204 No Content on success.
     * Requires: Authorization: Bearer <valid-token>
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke the current JWT — returns 204 No Content")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        String rawToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : "";
        String ipAddress = extractClientIp(httpRequest);
        authService.logout(rawToken, ipAddress);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/auth/me
     * Returns the authenticated user's own profile.
     * Requires: Authorization: Bearer <valid-token>
     */
    @GetMapping("/me")
    @Operation(summary = "Current user profile", description = "Returns the authenticated user's profile")
    public ResponseEntity<UserProfileResponse> me() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    /**
     * Extracts the client IP address.
     * Uses X-Forwarded-For if present (set by Nginx), falls back to remoteAddr.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; take the first
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
