package com.register.backend.repository;

import com.register.backend.entity.Submission;
import com.register.backend.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
     * Finds submissions matching an optional case-insensitive search term (across fullName, email, phone),
     * an optional status filter, and an optional course filter, all applied at the database level.
     *
     * @param search   substring to match against fullName/email/phone, or {@code null} to skip search filtering
     * @param status   status to filter by, or {@code null} to include all statuses
     * @param courseId course ID to filter by, or {@code null} to include submissions for any (or no) course
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
              AND (:courseId IS NULL OR s.courseId = :courseId)
            """)
    Page<Submission> search(@Param("search") String search, @Param("status") SubmissionStatus status,
                             @Param("courseId") Long courseId, Pageable pageable);

    /**
     * Counts submissions registered for the given course with a status in the given set, executed as a
     * database COUNT query - used to compute a course's {@code seatsRegistered} without storing a
     * separate counter on {@code Course}.
     *
     * @param courseId the course ID to count registrations for
     * @param statuses the set of statuses that count as a registered seat
     * @return the number of matching submissions
     */
    long countByCourseIdAndStatusIn(Long courseId, Collection<SubmissionStatus> statuses);

    /**
     * Finds submissions with a status in the given set, created within the given half-open time range, that
     * have a course assigned - used to compute the dashboard's estimated-revenue figure (each such
     * submission represents one registered seat on its course).
     *
     * @param statuses the set of statuses that count as a registered seat
     * @param start    the inclusive start of the created-at range
     * @param end      the exclusive end of the created-at range
     * @return the matching submissions
     */
    List<Submission> findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCourseIdIsNotNull(
            Collection<SubmissionStatus> statuses, LocalDateTime start, LocalDateTime end);

    /**
     * Counts submissions created in each calendar month from {@code start} onward, grouped by month
     * ({@code date_trunc('month', created_at)}, native PostgreSQL SQL), ordered chronologically. Months
     * with zero submissions are simply absent from the result - the caller fills gaps with zero (see
     * {@code DashboardService}).
     *
     * @param start the inclusive lower bound of {@code created_at} to include
     * @return one row per non-empty month, oldest first
     */
    @Query(value = """
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS count
            FROM submissions
            WHERE created_at >= :start
            GROUP BY date_trunc('month', created_at)
            ORDER BY date_trunc('month', created_at)
            """, nativeQuery = true)
    List<MonthlyRegistrationCountProjection> countRegistrationsByMonthSince(@Param("start") LocalDateTime start);

}
