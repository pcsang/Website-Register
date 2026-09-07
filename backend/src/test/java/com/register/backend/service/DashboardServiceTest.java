package com.register.backend.service;

import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private DashboardService dashboardService;

    @Test
    void getSummaryAssemblesCountsFromRepository() {
        dashboardService = new DashboardService(submissionRepository);

        when(submissionRepository.count()).thenReturn(150L);
        when(submissionRepository.countByStatus(SubmissionStatus.NEW)).thenReturn(30L);
        when(submissionRepository.countByStatus(SubmissionStatus.IN_PROGRESS)).thenReturn(40L);
        when(submissionRepository.countByStatus(SubmissionStatus.COMPLETED)).thenReturn(80L);
        when(submissionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(12L);

        DashboardSummaryResponse actual = dashboardService.getSummary();

        assertThat(actual).isEqualTo(new DashboardSummaryResponse(150L, 30L, 40L, 80L, 12L));
        verify(submissionRepository).count();
        verify(submissionRepository).countByStatus(SubmissionStatus.NEW);
        verify(submissionRepository).countByStatus(SubmissionStatus.IN_PROGRESS);
        verify(submissionRepository).countByStatus(SubmissionStatus.COMPLETED);
        verify(submissionRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(LocalDateTime.class), any(LocalDateTime.class));
    }

}
