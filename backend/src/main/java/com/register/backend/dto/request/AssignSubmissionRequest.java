package com.register.backend.dto.request;

/**
 * Request body for {@code PATCH /api/admin/submissions/{id}/assign}.
 *
 * @param adminUserId the admin/consultant account ID to assign the submission to, or {@code null} to
 *                     un-assign it. Deliberately not {@code @NotNull} - {@code null} is a valid, meaningful
 *                     value here, not an omission.
 */
public record AssignSubmissionRequest(
        Long adminUserId
) {
}
