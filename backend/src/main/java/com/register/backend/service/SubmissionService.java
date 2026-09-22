package com.register.backend.service;

import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionNoteResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.entity.AdminUser;
import com.register.backend.entity.Submission;
import com.register.backend.entity.SubmissionNote;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.SubmissionMapper;
import com.register.backend.repository.AdminUserRepository;
import com.register.backend.repository.SubmissionNoteRepository;
import com.register.backend.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;
    private final AdminUserRepository adminUserRepository;
    private final SubmissionNoteRepository submissionNoteRepository;

    /**
     * Creates the service.
     *
     * @param submissionRepository     used to read/persist submissions
     * @param submissionMapper         used to map submissions to response DTOs
     * @param adminUserRepository      used to validate an assignment target and resolve a note's author
     * @param submissionNoteRepository used to read/persist internal notes
     */
    public SubmissionService(SubmissionRepository submissionRepository, SubmissionMapper submissionMapper,
                              AdminUserRepository adminUserRepository, SubmissionNoteRepository submissionNoteRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionMapper = submissionMapper;
        this.adminUserRepository = adminUserRepository;
        this.submissionNoteRepository = submissionNoteRepository;
    }

    @Transactional
    public SubmissionResponse createSubmission(CreateSubmissionRequest request) {
        Submission submission = submissionMapper.toEntity(request);
        submission.setStatus(SubmissionStatus.PENDING_CONSULTATION);
        Submission saved = submissionRepository.save(submission);
        return submissionMapper.toResponse(saved);
    }

    /**
     * Lists submissions with server-side pagination, an optional case-insensitive search across
     * fullName/email/phone, an optional status filter, an optional course filter, and an optional
     * assigned-admin filter, all applied at the database level.
     *
     * @param search       substring to match against fullName/email/phone, or blank/{@code null} to skip search filtering
     * @param status       status to filter by, or {@code null} to include all statuses
     * @param courseId     course ID to filter by, or {@code null} to include submissions for any (or no) course
     * @param assignedToId admin user ID to filter by, or {@code null} to include submissions assigned to anyone (or no one)
     * @param pageable     pagination and sorting information
     * @return a page of submissions mapped to response DTOs
     */
    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> listSubmissions(String search, SubmissionStatus status, Long courseId,
                                                              Long assignedToId, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<Submission> page = submissionRepository.search(normalizedSearch, status, courseId, assignedToId, pageable);
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

    /**
     * Assigns (or un-assigns) a submission to an admin/consultant account. Assignment is organizational
     * only - it does not restrict which admin can view or edit the submission.
     *
     * @param id          the submission ID
     * @param adminUserId the admin user ID to assign the submission to, or {@code null} to un-assign it
     * @return the updated submission mapped to a response DTO
     * @throws ResourceNotFoundException if no submission exists with the given ID, or if {@code adminUserId}
     *                                    is non-null and no admin user exists with that ID
     */
    @Transactional
    public SubmissionResponse assignSubmission(Long id, Long adminUserId) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));

        if (adminUserId != null) {
            adminUserRepository.findById(adminUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with id: " + adminUserId));
        }

        submission.setAssignedToId(adminUserId);
        // saveAndFlush, same reasoning as updateStatus above - forces the flush so the mapped response
        // reflects the persisted state immediately rather than a value captured before Hibernate's
        // deferred end-of-transaction flush.
        Submission saved = submissionRepository.saveAndFlush(submission);
        return submissionMapper.toResponse(saved);
    }

    /**
     * Lists a submission's internal notes, newest first.
     *
     * @param submissionId the submission ID
     * @return the submission's notes mapped to response DTOs, ordered by {@code createdAt} descending
     * @throws ResourceNotFoundException if no submission exists with the given ID
     */
    @Transactional(readOnly = true)
    public List<SubmissionNoteResponse> listNotes(Long submissionId) {
        if (!submissionRepository.existsById(submissionId)) {
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }

        List<SubmissionNote> notes = submissionNoteRepository.findBySubmissionIdOrderByCreatedAtDesc(submissionId);

        // Resolve every note's author username in one bulk lookup instead of one query per note.
        List<Long> authorIds = notes.stream().map(SubmissionNote::getAuthorId).distinct().toList();
        Map<Long, String> usernamesByAuthorId = adminUserRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getUsername));

        return notes.stream()
                .map(note -> new SubmissionNoteResponse(note.getId(), note.getSubmissionId(), note.getAuthorId(),
                        usernamesByAuthorId.get(note.getAuthorId()), note.getContent(), note.getCreatedAt()))
                .toList();
    }

    /**
     * Adds a new internal note to a submission, authored by the currently authenticated admin user.
     *
     * @param submissionId   the submission ID
     * @param content        the note's text content
     * @param authentication the current request's authenticated principal, whose username identifies the author
     * @return the newly created note mapped to a response DTO
     * @throws ResourceNotFoundException if no submission exists with the given ID, or if the authenticated
     *                                    username doesn't match any admin user
     */
    @Transactional
    public SubmissionNoteResponse addNote(Long submissionId, String content, Authentication authentication) {
        if (!submissionRepository.existsById(submissionId)) {
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }

        AdminUser author = adminUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with username: " + authentication.getName()));

        SubmissionNote note = new SubmissionNote();
        note.setSubmissionId(submissionId);
        note.setAuthorId(author.getId());
        note.setContent(content);

        SubmissionNote saved = submissionNoteRepository.save(note);
        return new SubmissionNoteResponse(saved.getId(), saved.getSubmissionId(), author.getId(),
                author.getUsername(), saved.getContent(), saved.getCreatedAt());
    }

}
