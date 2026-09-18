package com.register.backend.controller;

import com.register.backend.dto.request.CreateCourseRequest;
import com.register.backend.dto.request.UpdateCourseRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.enums.LicenseClass;
import com.register.backend.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/courses")
public class AdminCourseController {

    private final CourseService courseService;

    public AdminCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Lists courses with server-side pagination and optional license class / branch filtering.
     *
     * @param licenseClass optional license class to filter by
     * @param branch       optional branch to filter by
     * @param pageable     pagination/sorting bound from the page/size/sort query params (defaults: page 0,
     *                     size 20, sorted by createdAt DESC)
     * @return a page of courses
     */
    @GetMapping
    public PageResponse<CourseResponse> listCourses(
            @RequestParam(required = false) LicenseClass licenseClass,
            @RequestParam(required = false) String branch,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return courseService.listCoursesForAdmin(licenseClass, branch, pageable);
    }

    /**
     * Retrieves a single course by ID.
     *
     * @param id the course ID
     * @return the matching course
     */
    @GetMapping("/{id}")
    public CourseResponse getCourse(@PathVariable Long id) {
        return courseService.getCourseById(id);
    }

    /**
     * Creates a new course.
     *
     * @param request the validated create request
     * @return the created course, 201
     */
    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse response = courseService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing course's editable fields (full replacement).
     *
     * @param id      the course ID
     * @param request the validated update request
     * @return the updated course
     */
    @PatchMapping("/{id}")
    public CourseResponse updateCourse(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.updateCourse(id, request);
    }

}
