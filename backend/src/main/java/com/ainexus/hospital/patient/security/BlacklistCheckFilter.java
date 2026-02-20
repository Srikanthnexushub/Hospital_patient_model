package com.ainexus.hospital.patient.security;

import com.ainexus.hospital.patient.repository.TokenBlacklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Filter that checks every incoming Bearer token against the token_blacklist table.
 * Registered BEFORE JwtAuthFilter so revoked tokens are rejected before signature
 * verification runs (AD-002).
 *
 * The jti claim is extracted by Base64-decoding the JWT payload segment —
 * no signature re-verification here; JwtAuthFilter handles that downstream.
 *
 * Skip list (same as JwtAuthFilter):
 *   /actuator/health, /api/v1/auth/login, /swagger-ui, /api-docs
 *
 * Note: NEVER add @CircuitBreaker to this class — CGLIB proxy breaks
 * GenericFilterBean.logger field injection (known Resilience4j + Spring issue).
 */
@Component
public class BlacklistCheckFilter extends OncePerRequestFilter {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    public BlacklistCheckFilter(TokenBlacklistRepository tokenBlacklistRepository) {
        this.tokenBlacklistRepository = tokenBlacklistRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip public paths — no token to check
        if (isSkippedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token present — let JwtAuthFilter handle the 401
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(7);
        String jti = extractJti(rawToken);

        if (jti != null && tokenBlacklistRepository.existsById(jti)) {
            sendUnauthorized(response, "Token has been revoked.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the jti claim from the JWT payload by Base64url-decoding the middle segment.
     * Returns null if the token is malformed or the jti claim is absent.
     */
    private String extractJti(String rawToken) {
        try {
            String[] parts = rawToken.split("\\.");
            if (parts.length < 2) return null;

            byte[] decodedBytes = Base64.getUrlDecoder().decode(
                    // Pad with '=' if needed
                    padBase64(parts[1]));
            String payload = new String(decodedBytes, StandardCharsets.UTF_8);

            // Parse jti from JSON payload
            String key = "\"jti\":\"";
            int idx = payload.indexOf(key);
            if (idx < 0) return null;
            int start = idx + key.length();
            int end = payload.indexOf('"', start);
            if (end < 0) return null;
            return payload.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String padBase64(String base64) {
        int padding = (4 - base64.length() % 4) % 4;
        return base64 + "=".repeat(padding);
    }

    private boolean isSkippedPath(String path) {
        return path.startsWith("/actuator/health")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}".formatted(message));
    }
}
