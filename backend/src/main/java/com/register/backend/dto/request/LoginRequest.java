package com.register.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/login}.
 *
 * @param username the admin username
 * @param password the admin password (plaintext, over HTTPS in production — never persisted)
 */
public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        String password

) {
}
