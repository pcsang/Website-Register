package com.register.backend.service;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.SubmissionMapper;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void createSubmissionSavesAndReturnsMappedResponseWhenRequestIsValid() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                "Jane Doe", "jane@example.com", "0123456789", "Hello", null);

        Submission mappedEntity = new Submission();
        mappedEntity.setFullName("Jane Doe");
        mappedEntity.setEmail("jane@example.com");
        mappedEntity.setPhone("0123456789");
        mappedEntity.setMessage("Hello");

        Submission savedSubmission = new Submission();
        savedSubmission.setId(1L);
        savedSubmission.setFullName("Jane Doe");
        savedSubmission.setEmail("jane@example.com");
        savedSubmission.setPhone("0123456789");
        savedSubmission.setMessage("Hello");
        savedSubmission.setStatus(SubmissionStatus.PENDING_CONSULTATION);

        SubmissionResponse expectedResponse = new SubmissionResponse(
                1L, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionMapper.toEntity(request)).thenReturn(mappedEntity);
        when(submissionRepository.save(mappedEntity)).thenReturn(savedSubmission);
        when(submissionMapper.toResponse(savedSubmission)).thenReturn(expectedResponse);

        SubmissionResponse actual = submissionService.createSubmission(request);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(submissionMapper).toEntity(request);
        verify(submissionRepository).save(mappedEntity);
        verify(submissionMapper).toResponse(savedSubmission);
    }

    @Test
    void createSubmissionForcesStatusToNewBeforeSaving() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                "Jane Doe", "jane@example.com", "0123456789", "Hello", null);

        Submission mappedEntity = new Submission();
        mappedEntity.setStatus(SubmissionStatus.GRADUATED);

        when(submissionMapper.toEntity(request)).thenReturn(mappedEntity);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        submissionService.createSubmission(request);

        ArgumentCaptor<Submission> savedCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(SubmissionStatus.PENDING_CONSULTATION);
    }

    @Test
    void listSubmissionsReturnsMappedPageResponseWhenSearchAndStatusProvided() {
        String search = "jane";
        SubmissionStatus status = SubmissionStatus.PENDING_CONSULTATION;
        Pageable pageable = PageRequest.of(0, 10);

        Submission submission = new Submission();
        submission.setId(1L);
        submission.setFullName("Jane Doe");
        submission.setEmail("jane@example.com");
        submission.setPhone("0123456789");
        submission.setMessage("Hello");
        submission.setStatus(SubmissionStatus.PENDING_CONSULTATION);

        Page<Submission> page = new PageImpl<>(List.of(submission), pageable, 1);

        SubmissionResponse mappedResponse = new SubmissionResponse(
                1L, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionRepository.search(search, status, null, pageable)).thenReturn(page);
        when(submissionMapper.toResponse(submission)).thenReturn(mappedResponse);

        PageResponse<SubmissionResponse> actual = submissionService.listSubmissions(search, status, null, pageable);

        assertThat(actual.content()).containsExactly(mappedResponse);
        assertThat(actual.page()).isEqualTo(0);
        assertThat(actual.size()).isEqualTo(10);
        assertThat(actual.totalElements()).isEqualTo(1L);
        assertThat(actual.totalPages()).isEqualTo(1);
        verify(submissionRepository).search(search, status, null, pageable);
        verify(submissionMapper).toResponse(submission);
    }

    @Test
    void listSubmissionsNormalizesBlankSearchToNullBeforeQuerying() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Submission> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(submissionRepository.search(isNull(), eq(SubmissionStatus.PENDING_CONSULTATION), isNull(), eq(pageable))).thenReturn(emptyPage);

        submissionService.listSubmissions("   ", SubmissionStatus.PENDING_CONSULTATION, null, pageable);

        verify(submissionRepository).search(isNull(), eq(SubmissionStatus.PENDING_CONSULTATION), isNull(), eq(pageable));
    }

    @Test
    void listSubmissionsPassesCourseIdFilterToRepository() {
        Long courseId = 7L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Submission> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(submissionRepository.search(isNull(), isNull(), eq(courseId), eq(pageable))).thenReturn(emptyPage);

        submissionService.listSubmissions(null, null, courseId, pageable);

        verify(submissionRepository).search(isNull(), isNull(), eq(courseId), eq(pageable));
    }

    @Test
    void getSubmissionByIdReturnsMappedResponseWhenSubmissionExists() {
        Long id = 1L;
        Submission submission = new Submission();
        submission.setId(id);
        submission.setFullName("Jane Doe");
        submission.setEmail("jane@example.com");
        submission.setPhone("0123456789");
        submission.setMessage("Hello");
        submission.setStatus(SubmissionStatus.PENDING_CONSULTATION);

        SubmissionResponse expectedResponse = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, LocalDateTime.now(), LocalDateTime.now());

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

    @Test
    void updateStatusUpdatesAndReturnsMappedResponseWhenSubmissionExists() {
        Long id = 1L;
        Submission submission = new Submission();
        submission.setId(id);
        submission.setFullName("Jane Doe");
        submission.setEmail("jane@example.com");
        submission.setPhone("0123456789");
        submission.setMessage("Hello");
        submission.setStatus(SubmissionStatus.PENDING_CONSULTATION);

        SubmissionResponse expectedResponse = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.IN_PROGRESS, null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionRepository.findById(id)).thenReturn(Optional.of(submission));
        when(submissionRepository.saveAndFlush(submission)).thenReturn(submission);
        when(submissionMapper.toResponse(submission)).thenReturn(expectedResponse);

        SubmissionResponse actual = submissionService.updateStatus(id, SubmissionStatus.IN_PROGRESS);

        assertThat(actual).isEqualTo(expectedResponse);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.IN_PROGRESS);
        verify(submissionRepository).findById(id);
        verify(submissionRepository).saveAndFlush(submission);
        verify(submissionMapper).toResponse(submission);
    }

    @Test
    void updateStatusThrowsResourceNotFoundExceptionWhenSubmissionDoesNotExist() {
        Long id = 999L;
        when(submissionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.updateStatus(id, SubmissionStatus.IN_PROGRESS))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Submission not found with id: " + id);

        verify(submissionRepository).findById(id);
        verifyNoInteractions(submissionMapper);
    }

}
