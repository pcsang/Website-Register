package com.register.backend.enums;

/**
 * Lifecycle states of a {@link com.register.backend.entity.Payment}. Deliberately decoupled from
 * {@link SubmissionStatus} - the admin still manually manages a submission's status; a payment is shown
 * alongside it as informational context, not auto-linked to it. There is no {@code EXPIRED} state because
 * this app has no scheduler/background job, and a real bank transfer can land hours after a QR code was
 * generated - a stale {@code PENDING} payment can simply be handled by generating a fresh one rather than
 * needing to be aged out automatically.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED
}
