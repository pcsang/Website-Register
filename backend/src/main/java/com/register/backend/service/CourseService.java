package com.register.backend.service;

import com.register.backend.dto.request.CreateCourseRequest;
import com.register.backend.dto.request.UpdateCourseRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.entity.Course;
import com.register.backend.enums.LicenseClass;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.CourseMapper;
import com.register.backend.repository.CourseRepository;
import com.register.backend.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class CourseService {

    /**
     * Submission statuses that count as a "registered seat" for a course's {@code seatsRegistered} - a
     * {@code PENDING_CONSULTATION} submission is an inquiry only, not yet a confirmed registration.
     *
     * <p>Public (not just used internally here) so {@code DashboardService} can reuse the exact same rule
     * for its estimated-revenue calculation rather than duplicating it and risking drift.
     */
    public static final Set<SubmissionStatus> REGISTERED_STATUSES =
            EnumSet.of(SubmissionStatus.CONFIRMED, SubmissionStatus.IN_PROGRESS, SubmissionStatus.GRADUATED);

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final SubmissionRepository submissionRepository;

    public CourseService(CourseRepository courseRepository, CourseMapper courseMapper,
                          SubmissionRepository submissionRepository) {
        this.courseRepository = courseRepository;
        this.courseMapper = courseMapper;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Creates a new course.
     *
     * @param request the validated create request
     * @return the created course mapped to a response DTO
     */
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        Course course = courseMapper.toEntity(request);
        Course saved = courseRepository.save(course);
        return courseMapper.toResponse(saved, seatsRegisteredFor(saved.getId()));
    }

    /**
     * Lists all courses with server-side pagination, unfiltered - intended for the public course listing.
     *
     * @param pageable pagination and sorting information
     * @return a page of courses mapped to response DTOs
     */
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listCourses(Pageable pageable) {
        Page<Course> page = courseRepository.findAll(pageable);
        return toPageResponse(page);
    }

    /**
     * Lists courses with server-side pagination and optional license class / branch filters - intended for
     * the admin course listing.
     *
     * @param licenseClass license class to filter by, or {@code null} to include all license classes
     * @param branch       branch to filter by, or {@code null} to include all branches
     * @param pageable     pagination and sorting information
     * @return a page of courses mapped to response DTOs
     */
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listCoursesForAdmin(LicenseClass licenseClass, String branch, Pageable pageable) {
        Page<Course> page = courseRepository.search(licenseClass, branch, pageable);
        return toPageResponse(page);
    }

    /**
     * Finds a single course by ID and maps it to a response DTO.
     *
     * @param id the course ID
     * @return the mapped course response
     * @throws ResourceNotFoundException if no course exists with the given ID
     */
    @Transactional(readOnly = true)
    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
        return courseMapper.toResponse(course, seatsRegisteredFor(id));
    }

    /**
     * Updates an existing course's editable fields and persists the change.
     *
     * @param id      the course ID
     * @param request the validated update request
     * @return the updated course mapped to a response DTO
     * @throws ResourceNotFoundException if no course exists with the given ID
     */
    @Transactional
    public CourseResponse updateCourse(Long id, UpdateCourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
        courseMapper.applyUpdate(course, request);
        Course saved = courseRepository.saveAndFlush(course);
        return courseMapper.toResponse(saved, seatsRegisteredFor(id));
    }

    /**
     * Finds courses whose start date is today or in the future, ordered soonest-first, capped at the given
     * limit - intended for the dashboard overview's upcoming course schedule.
     *
     * @param limit maximum number of courses to return
     * @return up to {@code limit} upcoming courses mapped to response DTOs, soonest-starting first
     */
    @Transactional(readOnly = true)
    public List<CourseResponse> getUpcomingCourses(int limit) {
        List<Course> courses = courseRepository.findByStartDateGreaterThanEqualOrderByStartDateAsc(
                LocalDate.now(), PageRequest.of(0, limit));
        return courses.stream()
                .map(course -> courseMapper.toResponse(course, seatsRegisteredFor(course.getId())))
                .toList();
    }

    /**
     * Maps a page of {@link Course} entities into the generic {@link PageResponse} envelope, computing each
     * course's live {@code seatsRegistered} count individually (one {@code COUNT} query per course, not a
     * hot path for this admin/public listing size - same simplicity-over-micro-optimization judgment call
     * as {@code DashboardService}'s multiple COUNT queries).
     *
     * @param page the page of entities to map
     * @return the mapped page response
     */
    private PageResponse<CourseResponse> toPageResponse(Page<Course> page) {
        return new PageResponse<>(
                page.getContent().stream()
                        .map(course -> courseMapper.toResponse(course, seatsRegisteredFor(course.getId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * Counts how many submissions currently count as a registered seat for the given course - a
     * {@code COUNT} query, not a stored counter, so it can never drift out of sync with the real
     * {@code Submission} rows.
     *
     * @param courseId the course ID
     * @return the number of registered seats for the course
     */
    private long seatsRegisteredFor(Long courseId) {
        return submissionRepository.countByCourseIdAndStatusIn(courseId, REGISTERED_STATUSES);
    }

}
