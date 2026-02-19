package com.ainexus.hospital.patient.security;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip auth for health endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, traceId);
                return;
            }

            String token = authHeader.substring(7);
            AuthContext ctx = parseToken(token);
            if (ctx == null) {
                sendUnauthorized(response, traceId);
                return;
            }

            AuthContext.Holder.set(ctx);
            MDC.put("userId", ctx.getUserId());

            filterChain.doFilter(request, response);
        } finally {
            AuthContext.Holder.clear();
            MDC.clear();
        }
    }

    @CircuitBreaker(name = "authModule", fallbackMethod = "authFallback")
    private AuthContext parseToken(String token) {
        try {
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            if (userId == null || username == null || role == null) return null;
            return new AuthContext(userId, username, role);
        } catch (JwtException e) {
            return null;
        }
    }

    private AuthContext authFallback(String token, Exception ex) {
        // Circuit breaker is OPEN — Auth Module unavailable
        return null; // Will result in 401; caller handles 503 via flag
    }

    private void sendUnauthorized(HttpServletResponse response, String traceId) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":401,"error":"Unauthorized","message":"Authentication required.","traceId":"%s"}
                """.formatted(traceId));
    }
}
