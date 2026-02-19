package com.ainexus.hospital.patient.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Holds the authenticated user's identity for the current request.
 * Stored in a ThreadLocal via AuthContextHolder.
 */
@Getter
@AllArgsConstructor
public class AuthContext {
    private final String userId;
    private final String username;
    private final String role;

    public static class Holder {
        private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

        public static void set(AuthContext ctx) { CONTEXT.set(ctx); }
        public static AuthContext get() { return CONTEXT.get(); }
        public static void clear() { CONTEXT.remove(); }
        public static boolean isAuthenticated() { return CONTEXT.get() != null; }
    }
}
