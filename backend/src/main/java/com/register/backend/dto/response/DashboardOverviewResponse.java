package com.register.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The admin dashboard's expanded overview: registrations grouped by month (last 6 months, oldest first),
 * the upcoming course schedule, an estimated revenue figure, and the current pass-rate settings.
 *
 * <p>Separate from {@link DashboardSummaryResponse} (unchanged, still backs the original
 * {@code GET /api/admin/dashboard/summary}) - this is a new, additive endpoint
 * ({@code GET /api/admin/dashboard/overview}) for the richer overview page.
 *
 * @param monthlyRegistrations       submission counts for each of the last 6 calendar months (including
 *                                   the current one), chronological, oldest first
 * @param upcomingCourses            courses starting today or later, soonest first, capped at a configured
 *                                   limit
 * @param estimatedRevenueThisMonth an <strong>estimate</strong>, not real transactional/payment data - the
 *                                   sum of {@code Course.price} for every submission with a "registered
 *                                   seat" status ({@code CONFIRMED}/{@code IN_PROGRESS}/{@code GRADUATED})
 *                                   created in the current calendar month
 * @param settings                   the current admin-configured pass-rate settings
 */
public record DashboardOverviewResponse(
        List<MonthlyRegistrationCountResponse> monthlyRegistrations,
        List<CourseResponse> upcomingCourses,
        BigDecimal estimatedRevenueThisMonth,
        DashboardSettingsResponse settings
) {
}
