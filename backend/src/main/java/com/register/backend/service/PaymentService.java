package com.register.backend.service;

import com.register.backend.dto.request.SepayWebhookRequest;
import com.register.backend.dto.response.PaymentResponse;
import com.register.backend.entity.Course;
import com.register.backend.entity.Payment;
import com.register.backend.entity.Submission;
import com.register.backend.enums.PaymentStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.PaymentMapper;
import com.register.backend.repository.CourseRepository;
import com.register.backend.repository.PaymentRepository;
import com.register.backend.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles SePay VietQR payment creation/lookup for a submission's tuition fee, and processing of inbound
 * SePay payment-confirmation webhooks.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Pattern PAYMENT_CODE_PATTERN = Pattern.compile("DUP\\s*0*([0-9]{1,9})", Pattern.CASE_INSENSITIVE);

    /**
     * Minimum fraction of the expected amount a webhook's reported transfer must meet to be auto-marked
     * paid. {@code paymentCode} is a guessable sequential value (visible on the QR shown to the student),
     * so without a floor anyone who knows the receiving bank account could send a trivial transfer with a
     * guessed/observed code and have it accepted as full payment of an unrelated, much larger amount. A
     * transfer at or above this fraction is still accepted (and a mismatch still logged) to tolerate minor
     * bank-fee-driven shortfalls, per the original "bank transfer is the source of truth" decision - this
     * only guards against wildly-under-amount transfers, not small discrepancies.
     */
    private static final BigDecimal MINIMUM_AMOUNT_RATIO = new BigDecimal("0.9");

    private final PaymentRepository paymentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository, SubmissionRepository submissionRepository,
                           CourseRepository courseRepository, PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Returns the existing pending payment for a submission if one exists (idempotent), or creates a new
     * one snapshotting the submission's course price.
     *
     * @param submissionId the submission ID to create/get a pending payment for
     * @return the pending payment, mapped to a response DTO
     * @throws ResourceNotFoundException if the submission doesn't exist, has no associated course, or that
     *                                    course doesn't exist
     */
    @Transactional
    public PaymentResponse createOrGetPendingPayment(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        return paymentRepository.findBySubmissionIdAndStatus(submissionId, PaymentStatus.PENDING)
                .map(paymentMapper::toResponse)
                .orElseGet(() -> {
                    Long courseId = submission.getCourseId();
                    if (courseId == null) {
                        throw new ResourceNotFoundException(
                                "Submission " + submissionId + " has no associated course, cannot create a payment");
                    }
                    Course course = courseRepository.findById(courseId)
                            .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));

                    Payment payment = new Payment();
                    payment.setSubmissionId(submissionId);
                    payment.setAmount(course.getPrice());
                    payment.setStatus(PaymentStatus.PENDING);
                    Payment saved = paymentRepository.save(payment);
                    return paymentMapper.toResponse(saved);
                });
    }

    /**
     * Retrieves the most recently created payment for a submission.
     *
     * @param submissionId the submission ID
     * @return the most recent payment, mapped to a response DTO
     * @throws ResourceNotFoundException if no payment exists yet for this submission
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForSubmission(Long submissionId) {
        Payment payment = paymentRepository.findFirstBySubmissionIdOrderByCreatedAtDesc(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for submission id: " + submissionId));
        return paymentMapper.toResponse(payment);
    }

    /**
     * Processes an inbound SePay webhook delivery, marking the matching pending payment as paid when the
     * payload is a recognizable, not-already-processed inbound transfer confirmation. Never throws for a
     * malformed/unrecognized/duplicate payload - those cases are logged and simply ignored, so the caller
     * always responds 200 to SePay.
     *
     * @param payload the webhook payload
     */
    @Transactional
    public void handleSepayWebhook(SepayWebhookRequest payload) {
        if (payload.transferType() == null || payload.transferType().isBlank()
                || !payload.transferType().equalsIgnoreCase("in")) {
            log.info("ignoring non-inbound or unrecognized transferType webhook");
            return;
        }

        if (payload.id() != null && paymentRepository.existsBySepayTransactionId(String.valueOf(payload.id()))) {
            log.info("duplicate webhook delivery, already processed");
            return;
        }

        String content = payload.content();
        if (content == null) {
            log.warn("could not extract a payment code from webhook content: {}", content);
            return;
        }
        Matcher matcher = PAYMENT_CODE_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("could not extract a payment code from webhook content: {}", content);
            return;
        }

        Long paymentId;
        try {
            paymentId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            log.warn("could not extract a payment code from webhook content: {}", content);
            return;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("webhook referenced unknown payment id: {}", paymentId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("webhook referenced payment {} which is no longer PENDING (status={})", paymentId, payment.getStatus());
            return;
        }

        if (payload.transferAmount() != null && payload.transferAmount().compareTo(payment.getAmount()) != 0) {
            BigDecimal minimumAcceptable = payment.getAmount().multiply(MINIMUM_AMOUNT_RATIO);
            if (payload.transferAmount().compareTo(minimumAcceptable) < 0) {
                log.warn("webhook transferAmount {} is well below expected payment amount {} for payment {} "
                                + "(minimum acceptable {}) - leaving PENDING for manual admin review",
                        payload.transferAmount(), payment.getAmount(), paymentId, minimumAcceptable);
                return;
            }
            log.warn("webhook transferAmount {} differs from expected payment amount {} for payment {}",
                    payload.transferAmount(), payment.getAmount(), paymentId);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setSepayTransactionId(payload.id() != null ? String.valueOf(payload.id()) : null);

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            log.info("lost a race on sepay_transaction_id uniqueness, treating as already-processed");
        }
    }

}
