package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.AssignSubmissionRequest;
import com.register.backend.dto.request.CreateSubmissionNoteRequest;
import com.register.backend.dto.request.UpdateSubmissionStatusRequest;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.dto.response.SubmissionNoteResponse;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                SubmissionStatus.PENDING_CONSULTATION, null, null, LocalDateTime.now(), LocalDateTime.now());
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(submission), 0, 20, 1, 1);

        when(submissionService.listSubmissions(eq("jane"), eq(SubmissionStatus.PENDING_CONSULTATION), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("search", "jane")
                        .param("status", "PENDING_CONSULTATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(submissionService).listSubmissions(eq("jane"), eq(SubmissionStatus.PENDING_CONSULTATION), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listSubmissionsPassesNullSearchAndStatusWhenParamsAreOmitted() throws Exception {
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        when(submissionService.listSubmissions(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(submissionService).listSubmissions(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listSubmissionsPassesCourseIdParamWhenProvided() throws Exception {
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        when(submissionService.listSubmissions(isNull(), isNull(), eq(5L), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(submissionService).listSubmissions(isNull(), isNull(), eq(5L), isNull(), any(Pageable.class));
    }

    @Test
    void listSubmissionsPassesAssignedToIdParamWhenProvided() throws Exception {
        PageResponse<SubmissionResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        when(submissionService.listSubmissions(isNull(), isNull(), isNull(), eq(3L), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/submissions")
                        .param("assignedToId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(submissionService).listSubmissions(isNull(), isNull(), isNull(), eq(3L), any(Pageable.class));
    }

    @Test
    void getSubmissionReturnsSubmissionWhenExists() throws Exception {
        Long id = 1L;
        SubmissionResponse response = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, null, LocalDateTime.now(), LocalDateTime.now());

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
                SubmissionStatus.IN_PROGRESS, null, null, LocalDateTime.now(), LocalDateTime.now());

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

    @Test
    void assignSubmissionReturnsUpdatedSubmissionWhenRequestIsValid() throws Exception {
        Long id = 1L;
        AssignSubmissionRequest request = new AssignSubmissionRequest(5L);
        SubmissionResponse response = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, 5L, LocalDateTime.now(), LocalDateTime.now());

        when(submissionService.assignSubmission(id, 5L)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(5));

        verify(submissionService).assignSubmission(id, 5L);
    }

    @Test
    void assignSubmissionAllowsNullAdminUserIdToUnassign() throws Exception {
        Long id = 1L;
        AssignSubmissionRequest request = new AssignSubmissionRequest(null);
        SubmissionResponse response = new SubmissionResponse(
                id, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.PENDING_CONSULTATION, null, null, LocalDateTime.now(), LocalDateTime.now());

        when(submissionService.assignSubmission(id, null)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").doesNotExist());

        verify(submissionService).assignSubmission(id, null);
    }

    @Test
    void assignSubmissionReturns404WhenAdminUserDoesNotExist() throws Exception {
        Long id = 1L;
        AssignSubmissionRequest request = new AssignSubmissionRequest(999L);

        when(submissionService.assignSubmission(id, 999L))
                .thenThrow(new ResourceNotFoundException("Admin user not found with id: 999"));

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Admin user not found with id: 999"));
    }

    @Test
    void listNotesReturnsNotesFromService() throws Exception {
        Long id = 1L;
        SubmissionNoteResponse note = new SubmissionNoteResponse(
                1L, id, 5L, "consultant1", "Called back", LocalDateTime.now());

        when(submissionService.listNotes(id)).thenReturn(List.of(note));

        mockMvc.perform(get("/api/admin/submissions/{id}/notes", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].authorUsername").value("consultant1"))
                .andExpect(jsonPath("$[0].content").value("Called back"));

        verify(submissionService).listNotes(id);
    }

    @Test
    void listNotesReturns404WhenSubmissionDoesNotExist() throws Exception {
        Long id = 999L;
        when(submissionService.listNotes(id))
                .thenThrow(new ResourceNotFoundException("Submission not found with id: " + id));

        mockMvc.perform(get("/api/admin/submissions/{id}/notes", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void addNoteReturns201WithCreatedNoteWhenRequestIsValid() throws Exception {
        Long id = 1L;
        CreateSubmissionNoteRequest request = new CreateSubmissionNoteRequest("Called back");
        SubmissionNoteResponse response = new SubmissionNoteResponse(
                10L, id, 5L, "consultant1", "Called back", LocalDateTime.now());
        Authentication principal = new UsernamePasswordAuthenticationToken("consultant1", null);

        when(submissionService.addNote(eq(id), eq("Called back"), any(Authentication.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/submissions/{id}/notes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .principal(principal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.authorUsername").value("consultant1"))
                .andExpect(jsonPath("$.content").value("Called back"));

        verify(submissionService).addNote(eq(id), eq("Called back"), any(Authentication.class));
    }

    @Test
    void addNoteReturns400WhenContentIsBlank() throws Exception {
        Long id = 1L;
        CreateSubmissionNoteRequest request = new CreateSubmissionNoteRequest("");

        mockMvc.perform(post("/api/admin/submissions/{id}/notes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

}
