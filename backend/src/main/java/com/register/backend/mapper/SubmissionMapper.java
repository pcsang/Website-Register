package com.register.backend.mapper;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.Submission;
import org.springframework.stereotype.Component;

@Component
public class SubmissionMapper {

    public Submission toEntity(CreateSubmissionRequest request) {
        Submission submission = new Submission();
        submission.setFullName(request.fullName());
        submission.setEmail(request.email());
        submission.setPhone(request.phone());
        submission.setCompany(request.company());
        submission.setPosition(request.position());
        submission.setMessage(request.message());
        return submission;
    }

    public SubmissionResponse toResponse(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getFullName(),
                submission.getEmail(),
                submission.getPhone(),
                submission.getCompany(),
                submission.getPosition(),
                submission.getMessage(),
                submission.getStatus(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }

}
