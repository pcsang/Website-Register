package com.register.backend.controller;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> createSubmission(@Valid @RequestBody CreateSubmissionRequest request) {
        SubmissionResponse response = submissionService.createSubmission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
