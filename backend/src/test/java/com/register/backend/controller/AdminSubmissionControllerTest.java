package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.UpdateSubmissionStatusRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads AdminSubmissionController, not the app's SecurityConfig. addFilters = false
// disables the security filter chain since this test isn't exercising security at all — access control
// for /api/admin/** is verified end to end in SecurityIntegrationTest instead.
@WebMvcTest(AdminSubmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubmissionService submissionService;

    @Test
    void listSubmissionsReturnsPageResponseFromServiceWithSearchAndStatusParams() throws Exception {
        SubmissionResponse submission = new SubmissionResponse(
                1L, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.NEW, LocalDateTime.now(), LocalDateTime.now());
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(submission), 0, 20, 1, 1);

        when(submissionService.listSubmissions(eq("jane"), eq(SubmissionStatus.NEW), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("search", "jane")
                        .param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(submissionService).listSubmissions(eq("jane"), eq(SubmissionStatus.NEW), any(Pageable.class));
    }

    @Test
    void listSubmissionsPassesNullSearchAndStatusWhenParamsAreOmitted() throws Exception {
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        when(submissionService.listSubmissions(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(submissionService).listSubmissions(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getSubmissionReturnsSubmissionWhenExists() throws Exception {
        Long id = 1L;
        SubmissionResponse response = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.NEW, LocalDateTime.now(), LocalDateTime.now());

        when(submissionService.getSubmissionById(id)).thenReturn(response);

        mockMvc.perform(get("/api/admin/submissions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("jane@example.com"));

        verify(submissionService).getSubmissionById(id);
    }

    @Test
    void getSubmissionReturns404WhenSubmissionDoesNotExist() throws Exception {
        Long id = 999L;
        when(submissionService.getSubmissionById(id))
                .thenThrow(new ResourceNotFoundException("Submission not found with id: " + id));

        mockMvc.perform(get("/api/admin/submissions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Submission not found with id: " + id));

        verify(submissionService).getSubmissionById(id);
    }

    @Test
    void updateStatusReturnsUpdatedSubmissionWhenRequestIsValid() throws Exception {
        Long id = 1L;
        UpdateSubmissionStatusRequest request = new UpdateSubmissionStatusRequest(SubmissionStatus.IN_PROGRESS);
        SubmissionResponse response = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.IN_PROGRESS, LocalDateTime.now(), LocalDateTime.now());

        when(submissionService.updateStatus(id, SubmissionStatus.IN_PROGRESS)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/submissions/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(submissionService).updateStatus(id, SubmissionStatus.IN_PROGRESS);
    }

    @Test
    void updateStatusReturns400WhenStatusIsMissing() throws Exception {
        Long id = 1L;

        mockMvc.perform(patch("/api/admin/submissions/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

}
