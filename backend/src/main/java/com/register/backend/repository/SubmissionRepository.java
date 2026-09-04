package com.register.backend.repository;

import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Finds submissions matching an optional case-insensitive search term (across fullName, email, phone)
     * and an optional status filter, both applied at the database level.
     *
     * @param search   substring to match against fullName/email/phone, or {@code null} to skip search filtering
     * @param status   status to filter by, or {@code null} to include all statuses
     * @param pageable pagination and sorting information
     * @return a page of matching submissions
     */
    @Query("""
            SELECT s FROM Submission s
            WHERE (:search IS NULL
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(s.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(s.phone) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<Submission> search(@Param("search") String search, @Param("status") SubmissionStatus status, Pageable pageable);

}
