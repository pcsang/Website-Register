package com.register.backend.dto.response;

/**
 * Response body for a successful {@code POST /api/auth/login}.
 *
 * @param token    the signed JWT to send as {@code Authorization: Bearer <token>} on subsequent
 *                 admin requests
 * @param username the authenticated admin's username
 * @param role     the authenticated admin's role (e.g. {@code ROLE_ADMIN})
 */
public record LoginResponse(
        String token,
        String username,
        String role
) {
}
