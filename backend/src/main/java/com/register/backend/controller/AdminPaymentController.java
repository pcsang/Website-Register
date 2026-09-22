package com.register.backend.controller;

import com.register.backend.dto.response.PaymentResponse;
import com.register.backend.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/submissions/{submissionId}/payment")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Creates a new pending payment for a submission's tuition fee, or returns the existing pending one if
     * already created.
     *
     * @param submissionId the submission ID
     * @return the pending payment
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createOrGetPendingPayment(@PathVariable Long submissionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createOrGetPendingPayment(submissionId));
    }

    /**
     * Retrieves the most recent payment for a submission.
     *
     * @param submissionId the submission ID
     * @return the most recent payment
     */
    @GetMapping
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long submissionId) {
        return ResponseEntity.ok(paymentService.getPaymentForSubmission(submissionId));
    }

}
