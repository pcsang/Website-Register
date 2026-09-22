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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createOrGetPendingPaymentSnapshotsCoursePriceAndSavesPendingWhenNoneExists() {
        Long submissionId = 1L;
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setCourseId(5L);

        Course course = new Course();
        course.setId(5L);
        course.setPrice(new BigDecimal("12000000.00"));

        Payment savedPayment = new Payment();
        savedPayment.setId(10L);
        savedPayment.setSubmissionId(submissionId);
        savedPayment.setAmount(course.getPrice());
        savedPayment.setStatus(PaymentStatus.PENDING);

        PaymentResponse expectedResponse = new PaymentResponse(
                10L, submissionId, course.getPrice(), PaymentStatus.PENDING, "DUP000010", "http://qr",
                null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(paymentRepository.findBySubmissionIdAndStatus(submissionId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentMapper.toResponse(savedPayment)).thenReturn(expectedResponse);

        PaymentResponse actual = paymentService.createOrGetPendingPayment(submissionId);

        assertThat(actual).isEqualTo(expectedResponse);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getSubmissionId()).isEqualTo(submissionId);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(course.getPrice());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createOrGetPendingPaymentReturnsExistingPendingPaymentWithoutCreatingANewOne() {
        Long submissionId = 1L;
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setCourseId(5L);

        Payment existingPayment = new Payment();
        existingPayment.setId(7L);
        existingPayment.setSubmissionId(submissionId);
        existingPayment.setStatus(PaymentStatus.PENDING);

        PaymentResponse expectedResponse = new PaymentResponse(
                7L, submissionId, new BigDecimal("100.00"), PaymentStatus.PENDING, "DUP000007", "http://qr",
                null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(paymentRepository.findBySubmissionIdAndStatus(submissionId, PaymentStatus.PENDING)).thenReturn(Optional.of(existingPayment));
        when(paymentMapper.toResponse(existingPayment)).thenReturn(expectedResponse);

        PaymentResponse actual = paymentService.createOrGetPendingPayment(submissionId);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(paymentRepository, never()).save(any(Payment.class));
        verifyNoInteractions(courseRepository);
    }

    @Test
    void createOrGetPendingPaymentThrowsResourceNotFoundExceptionWhenSubmissionDoesNotExist() {
        Long submissionId = 999L;
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createOrGetPendingPayment(submissionId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(paymentRepository, courseRepository, paymentMapper);
    }

    @Test
    void createOrGetPendingPaymentThrowsResourceNotFoundExceptionWhenSubmissionHasNoCourse() {
        Long submissionId = 1L;
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setCourseId(null);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(paymentRepository.findBySubmissionIdAndStatus(submissionId, PaymentStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createOrGetPendingPayment(submissionId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(courseRepository);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createOrGetPendingPaymentThrowsResourceNotFoundExceptionWhenCourseDoesNotExist() {
        Long submissionId = 1L;
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setCourseId(5L);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(paymentRepository.findBySubmissionIdAndStatus(submissionId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(courseRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createOrGetPendingPayment(submissionId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void getPaymentForSubmissionReturnsMappedResponseWhenPaymentExists() {
        Long submissionId = 1L;
        Payment payment = new Payment();
        payment.setId(7L);
        payment.setSubmissionId(submissionId);

        PaymentResponse expectedResponse = new PaymentResponse(
                7L, submissionId, new BigDecimal("100.00"), PaymentStatus.PENDING, "DUP000007", "http://qr",
                null, LocalDateTime.now(), LocalDateTime.now());

        when(paymentRepository.findFirstBySubmissionIdOrderByCreatedAtDesc(submissionId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(expectedResponse);

        PaymentResponse actual = paymentService.getPaymentForSubmission(submissionId);

        assertThat(actual).isEqualTo(expectedResponse);
    }

    @Test
    void getPaymentForSubmissionThrowsResourceNotFoundExceptionWhenNoneExists() {
        Long submissionId = 999L;
        when(paymentRepository.findFirstBySubmissionIdOrderByCreatedAtDesc(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentForSubmission(submissionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleSepayWebhookExtractsCodeFromContentAndMarksMatchingPendingPaymentPaid() {
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "CT DEN:123456 DUP000042 chuyen tien hoc phi", "in", new BigDecimal("12000000.00"), "REF1");

        Payment payment = new Payment();
        payment.setId(42L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("12000000.00"));

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(false);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        paymentService.handleSepayWebhook(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getSepayTransactionId()).isEqualTo("123");
        verify(paymentRepository).saveAndFlush(payment);
    }

    @Test
    void handleSepayWebhookIsNoOpWhenTransactionIdAlreadyProcessed() {
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "CT DEN:123456 DUP000042 chuyen tien hoc phi", "in", new BigDecimal("12000000.00"), "REF1");

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(true);

        paymentService.handleSepayWebhook(payload);

        verify(paymentRepository, never()).findById(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleSepayWebhookIsNoOpWhenContentHasNoExtractableCode() {
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "some unrelated content", "in", new BigDecimal("12000000.00"), "REF1");

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(false);

        paymentService.handleSepayWebhook(payload);

        verify(paymentRepository, never()).findById(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleSepayWebhookIsNoOpWhenTransferTypeIsOut() {
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "DUP000042", "out", new BigDecimal("12000000.00"), "REF1");

        paymentService.handleSepayWebhook(payload);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void handleSepayWebhookStillMarksPaidDespiteMinorAmountMismatch() {
        // 11,900,000 is ~99.2% of 12,000,000 - within the minimum-acceptable-ratio tolerance, so this
        // should still be treated as paid (e.g. a bank-fee-driven shortfall), just logged.
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "DUP000042", "in", new BigDecimal("11900000.00"), "REF1");

        Payment payment = new Payment();
        payment.setId(42L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("12000000.00"));

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(false);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        paymentService.handleSepayWebhook(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).saveAndFlush(payment);
    }

    @Test
    void handleSepayWebhookLeavesPendingWhenTransferAmountIsWellBelowExpected() {
        // A trivial 1,000 VND transfer carrying a guessed/observed payment code must NOT be able to mark a
        // much larger tuition payment as paid - this is the fraud-vector fix: paymentCode is a guessable
        // sequential value, so an amount floor is the real defense, not the code itself.
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "DUP000042", "in", new BigDecimal("1000.00"), "REF1");

        Payment payment = new Payment();
        payment.setId(42L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("12000000.00"));

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(false);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        paymentService.handleSepayWebhook(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleSepayWebhookIsNoOpWhenPaymentIsAlreadyPaid() {
        SepayWebhookRequest payload = new SepayWebhookRequest(
                123L, "MBBank", "2026-01-01 10:00:00", "0000000000",
                "DUP000042", "in", new BigDecimal("12000000.00"), "REF1");

        Payment payment = new Payment();
        payment.setId(42L);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("12000000.00"));

        when(paymentRepository.existsBySepayTransactionId("123")).thenReturn(false);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        paymentService.handleSepayWebhook(payload);

        verify(paymentRepository, times(1)).findById(42L);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

}
