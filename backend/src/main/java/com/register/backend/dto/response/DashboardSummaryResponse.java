package com.register.backend.dto.response;

/**
 * Summary counts for the admin dashboard: total submissions, counts broken down by the 4-state
 * {@link com.register.backend.enums.SubmissionStatus} model, and submissions created today (server local
 * time).
 */
public record DashboardSummaryResponse(
        long total,
        long pendingConsultation,
        long confirmed,
        long inProgress,
        long graduated,
        long submittedToday
) {
}
