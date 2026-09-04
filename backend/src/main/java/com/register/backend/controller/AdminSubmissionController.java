package com.register.backend.controller;

import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.service.SubmissionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/submissions")
public class AdminSubmissionController {

    private final SubmissionService submissionService;

    public AdminSubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Lists submissions with server-side pagination, optional search, and optional status filtering.
     *
     * @param search   optional case-insensitive substring to match against fullName/email/phone
     * @param status   optional status to filter by
     * @param pageable pagination/sorting bound from the page/size/sort query params (defaults: page 0,
     *                 size 20, sorted by createdAt DESC)
     * @return a page of submissions
     */
    @GetMapping
    public PageResponse<SubmissionResponse> listSubmissions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubmissionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return submissionService.listSubmissions(search, status, pageable);
    }

    /**
     * Retrieves a single submission by ID.
     *
     * @param id the submission ID
     * @return the matching submission
     */
    @GetMapping("/{id}")
    public SubmissionResponse getSubmission(@PathVariable Long id) {
        return submissionService.getSubmissionById(id);
    }

}
