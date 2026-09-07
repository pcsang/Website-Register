package com.register.backend.repository;

import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Counts submissions with the given status, executed as a database COUNT query.
     *
     * @param status the status to count
     * @return the number of submissions with that status
     */
    long countByStatus(SubmissionStatus status);

    /**
     * Counts submissions created within the given half-open time range (start inclusive, end exclusive),
     * executed as a database COUNT query.
     *
     * @param start the inclusive start of the range
     * @param end   the exclusive end of the range
     * @return the number of submissions created within the range
     */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

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
