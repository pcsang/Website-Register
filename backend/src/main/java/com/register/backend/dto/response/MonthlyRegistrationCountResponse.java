package com.register.backend.dto.response;

/**
 * One data point in the dashboard overview's "registrations by month" chart.
 *
 * @param month the calendar month, formatted {@code "yyyy-MM"} (e.g. {@code "2026-09"})
 * @param count the number of submissions created in that month
 */
public record MonthlyRegistrationCountResponse(
        String month,
        long count
) {
}
