package com.register.backend.dto.response;

import com.register.backend.enums.SubmissionStatus;

import java.time.LocalDateTime;

public record SubmissionResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String company,
        String position,
        String message,
        SubmissionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
