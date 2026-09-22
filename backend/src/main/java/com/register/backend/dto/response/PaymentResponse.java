package com.register.backend.dto.response;

import com.register.backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long submissionId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentCode,
        String qrImageUrl,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
