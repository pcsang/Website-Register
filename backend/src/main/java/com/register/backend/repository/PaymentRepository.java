package com.register.backend.repository;

import com.register.backend.entity.Payment;
import com.register.backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Finds a payment for the given submission that currently has the given status.
     *
     * @param submissionId the submission ID
     * @param status       the status to match
     * @return the matching payment, if any
     */
    Optional<Payment> findBySubmissionIdAndStatus(Long submissionId, PaymentStatus status);

    /**
     * Finds the most recently created payment for the given submission, regardless of status.
     *
     * @param submissionId the submission ID
     * @return the most recently created matching payment, if any
     */
    Optional<Payment> findFirstBySubmissionIdOrderByCreatedAtDesc(Long submissionId);

    /**
     * Checks whether a payment already recorded the given SePay transaction ID - used to detect duplicate
     * webhook deliveries.
     *
     * @param sepayTransactionId the SePay transaction ID to check
     * @return {@code true} if a payment already has this transaction ID recorded
     */
    boolean existsBySepayTransactionId(String sepayTransactionId);

}
