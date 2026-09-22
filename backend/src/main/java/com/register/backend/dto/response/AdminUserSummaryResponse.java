package com.register.backend.dto.response;

/**
 * Summary view of an admin/consultant account returned by {@code /api/admin/users} endpoints.
 *
 * <p>Deliberately excludes {@code passwordHash} and {@code role} — the API never exposes password hashes,
 * and every account currently created through this endpoint is a {@code ROLE_ADMIN} account.
 *
 * @param id       the admin user's ID
 * @param username the admin user's username
 */
public record AdminUserSummaryResponse(
        Long id,
        String username
) {
}
