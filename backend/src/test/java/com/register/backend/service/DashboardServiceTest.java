package com.register.backend.service;

import com.register.backend.dto.request.UpdateDashboardSettingsRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.DashboardOverviewResponse;
import com.register.backend.dto.response.DashboardSettingsResponse;
import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.entity.Course;
import com.register.backend.entity.DashboardSettings;
import com.register.backend.entity.Submission;
import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.CourseRepository;
import com.register.backend.repository.DashboardSettingsRepository;
import com.register.backend.repository.MonthlyRegistrationCountProjection;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseService courseService;

    @Mock
    private DashboardSettingsRepository dashboardSettingsRepository;

    private DashboardService dashboardService;

    private static Course sampleCourse(Long id, BigDecimal price) {
        Course course = new Course();
        course.setId(id);
        course.setName("B1 Automatic");
        course.setLicenseClass(LicenseClass.B1);
        course.setPrice(price);
        course.setDurationMonths(3);
        course.setPracticeHours(40);
        course.setSeatsTotal(30);
        course.setStartDate(LocalDate.now().plusMonths(1));
        return course;
    }

    private static Submission sampleSubmission(Long courseId, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setFullName("Nguyen Van A");
        submission.setStatus(status);
        submission.setCourseId(courseId);
        return submission;
    }

    private static MonthlyRegistrationCountProjection projection(String month, long count) {
        return new MonthlyRegistrationCountProjection() {
            @Override
            public String getMonth() {
                return month;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    @Test
    void getSummaryAssemblesCountsFromRepository() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        when(submissionRepository.count()).thenReturn(150L);
        when(submissionRepository.countByStatus(SubmissionStatus.PENDING_CONSULTATION)).thenReturn(30L);
        when(submissionRepository.countByStatus(SubmissionStatus.CONFIRMED)).thenReturn(25L);
        when(submissionRepository.countByStatus(SubmissionStatus.IN_PROGRESS)).thenReturn(40L);
        when(submissionRepository.countByStatus(SubmissionStatus.GRADUATED)).thenReturn(55L);
        when(submissionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(12L);

        DashboardSummaryResponse actual = dashboardService.getSummary();

        assertThat(actual).isEqualTo(new DashboardSummaryResponse(150L, 30L, 25L, 40L, 55L, 12L));
        verify(submissionRepository).count();
        verify(submissionRepository).countByStatus(SubmissionStatus.PENDING_CONSULTATION);
        verify(submissionRepository).countByStatus(SubmissionStatus.CONFIRMED);
        verify(submissionRepository).countByStatus(SubmissionStatus.IN_PROGRESS);
        verify(submissionRepository).countByStatus(SubmissionStatus.GRADUATED);
        verify(submissionRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void getOverviewFillsMissingMonthsWithZeroAndOrdersChronologically() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        YearMonth currentMonth = YearMonth.now();
        YearMonth oldestMonth = currentMonth.minusMonths(5);
        // Only the oldest and current months have data; the 4 months in between should default to 0.
        when(submissionRepository.countRegistrationsByMonthSince(any(LocalDateTime.class))).thenReturn(List.of(
                projection(oldestMonth.toString(), 3L),
                projection(currentMonth.toString(), 7L)));
        when(courseService.getUpcomingCourses(4)).thenReturn(List.of());
        when(submissionRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
                anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(sampleSettings()));

        DashboardOverviewResponse actual = dashboardService.getOverview();

        assertThat(actual.monthlyRegistrations()).hasSize(6);
        assertThat(actual.monthlyRegistrations().get(0).month()).isEqualTo(oldestMonth.toString());
        assertThat(actual.monthlyRegistrations().get(0).count()).isEqualTo(3L);
        assertThat(actual.monthlyRegistrations().get(5).month()).isEqualTo(currentMonth.toString());
        assertThat(actual.monthlyRegistrations().get(5).count()).isEqualTo(7L);
        // The 4 in-between months, in order, should all be 0.
        for (int i = 1; i <= 4; i++) {
            assertThat(actual.monthlyRegistrations().get(i).count()).isZero();
        }
    }

    @Test
    void getOverviewComputesEstimatedRevenueOnlyForRegisteredSeatStatuses() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        when(submissionRepository.countRegistrationsByMonthSince(any(LocalDateTime.class))).thenReturn(List.of());
        when(courseService.getUpcomingCourses(4)).thenReturn(List.of());
        // 2 CONFIRMED seats on course 1 (price 10,000,000) + 1 GRADUATED seat on course 2 (price 20,000,000)
        when(submissionRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
                eq(CourseService.REGISTERED_STATUSES), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        sampleSubmission(1L, SubmissionStatus.CONFIRMED),
                        sampleSubmission(1L, SubmissionStatus.CONFIRMED),
                        sampleSubmission(2L, SubmissionStatus.GRADUATED)));
        when(courseRepository.findAllById(any())).thenReturn(List.of(
                sampleCourse(1L, new BigDecimal("10000000")),
                sampleCourse(2L, new BigDecimal("20000000"))));
        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(sampleSettings()));

        DashboardOverviewResponse actual = dashboardService.getOverview();

        // (2 * 10,000,000) + (1 * 20,000,000) = 40,000,000
        assertThat(actual.estimatedRevenueThisMonth()).isEqualByComparingTo(new BigDecimal("40000000"));
    }

    @Test
    void getOverviewReturnsZeroRevenueWhenNoMatchingSubmissions() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        when(submissionRepository.countRegistrationsByMonthSince(any(LocalDateTime.class))).thenReturn(List.of());
        when(courseService.getUpcomingCourses(4)).thenReturn(List.of());
        when(submissionRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
                anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(sampleSettings()));

        DashboardOverviewResponse actual = dashboardService.getOverview();

        assertThat(actual.estimatedRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getOverviewPassesConfiguredLimitToUpcomingCourses() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 7);

        CourseResponse upcoming = new CourseResponse(1L, "B1", LicenseClass.B1, BigDecimal.TEN, 3, 40,
                null, null, null, 30, 0L, CourseAvailabilityStatus.AVAILABLE, LocalDate.now(),
                LocalDateTime.now(), LocalDateTime.now());
        when(submissionRepository.countRegistrationsByMonthSince(any(LocalDateTime.class))).thenReturn(List.of());
        when(courseService.getUpcomingCourses(7)).thenReturn(List.of(upcoming));
        when(submissionRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
                anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(sampleSettings()));

        DashboardOverviewResponse actual = dashboardService.getOverview();

        assertThat(actual.upcomingCourses()).containsExactly(upcoming);
        verify(courseService).getUpcomingCourses(7);
    }

    @Test
    void getSettingsReturnsExistingRowMappedToResponse() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(sampleSettings()));

        DashboardSettingsResponse actual = dashboardService.getSettings();

        assertThat(actual.passRatePercent()).isEqualByComparingTo(new BigDecimal("95.50"));
        assertThat(actual.examCount()).isEqualTo(640);
    }

    @Test
    void getSettingsCreatesDefaultRowWhenMissing() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(dashboardSettingsRepository.saveAndFlush(any(DashboardSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DashboardSettingsResponse actual = dashboardService.getSettings();

        assertThat(actual.passRatePercent()).isNull();
        assertThat(actual.examCount()).isNull();
        verify(dashboardSettingsRepository).saveAndFlush(any(DashboardSettings.class));
    }

    @Test
    void updateSettingsAppliesRequestAndPersists() {
        dashboardService = new DashboardService(submissionRepository, courseRepository, courseService,
                dashboardSettingsRepository, 4);

        DashboardSettings existing = sampleSettings();
        UpdateDashboardSettingsRequest request = new UpdateDashboardSettingsRequest(new BigDecimal("98.20"), 700);

        when(dashboardSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dashboardSettingsRepository.saveAndFlush(existing)).thenReturn(existing);

        DashboardSettingsResponse actual = dashboardService.updateSettings(request);

        assertThat(actual.passRatePercent()).isEqualByComparingTo(new BigDecimal("98.20"));
        assertThat(actual.examCount()).isEqualTo(700);
        assertThat(existing.getPassRatePercent()).isEqualByComparingTo(new BigDecimal("98.20"));
        assertThat(existing.getExamCount()).isEqualTo(700);
        verify(dashboardSettingsRepository).saveAndFlush(existing);
    }

    private static DashboardSettings sampleSettings() {
        DashboardSettings settings = new DashboardSettings();
        settings.setId(1L);
        settings.setPassRatePercent(new BigDecimal("95.50"));
        settings.setExamCount(640);
        return settings;
    }

}
