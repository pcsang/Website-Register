package com.register.backend.controller;

import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.service.CourseService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated course listing endpoint - intended for a future public landing page to display
 * available courses/pricing.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Lists all courses with server-side pagination, soonest-starting first.
     *
     * @param pageable pagination/sorting bound from the page/size/sort query params (defaults: page 0,
     *                 size 20, sorted by startDate ASC)
     * @return a page of courses
     */
    @GetMapping
    public PageResponse<CourseResponse> listCourses(
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return courseService.listCourses(pageable);
    }

}
