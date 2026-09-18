package com.register.backend.controller;

import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;
import com.register.backend.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads CourseController, not the app's SecurityConfig, so Spring Boot's default security
// auto-configuration would otherwise apply and require authentication for every request. addFilters =
// false disables the security filter chain since this test isn't exercising security at all (GET
// /api/courses is genuinely public).
@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    @Test
    void listCoursesReturnsPageResponseFromService() throws Exception {
        CourseResponse course = new CourseResponse(
                1L, "B1 Automatic", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "desc", "Downtown", "Ms. Lan", 30, 10L, CourseAvailabilityStatus.AVAILABLE,
                LocalDate.now().plusMonths(1), LocalDateTime.now(), LocalDateTime.now());
        PageResponse<CourseResponse> pageResponse = new PageResponse<>(List.of(course), 0, 20, 1, 1);

        when(courseService.listCourses(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(courseService).listCourses(any(Pageable.class));
    }

}
