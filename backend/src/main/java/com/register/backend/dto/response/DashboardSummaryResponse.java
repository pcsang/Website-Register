package com.register.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary counts for the admin dashboard: total submissions, counts broken down by status, and
 * submissions created today (server local time).
 */
public record DashboardSummaryResponse(
        long total,
        @JsonProperty("new") long newCount,
        long inProgress,
        long completed,
        long submittedToday
) {
}
