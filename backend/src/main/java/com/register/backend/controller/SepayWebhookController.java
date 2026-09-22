package com.register.backend.controller;

import com.register.backend.dto.request.SepayWebhookRequest;
import com.register.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Receives payment-confirmation webhooks from SePay. This path is left public by
 * {@code SecurityConfig}'s existing catch-all rule (only {@code /api/admin/**} requires authentication) -
 * authenticity is instead verified here via a shared-secret {@code Authorization} header, since a
 * server-to-server webhook caller doesn't fit Spring Security's JWT-based admin auth model.
 *
 * <p>The exact header name/scheme ({@code Authorization: Apikey <secret>}) is assumed from commonly
 * documented SePay convention and has not been independently verified against the live SePay dashboard -
 * confirm/adjust against the real configured webhook settings before going live.
 */
@RestController
@RequestMapping("/api/webhooks/sepay")
public class SepayWebhookController {

    private final PaymentService paymentService;
    private final String webhookSecret;

    public SepayWebhookController(PaymentService paymentService, @Value("${app.sepay.webhook-secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    /**
     * Verifies the shared-secret {@code Authorization} header and, if valid, hands the payload off to the
     * service layer for processing.
     *
     * @param authorizationHeader the incoming {@code Authorization} header, or {@code null} if absent
     * @param payload             the webhook payload
     * @return 401 if the header is missing or doesn't match the configured secret, otherwise 200
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                               @RequestBody SepayWebhookRequest payload) {
        String expected = "Apikey " + webhookSecret;
        if (authorizationHeader == null
                || !MessageDigest.isEqual(authorizationHeader.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        paymentService.handleSepayWebhook(payload);
        return ResponseEntity.ok().build();
    }

}
