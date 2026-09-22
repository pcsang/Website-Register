package com.register.backend.dto.response;

import java.time.LocalDateTime;

/**
 * A single internal note on a submission's timeline, returned by the {@code /api/admin/submissions/{id}/notes}
 * endpoints.
 *
 * <p>Includes {@code authorUsername} (denormalized at write time) rather than only {@code authorId}, since
 * notes display "who wrote this" directly on the UI - unlike {@code assignedToId}, which is a filter/label
 * resolved on the frontend.
 *
 * @param id             the note's ID
 * @param submissionId   the submission this note belongs to
 * @param authorId       the admin user ID who wrote this note
 * @param authorUsername the username of the admin user who wrote this note
 * @param content        the note's text content
 * @param createdAt      when the note was created
 */
public record SubmissionNoteResponse(
        Long id,
        Long submissionId,
        Long authorId,
        String authorUsername,
        String content,
        LocalDateTime createdAt
) {
}
