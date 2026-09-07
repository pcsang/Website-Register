package com.register.backend.service;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.SubmissionMapper;
import com.register.backend.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;

    public SubmissionService(SubmissionRepository submissionRepository, SubmissionMapper submissionMapper) {
        this.submissionRepository = submissionRepository;
        this.submissionMapper = submissionMapper;
    }

    @Transactional
    public SubmissionResponse createSubmission(CreateSubmissionRequest request) {
        Submission submission = submissionMapper.toEntity(request);
        submission.setStatus(SubmissionStatus.NEW);
        Submission saved = submissionRepository.save(submission);
        return submissionMapper.toResponse(saved);
    }

    /**
     * Lists submissions with server-side pagination, an optional case-insensitive search across
     * fullName/email/phone, and an optional status filter, both applied at the database level.
     *
     * @param search   substring to match against fullName/email/phone, or blank/{@code null} to skip search filtering
     * @param status   status to filter by, or {@code null} to include all statuses
     * @param pageable pagination and sorting information
     * @return a page of submissions mapped to response DTOs
     */
    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> listSubmissions(String search, SubmissionStatus status, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<Submission> page = submissionRepository.search(normalizedSearch, status, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(submissionMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * Finds a single submission by ID and maps it to a response DTO.
     *
     * @param id the submission ID
     * @return the mapped submission response
     * @throws ResourceNotFoundException if no submission exists with the given ID
     */
    @Transactional(readOnly = true)
    public SubmissionResponse getSubmissionById(Long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
        return submissionMapper.toResponse(submission);
    }

    /**
     * Updates the status of an existing submission and persists the change.
     *
     * @param id     the submission ID
     * @param status the new status to set
     * @return the updated submission mapped to a response DTO
     * @throws ResourceNotFoundException if no submission exists with the given ID
     */
    @Transactional
    public SubmissionResponse updateStatus(Long id, SubmissionStatus status) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
        submission.setStatus(status);
        // saveAndFlush (rather than save) forces the flush - and therefore the @PreUpdate callback that
        // bumps updatedAt - to run immediately, so the mapped response below reflects the new updatedAt
        // instead of a stale value captured before Hibernate's deferred end-of-transaction flush.
        Submission saved = submissionRepository.saveAndFlush(submission);
        return submissionMapper.toResponse(saved);
    }

}
