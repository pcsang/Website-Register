package com.register.backend.repository;

import com.register.backend.entity.SubmissionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionNoteRepository extends JpaRepository<SubmissionNote, Long> {

    /**
     * Finds all notes for a given submission, newest first.
     *
     * @param submissionId the submission ID to find notes for
     * @return the matching notes, ordered by {@code createdAt} descending
     */
    List<SubmissionNote> findBySubmissionIdOrderByCreatedAtDesc(Long submissionId);

}
