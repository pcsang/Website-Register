package com.register.backend.dto.response;

import com.register.backend.enums.SubmissionStatus;

import java.time.LocalDateTime;

public record SubmissionResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String message,
        SubmissionStatus status,
        Long courseId,
        Long assignedToId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
