package com.register.backend.service;

import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.request.UpdateDashboardSettingsRequest;
import com.register.backend.dto.response.DashboardOverviewResponse;
import com.register.backend.dto.response.DashboardSettingsResponse;
import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.dto.response.MonthlyRegistrationCountResponse;
import com.register.backend.entity.Course;
import com.register.backend.entity.DashboardSettings;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.CourseRepository;
import com.register.backend.repository.DashboardSettingsRepository;
import com.register.backend.repository.MonthlyRegistrationCountProjection;
import com.register.backend.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides aggregate counts and figures for the admin dashboard, both the original summary
 * ({@link #getSummary()}, unchanged) and the expanded overview added in Phase D3
 * ({@link #getOverview()}, monthly registration chart / upcoming courses / estimated revenue / pass-rate
 * settings).
 */
@Service
public class DashboardService {

    /** Number of trailing calendar months (including the current one) shown in the registrations chart. */
    private static final int MONTHLY_CHART_WINDOW_SIZE = 6;

    /** Fixed, single-row id of the {@link DashboardSettings} table. */
    private static final long SETTINGS_ID = 1L;

    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final DashboardSettingsRepository dashboardSettingsRepository;
    private final int upcomingCoursesLimit;

    /**
     * Creates the dashboard service.
     *
     * @param submissionRepository       used for the summary counts, monthly chart grouping, and revenue
     *                                    calculation
     * @param courseRepository            used to price out registered seats for the revenue estimate
     * @param courseService                used to fetch the upcoming course schedule
     * @param dashboardSettingsRepository used to read/update the admin-configured pass-rate settings
     * @param upcomingCoursesLimit         maximum number of courses returned in the upcoming schedule
     */
    public DashboardService(SubmissionRepository submissionRepository,
                             CourseRepository courseRepository,
                             CourseService courseService,
                             DashboardSettingsRepository dashboardSettingsRepository,
                             @Value("${app.dashboard.upcoming-courses-limit:4}") int upcomingCoursesLimit) {
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.dashboardSettingsRepository = dashboardSettingsRepository;
        this.upcomingCoursesLimit = upcomingCoursesLimit;
    }

    /**
     * Builds the dashboard summary: total submissions, counts per status (4-state model), and submissions
     * created today. "Today" is computed as the current day's start-of-day through the next day's
     * start-of-day in the server's local time zone, matching how {@code Submission.createdAt} is populated
     * ({@code LocalDateTime.now()} in {@code @PrePersist}, with no time zone stored).
     *
     * @return the assembled dashboard summary
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        long total = submissionRepository.count();
        long pendingConsultation = submissionRepository.countByStatus(SubmissionStatus.PENDING_CONSULTATION);
        long confirmed = submissionRepository.countByStatus(SubmissionStatus.CONFIRMED);
        long inProgress = submissionRepository.countByStatus(SubmissionStatus.IN_PROGRESS);
        long graduated = submissionRepository.countByStatus(SubmissionStatus.GRADUATED);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
        long submittedToday = submissionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startOfToday, startOfTomorrow);

        return new DashboardSummaryResponse(total, pendingConsultation, confirmed, inProgress, graduated, submittedToday);
    }

    /**
     * Builds the expanded dashboard overview: registrations grouped by month (last 6 months), the upcoming
     * course schedule, an estimated revenue figure for the current month, and the current pass-rate
     * settings.
     *
     * @return the assembled dashboard overview
     */
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        List<MonthlyRegistrationCountResponse> monthlyRegistrations = getMonthlyRegistrationCounts();
        List<CourseResponse> upcomingCourses = courseService.getUpcomingCourses(upcomingCoursesLimit);
        BigDecimal estimatedRevenueThisMonth = calculateEstimatedRevenueForCurrentMonth();
        DashboardSettingsResponse settings = toSettingsResponse(getOrCreateSettings());

        return new DashboardOverviewResponse(monthlyRegistrations, upcomingCourses, estimatedRevenueThisMonth, settings);
    }

    /**
     * Retrieves the current admin-configured dashboard settings (pass rate / exam count).
     *
     * @return the current settings
     */
    @Transactional(readOnly = true)
    public DashboardSettingsResponse getSettings() {
        return toSettingsResponse(getOrCreateSettings());
    }

    /**
     * Updates the admin-configured dashboard settings.
     *
     * @param request the validated update request
     * @return the updated settings
     */
    @Transactional
    public DashboardSettingsResponse updateSettings(UpdateDashboardSettingsRequest request) {
        DashboardSettings settings = getOrCreateSettings();
        settings.setPassRatePercent(request.passRatePercent());
        settings.setExamCount(request.examCount());
        DashboardSettings saved = dashboardSettingsRepository.saveAndFlush(settings);
        return toSettingsResponse(saved);
    }

    /**
     * Builds the registrations-by-month chart data for the last {@value #MONTHLY_CHART_WINDOW_SIZE}
     * calendar months (including the current month), oldest first. Months with no submissions are filled
     * in with a count of zero, since the underlying grouped query only returns rows for months that have
     * at least one submission.
     *
     * @return the chronological list of monthly counts
     */
    private List<MonthlyRegistrationCountResponse> getMonthlyRegistrationCounts() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(MONTHLY_CHART_WINDOW_SIZE - 1L);
        LocalDateTime start = startMonth.atDay(1).atStartOfDay();

        Map<String, Long> countsByMonth = submissionRepository.countRegistrationsByMonthSince(start).stream()
                .collect(Collectors.toMap(
                        MonthlyRegistrationCountProjection::getMonth,
                        MonthlyRegistrationCountProjection::getCount));

        List<MonthlyRegistrationCountResponse> result = new ArrayList<>(MONTHLY_CHART_WINDOW_SIZE);
        for (int i = 0; i < MONTHLY_CHART_WINDOW_SIZE; i++) {
            String monthKey = startMonth.plusMonths(i).toString();
            result.add(new MonthlyRegistrationCountResponse(monthKey, countsByMonth.getOrDefault(monthKey, 0L)));
        }
        return result;
    }

    /**
     * Estimates this calendar month's revenue: the sum of {@code Course.price} for every submission with a
     * "registered seat" status ({@link CourseService#REGISTERED_STATUSES} - the same rule
     * {@code CourseService} uses for a course's {@code seatsRegistered}) created since the start of the
     * current month. This is an <strong>estimate</strong>, not real payment/transaction data - the system
     * has no payment concept.
     *
     * @return the estimated revenue for the current calendar month, or zero if there are no matching
     *         submissions
     */
    private BigDecimal calculateEstimatedRevenueForCurrentMonth() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        List<Submission> revenueSubmissions = submissionRepository
                .findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
                        CourseService.REGISTERED_STATUSES, startOfMonth, startOfNextMonth);
        if (revenueSubmissions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, Long> seatCountByCourseId = revenueSubmissions.stream()
                .collect(Collectors.groupingBy(Submission::getCourseId, Collectors.counting()));
        Map<Long, Course> coursesById = courseRepository.findAllById(seatCountByCourseId.keySet()).stream()
                .collect(Collectors.toMap(Course::getId, course -> course));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Long> entry : seatCountByCourseId.entrySet()) {
            Course course = coursesById.get(entry.getKey());
            if (course != null) {
                total = total.add(course.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            }
        }
        return total;
    }

    /**
     * Fetches the single {@link DashboardSettings} row, lazily creating it with default (unconfigured)
     * values if it's somehow missing - the normal case is that {@code V4__add_dashboard_settings.sql}
     * already seeded it, this is just a defensive fallback so the overview/settings endpoints never fail
     * outright over a missing settings row.
     *
     * @return the settings entity
     */
    private DashboardSettings getOrCreateSettings() {
        return dashboardSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> {
                    DashboardSettings defaults = new DashboardSettings();
                    defaults.setId(SETTINGS_ID);
                    return dashboardSettingsRepository.saveAndFlush(defaults);
                });
    }

    /**
     * Maps a {@link DashboardSettings} entity to its response DTO.
     *
     * @param settings the entity to map
     * @return the mapped response
     */
    private DashboardSettingsResponse toSettingsResponse(DashboardSettings settings) {
        return new DashboardSettingsResponse(settings.getPassRatePercent(), settings.getExamCount(), settings.getUpdatedAt());
    }

}
