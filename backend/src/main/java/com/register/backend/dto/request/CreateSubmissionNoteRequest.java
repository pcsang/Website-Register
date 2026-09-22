package com.register.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/admin/submissions/{id}/notes}.
 *
 * @param content the note's text content
 */
public record CreateSubmissionNoteRequest(

        @NotBlank
        String content

) {
}
