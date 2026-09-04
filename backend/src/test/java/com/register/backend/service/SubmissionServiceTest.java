package com.register.backend.service;

import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.SubmissionMapper;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionMapper submissionMapper;

    @InjectMocks
    private SubmissionService submissionService;

    @Test
    void getSubmissionByIdReturnsMappedResponseWhenSubmissionExists() {
        Long id = 1L;
        Submission submission = new Submission();
        submission.setId(id);
        submission.setFullName("Jane Doe");
        submission.setEmail("jane@example.com");
        submission.setPhone("0123456789");
        submission.setMessage("Hello");
        submission.setStatus(SubmissionStatus.NEW);

        SubmissionResponse expectedResponse = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.NEW, LocalDateTime.now(), LocalDateTime.now());

        when(submissionRepository.findById(id)).thenReturn(Optional.of(submission));
        when(submissionMapper.toResponse(submission)).thenReturn(expectedResponse);

        SubmissionResponse actual = submissionService.getSubmissionById(id);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(submissionRepository).findById(id);
        verify(submissionMapper).toResponse(submission);
    }

    @Test
    void getSubmissionByIdThrowsResourceNotFoundExceptionWhenSubmissionDoesNotExist() {
        Long id = 999L;
        when(submissionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.getSubmissionById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Submission not found with id: " + id);

        verify(submissionRepository).findById(id);
        verifyNoInteractions(submissionMapper);
    }

}
