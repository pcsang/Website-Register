package com.register.backend.dto.request;

import java.math.BigDecimal;

/**
 * Inbound SePay webhook payload. Field names are best-effort based on commonly documented SePay webhook
 * shape - not independently verified against live SePay docs; adjust if the real payload differs. Unknown
 * incoming JSON fields are ignored (Spring's default {@code ObjectMapper} already has
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled).
 *
 * <p>No {@code @NotNull}/{@code @NotBlank} validation annotations here - this is an external, unverified
 * sender; the service layer decides what to do with a partial payload rather than the controller 400ing it.
 */
public record SepayWebhookRequest(
        Long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String referenceCode
) {
}
