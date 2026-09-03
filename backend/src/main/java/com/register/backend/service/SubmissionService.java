package com.register.backend.service;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.mapper.SubmissionMapper;
import com.register.backend.repository.SubmissionRepository;
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

}
