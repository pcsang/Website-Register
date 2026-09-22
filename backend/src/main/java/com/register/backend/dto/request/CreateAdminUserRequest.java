package com.register.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/users}.
 *
 * @param username the new admin account's username (must be unique)
 * @param password the new admin account's plaintext password (hashed before persisting, never stored as-is)
 */
public record CreateAdminUserRequest(

        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password

) {
}
