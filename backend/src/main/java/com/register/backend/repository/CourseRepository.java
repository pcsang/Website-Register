package com.register.backend.repository;

import com.register.backend.entity.Course;
import com.register.backend.enums.LicenseClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Finds courses matching an optional license class filter and an optional branch filter, both applied
     * at the database level.
     *
     * <p>Only these two fields are filterable for now: the design also calls for filtering by a derived
     * "Còn chỗ/Sắp đầy/Đã đầy" status, but that status is computed at read time from a per-course
     * {@code COUNT} of {@code Submission} rows (see {@code CourseService}/{@code CourseMapper}), not a
     * stored column - so it can't be expressed as a SQL {@code WHERE} predicate here without a more
     * involved query. Left out of phase D2's scope; revisit if a real need for it emerges.
     *
     * @param licenseClass license class to filter by, or {@code null} to include all license classes
     * @param branch       branch to filter by (exact match), or {@code null} to include all branches
     * @param pageable     pagination and sorting information
     * @return a page of matching courses
     */
    @Query("""
            SELECT c FROM Course c
            WHERE (:licenseClass IS NULL OR c.licenseClass = :licenseClass)
              AND (:branch IS NULL OR c.branch = :branch)
            """)
    Page<Course> search(@Param("licenseClass") LicenseClass licenseClass, @Param("branch") String branch, Pageable pageable);

    /**
     * Finds courses whose start date is on or after the given date, ordered soonest-first, capped by the
     * page size of the given {@link Pageable} - used for the dashboard's upcoming course schedule.
     *
     * @param date     the earliest start date to include (typically today)
     * @param pageable used only for its page size/offset here (e.g. {@code PageRequest.of(0, limit)})
     * @return up to {@code pageable}'s page size of matching courses, soonest-starting first
     */
    List<Course> findByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate date, Pageable pageable);

}
