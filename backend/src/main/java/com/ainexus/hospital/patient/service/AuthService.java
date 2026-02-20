package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AuthAuditService;
import com.ainexus.hospital.patient.dto.request.LoginRequest;
import com.ainexus.hospital.patient.dto.response.TokenResponse;
import com.ainexus.hospital.patient.dto.response.UserProfileResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import com.ainexus.hospital.patient.entity.TokenBlacklist;
import com.ainexus.hospital.patient.exception.AccountLockedException;
import com.ainexus.hospital.patient.mapper.StaffMapper;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.TokenBlacklistRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Core authentication service.
 *
 * Handles login (BCrypt verify, lockout, JWT issuance, audit),
 * token refresh, logout (blacklist), and current-user profile.
 *
 * HIPAA: never logs passwords, tokens, or PHI in any code path.
 */
@Service
@Transactional
public class AuthService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.expiration-hours:8}")
    private int expirationHours;

    @Value("${app.auth.lockout.max-attempts:5}")
    private int lockoutMaxAttempts;

    @Value("${app.auth.lockout.duration-minutes:15}")
    private int lockoutDurationMinutes;

    private final HospitalUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final AuthAuditService authAuditService;
    private final StaffMapper staffMapper;

    public AuthService(HospitalUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenBlacklistRepository tokenBlacklistRepository,
                       AuthAuditService authAuditService,
                       StaffMapper staffMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
        this.authAuditService = authAuditService;
        this.staffMapper = staffMapper;
    }

    /**
     * Authenticates a staff member and issues a JWT.
     *
     * noRollbackFor ensures that failed-attempt counter increments and lockout writes
     * are committed even when a BadCredentialsException is thrown (which is a RuntimeException
     * and would otherwise trigger a transaction rollback).
     *
     * @param request   login credentials
     * @param ipAddress client IP (from X-Forwarded-For or remoteAddr)
     * @return TokenResponse with signed JWT and metadata
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public TokenResponse login(LoginRequest request, String ipAddress) {
        // 1. Find user — generic message to prevent username enumeration
        HospitalUser user = userRepository
                .findByUsernameIgnoreCase(request.username())
                .orElse(null);

        if (user == null) {
            authAuditService.writeAuthLog("LOGIN_FAILURE", "UNKNOWN", null,
                    "FAILURE", ipAddress, null);
            throw new BadCredentialsException("Invalid username or password");
        }

        // 2. Check account is active
        if (!user.isActive()) {
            authAuditService.writeAuthLog("LOGIN_FAILURE", user.getUserId(), null,
                    "FAILURE", ipAddress, "Account inactive");
            throw new BadCredentialsException("Invalid username or password");
        }

        // 3. Check account is not locked
        if (user.isLocked()) {
            authAuditService.writeAuthLog("LOGIN_FAILURE", user.getUserId(), null,
                    "FAILURE", ipAddress, "Account locked");
            throw new AccountLockedException("Account is temporarily locked. Please try again later.");
        }

        // 4. Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int newAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newAttempts);

            if (newAttempts >= lockoutMaxAttempts) {
                user.setLockedUntil(OffsetDateTime.now().plusMinutes(lockoutDurationMinutes));
                authAuditService.writeAuthLog("ACCOUNT_LOCKED", user.getUserId(), null,
                        "FAILURE", ipAddress, null);
            } else {
                authAuditService.writeAuthLog("LOGIN_FAILURE", user.getUserId(), null,
                        "FAILURE", ipAddress, null);
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        // 5. Success — reset lockout state, update last login, issue token
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        authAuditService.writeAuthLog("LOGIN_SUCCESS", user.getUserId(), null,
                "SUCCESS", ipAddress, null);

        return issueToken(user);
    }

    /**
     * Issues a fresh JWT for the currently authenticated user (token refresh).
     * Old token remains valid until its natural expiry (AD-006).
     */
    public TokenResponse refresh(String ipAddress) {
        AuthContext ctx = AuthContext.Holder.get();
        HospitalUser user = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.isActive()) {
            throw new BadCredentialsException("Account is inactive");
        }

        authAuditService.writeAuthLog("TOKEN_REFRESH", user.getUserId(), null,
                "SUCCESS", ipAddress, null);

        return issueToken(user);
    }

    /**
     * Revokes the given raw Bearer token by inserting its jti into the blacklist.
     * The jti and expiry are extracted by Base64-decoding the JWT payload
     * (no signature re-verification — JwtAuthFilter already verified it upstream).
     */
    public void logout(String rawToken, String ipAddress) {
        String[] parts = rawToken.split("\\.");
        if (parts.length < 2) return;

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8);

        String jti = extractClaim(payload, "jti");
        String expStr = extractClaim(payload, "exp");
        if (jti == null || expStr == null) return;

        long expEpochSeconds = Long.parseLong(expStr);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(expEpochSeconds), ZoneOffset.UTC);

        AuthContext ctx = AuthContext.Holder.get();
        TokenBlacklist entry = TokenBlacklist.builder()
                .jti(jti)
                .userId(ctx.getUserId())
                .expiresAt(expiresAt)
                .revokedAt(OffsetDateTime.now())
                .build();
        tokenBlacklistRepository.save(entry);

        authAuditService.writeAuthLog("LOGOUT", ctx.getUserId(), null,
                "SUCCESS", ipAddress, null);
    }

    /**
     * Retrieves the authenticated user's own profile.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser() {
        AuthContext ctx = AuthContext.Holder.get();
        HospitalUser user = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return staffMapper.toProfileResponse(user);
    }

    /**
     * Builds and signs a JWT for the given user.
     * Claims structure matches the frozen JwtAuthFilter contract exactly.
     */
    public TokenResponse issueToken(HospitalUser user) {
        String jti = UUID.randomUUID().toString();
        long nowMs = System.currentTimeMillis();
        long expirationMs = (long) expirationHours * 60 * 60 * 1000L;
        long expiryMs = nowMs + expirationMs;

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        String token = Jwts.builder()
                .id(jti)
                .subject(user.getUserId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expiryMs))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        return new TokenResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                Instant.ofEpochMilli(expiryMs)
        );
    }

    /**
     * Extracts a single claim value from a raw JSON JWT payload string.
     * Handles both quoted string values and numeric values.
     */
    private String extractClaim(String json, String claim) {
        String key = "\"" + claim + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            if (end < 0) return null;
            return json.substring(start, end);
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end))
                    || json.charAt(end) == '.')) {
                end++;
            }
            return json.substring(start, end);
        }
    }
}
