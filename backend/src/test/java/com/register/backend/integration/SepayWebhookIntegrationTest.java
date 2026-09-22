package com.register.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.entity.Payment;
import com.register.backend.entity.Submission;
import com.register.backend.enums.PaymentStatus;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.PaymentRepository;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for {@code POST /api/webhooks/sepay}, mirroring
 * {@code SubmissionApiIntegrationTest}'s real-context, {@code MockMvc}-driven, {@code test}-profile (H2)
 * style. The class is {@code @Transactional} so rows seeded by one test never leak into another.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SepayWebhookIntegrationTest {

    private static final String WEBHOOK_URL = "/api/webhooks/sepay";
    private static final String CORRECT_AUTH_HEADER = "Apikey test-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void webhookReturns401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(1L, "DUP000001", new BigDecimal("100.00"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookReturns401WhenAuthorizationHeaderIsGarbage() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", "Apikey wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(1L, "DUP000001", new BigDecimal("100.00"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookMarksMatchingPendingPaymentPaidAndIsIdempotentOnReplay() throws Exception {
        Payment payment = seedPendingPayment();
        String body = webhookBody(555L, "CT DEN:1234 DUP" + String.format("%06d", payment.getId()) + " hoc phi", payment.getAmount());

        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", CORRECT_AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Payment afterFirst = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(afterFirst.getSepayTransactionId()).isEqualTo("555");
        assertThat(afterFirst.getPaidAt()).isNotNull();

        // Replaying the identical payload a second time must still return 200 and leave the row unchanged
        // (idempotent) - the existsBySepayTransactionId short-circuit kicks in.
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", CORRECT_AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Payment afterSecond = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(afterSecond.getSepayTransactionId()).isEqualTo("555");
        assertThat(afterSecond.getPaidAt()).isEqualTo(afterFirst.getPaidAt());
    }

    /**
     * Seeds a submission and a PENDING payment directly via the repositories.
     *
     * @return the persisted, flushed pending payment (with its generated ID populated)
     */
    private Payment seedPendingPayment() {
        Submission submission = new Submission();
        submission.setFullName("Jane Doe");
        submission.setEmail("jane@example.com");
        submission.setPhone("0123456789");
        submission.setStatus(SubmissionStatus.PENDING_CONSULTATION);
        Submission savedSubmission = submissionRepository.saveAndFlush(submission);

        Payment payment = new Payment();
        payment.setSubmissionId(savedSubmission.getId());
        payment.setAmount(new BigDecimal("12000000.00"));
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.saveAndFlush(payment);
    }

    /**
     * Builds a minimal JSON SePay webhook payload.
     *
     * @param id              the SePay transaction ID
     * @param content         the transfer content (expected to contain the payment code)
     * @param transferAmount  the transferred amount
     * @return the JSON request body
     * @throws Exception if serialization fails
     */
    private String webhookBody(Long id, String content, BigDecimal transferAmount) throws Exception {
        return objectMapper.writeValueAsString(new WebhookBody(id, "in", content, transferAmount));
    }

    private record WebhookBody(Long id, String transferType, String content, BigDecimal transferAmount) {
    }

}
