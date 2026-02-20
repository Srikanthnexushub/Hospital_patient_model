package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.CreateUserRequest;
import com.ainexus.hospital.patient.dto.request.UpdateUserRequest;
import com.ainexus.hospital.patient.dto.response.UserDetailResponse;
import com.ainexus.hospital.patient.dto.response.UserSummaryResponse;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * REST controller for staff account management.
 * All endpoints require ADMIN role (enforced by StaffService.roleGuard).
 *
 * POST   /api/v1/admin/users          — create staff account → 201 + Location
 * GET    /api/v1/admin/users          — list staff (paginated, filterable)
 * GET    /api/v1/admin/users/{userId} — get staff detail
 * PATCH  /api/v1/admin/users/{userId} — update fields (If-Match version required)
 * DELETE /api/v1/admin/users/{userId} — deactivate (soft-delete) → 204
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Staff Management", description = "ADMIN-only staff account lifecycle")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    /**
     * POST /api/v1/admin/users
     * Creates a new staff account. Returns 201 with Location header.
     */
    @PostMapping
    @Operation(summary = "Create staff account")
    public ResponseEntity<UserDetailResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        String createdBy = AuthContext.Holder.get().getUsername();
        UserDetailResponse created = staffService.createUser(request, createdBy);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(created.userId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * GET /api/v1/admin/users
     * Returns a paginated list of staff accounts. Supports ?role= and ?status= filters.
     */
    @GetMapping
    @Operation(summary = "List staff accounts")
    public ResponseEntity<Page<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "username") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction dir = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));

        return ResponseEntity.ok(staffService.listUsers(role, status, pageable));
    }

    /**
     * GET /api/v1/admin/users/{userId}
     * Returns the full detail response for a single staff account.
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get staff account detail")
    public ResponseEntity<UserDetailResponse> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(staffService.getUserById(userId));
    }

    /**
     * PATCH /api/v1/admin/users/{userId}
     * Partial update. Requires If-Match header with current version for optimistic locking.
     * Returns updated UserDetailResponse with ETag set to new version.
     */
    @PatchMapping("/{userId}")
    @Operation(summary = "Update staff account")
    public ResponseEntity<UserDetailResponse> updateUser(
            @PathVariable String userId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateUserRequest request) {

        int version = parseVersion(ifMatch);
        String updatedBy = AuthContext.Holder.get().getUsername();
        UserDetailResponse updated = staffService.updateUser(userId, request, version, updatedBy);

        return ResponseEntity.ok()
                .eTag(String.valueOf(updated.failedAttempts()))  // ETag = version from response
                .body(updated);
    }

    /**
     * DELETE /api/v1/admin/users/{userId}
     * Deactivates a staff account (soft-delete). Returns 204 No Content.
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Deactivate staff account")
    public ResponseEntity<Void> deactivateUser(@PathVariable String userId) {
        String requestingUserId = AuthContext.Holder.get().getUserId();
        staffService.deactivateUser(userId, requestingUserId);
        return ResponseEntity.noContent().build();
    }

    private int parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return -1;
        // ETag may be quoted: "42" or unquoted: 42
        String cleaned = ifMatch.replace("\"", "").trim();
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
