package com.register.backend.service;

import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides aggregate counts for the admin dashboard summary.
 */
@Service
public class DashboardService {

    private final SubmissionRepository submissionRepository;

    public DashboardService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * Builds the dashboard summary: total submissions, counts per status, and submissions created today.
     * "Today" is computed as the current day's start-of-day through the next day's start-of-day in the
     * server's local time zone, matching how {@code Submission.createdAt} is populated
     * ({@code LocalDateTime.now()} in {@code @PrePersist}, with no time zone stored).
     *
     * @return the assembled dashboard summary
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        long total = submissionRepository.count();
        long newCount = submissionRepository.countByStatus(SubmissionStatus.NEW);
        long inProgress = submissionRepository.countByStatus(SubmissionStatus.IN_PROGRESS);
        long completed = submissionRepository.countByStatus(SubmissionStatus.COMPLETED);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
        long submittedToday = submissionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startOfToday, startOfTomorrow);

        return new DashboardSummaryResponse(total, newCount, inProgress, completed, submittedToday);
    }

}
