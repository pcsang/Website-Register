package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.CreateCourseRequest;
import com.register.backend.dto.request.UpdateCourseRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads AdminCourseController, not the app's SecurityConfig. addFilters = false disables
// the security filter chain since this test isn't exercising security at all — access control for
// /api/admin/** is verified end to end for the existing admin endpoints in SecurityIntegrationTest, and
// the same SecurityConfig rule (`/api/admin/**` -> hasRole("ADMIN")) applies to this new controller too.
@WebMvcTest(AdminCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseService courseService;

    private static CourseResponse sampleResponse(Long id) {
        return new CourseResponse(
                id, "B1 Automatic", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "desc", "Downtown", "Ms. Lan", 30, 10L, CourseAvailabilityStatus.AVAILABLE,
                LocalDate.now().plusMonths(1), LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void listCoursesReturnsPageResponseFromServiceWithFilterParams() throws Exception {
        PageResponse<CourseResponse> pageResponse = new PageResponse<>(List.of(sampleResponse(1L)), 0, 20, 1, 1);

        when(courseService.listCoursesForAdmin(eq(LicenseClass.B1), eq("Downtown"), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/courses")
                        .param("licenseClass", "B1")
                        .param("branch", "Downtown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(courseService).listCoursesForAdmin(eq(LicenseClass.B1), eq("Downtown"), any(Pageable.class));
    }

    @Test
    void listCoursesPassesNullFiltersWhenParamsAreOmitted() throws Exception {
        PageResponse<CourseResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        when(courseService.listCoursesForAdmin(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(courseService).listCoursesForAdmin(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getCourseReturnsCourseWhenExists() throws Exception {
        Long id = 1L;
        when(courseService.getCourseById(id)).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/admin/courses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.licenseClass").value("B1"));

        verify(courseService).getCourseById(id);
    }

    @Test
    void getCourseReturns404WhenCourseDoesNotExist() throws Exception {
        Long id = 999L;
        when(courseService.getCourseById(id))
                .thenThrow(new ResourceNotFoundException("Course not found with id: " + id));

        mockMvc.perform(get("/api/admin/courses/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Course not found with id: " + id));
    }

    @Test
    void createCourseReturns201WithMappedResponseWhenRequestIsValid() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "B1 Automatic", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "desc", "Downtown", "Ms. Lan", 30, LocalDate.now().plusMonths(1));

        when(courseService.createCourse(request)).thenReturn(sampleResponse(1L));

        mockMvc.perform(post("/api/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("B1 Automatic"));

        verify(courseService).createCourse(request);
    }

    @Test
    void createCourseReturns400WhenNameIsBlank() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "desc", "Downtown", "Ms. Lan", 30, LocalDate.now().plusMonths(1));

        mockMvc.perform(post("/api/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(courseService);
    }

    @Test
    void updateCourseReturnsUpdatedCourseWhenRequestIsValid() throws Exception {
        Long id = 1L;
        UpdateCourseRequest request = new UpdateCourseRequest(
                "B1 Automatic v2", LicenseClass.B1, new BigDecimal("13000000"), 3, 40,
                "desc", "Downtown", "Ms. Lan", 28, LocalDate.now().plusMonths(1));

        when(courseService.updateCourse(id, request)).thenReturn(sampleResponse(id));

        mockMvc.perform(patch("/api/admin/courses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(courseService).updateCourse(id, request);
    }

    @Test
    void updateCourseReturns400WhenSeatsTotalIsMissing() throws Exception {
        Long id = 1L;
        String bodyMissingSeatsTotal = """
                {
                  "name": "B1 Automatic",
                  "licenseClass": "B1",
                  "price": 12000000,
                  "durationMonths": 3,
                  "practiceHours": 40,
                  "startDate": "2026-12-01"
                }
                """;

        mockMvc.perform(patch("/api/admin/courses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingSeatsTotal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seatsTotal").exists());

        verifyNoInteractions(courseService);
    }

}
