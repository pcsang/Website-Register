package com.register.backend.repository;

/**
 * Projection for {@link SubmissionRepository#countRegistrationsByMonthSince}: one row per calendar month
 * that has at least one submission, with the month formatted as {@code "yyyy-MM"} (matching
 * {@link java.time.YearMonth#toString()}) and the number of submissions created in that month.
 */
public interface MonthlyRegistrationCountProjection {

    /**
     * The calendar month, formatted {@code "yyyy-MM"}.
     *
     * @return the month string
     */
    String getMonth();

    /**
     * The number of submissions created in this month.
     *
     * @return the count
     */
    Long getCount();

}
