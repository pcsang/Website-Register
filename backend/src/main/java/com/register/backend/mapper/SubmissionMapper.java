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
        submission.setMessage(request.message());
        submission.setCourseId(request.courseId());
        return submission;
    }

    public SubmissionResponse toResponse(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getFullName(),
                submission.getEmail(),
                submission.getPhone(),
                submission.getMessage(),
                submission.getStatus(),
                submission.getCourseId(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }

}
