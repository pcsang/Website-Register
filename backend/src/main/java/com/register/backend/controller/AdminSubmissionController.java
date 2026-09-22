package com.register.backend.controller;

import com.register.backend.dto.request.AssignSubmissionRequest;
import com.register.backend.dto.request.CreateSubmissionNoteRequest;
import com.register.backend.dto.request.UpdateSubmissionStatusRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionNoteResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/submissions")
public class AdminSubmissionController {

    private final SubmissionService submissionService;

    public AdminSubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Lists submissions with server-side pagination, optional search, optional status filtering, optional
     * course filtering, and optional assigned-admin filtering.
     *
     * @param search       optional case-insensitive substring to match against fullName/email/phone
     * @param status       optional status to filter by
     * @param courseId     optional course ID to filter by
     * @param assignedToId optional admin user ID to filter by
     * @param pageable     pagination/sorting bound from the page/size/sort query params (defaults: page 0,
     *                     size 20, sorted by createdAt DESC)
     * @return a page of submissions
     */
    @GetMapping
    public PageResponse<SubmissionResponse> listSubmissions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long assignedToId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return submissionService.listSubmissions(search, status, courseId, assignedToId, pageable);
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

    /**
     * Updates the status of a single submission.
     *
     * @param id      the submission ID
     * @param request the new status to apply
     * @return the updated submission
     */
    @PatchMapping("/{id}/status")
    public SubmissionResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateSubmissionStatusRequest request) {
        return submissionService.updateStatus(id, request.status());
    }

    /**
     * Assigns (or un-assigns, if {@code adminUserId} is {@code null}) a submission to an admin/consultant
     * account.
     *
     * @param id      the submission ID
     * @param request the admin user ID to assign to, or {@code null} to un-assign
     * @return the updated submission
     */
    @PatchMapping("/{id}/assign")
    public SubmissionResponse assignSubmission(@PathVariable Long id, @Valid @RequestBody AssignSubmissionRequest request) {
        return submissionService.assignSubmission(id, request.adminUserId());
    }

    /**
     * Lists a submission's internal notes, newest first.
     *
     * @param id the submission ID
     * @return the submission's notes
     */
    @GetMapping("/{id}/notes")
    public List<SubmissionNoteResponse> listNotes(@PathVariable Long id) {
        return submissionService.listNotes(id);
    }

    /**
     * Adds a new internal note to a submission, authored by the current authenticated admin.
     *
     * @param id             the submission ID
     * @param request        the note's content
     * @param authentication the current request's authenticated principal
     * @return the newly created note, 201
     */
    @PostMapping("/{id}/notes")
    public ResponseEntity<SubmissionNoteResponse> addNote(@PathVariable Long id,
                                                           @Valid @RequestBody CreateSubmissionNoteRequest request,
                                                           Authentication authentication) {
        SubmissionNoteResponse response = submissionService.addNote(id, request.content(), authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
