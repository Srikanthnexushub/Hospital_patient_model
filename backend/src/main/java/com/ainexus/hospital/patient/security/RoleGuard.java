package com.ainexus.hospital.patient.security;

import com.ainexus.hospital.patient.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Server-side role enforcement. Called at the beginning of every service method.
 * Client-side checks are cosmetic only and do not constitute a security boundary.
 */
@Component
public class RoleGuard {

    /**
     * Ensures the current authenticated user has one of the allowed roles.
     * @throws ForbiddenException if the role is not permitted
     */
    public void requireRoles(Set<String> allowedRoles) {
        AuthContext ctx = AuthContext.Holder.get();
        if (ctx == null || !allowedRoles.contains(ctx.getRole())) {
            throw new ForbiddenException();
        }
    }

    /** Convenience overload for inline role sets. */
    public void requireRoles(String... roles) {
        requireRoles(Set.of(roles));
    }

    /** Ensures any authenticated user (any valid role) is present. */
    public void requireAuthenticated() {
        if (!AuthContext.Holder.isAuthenticated()) {
            throw new ForbiddenException("Authentication required.");
        }
    }
}
