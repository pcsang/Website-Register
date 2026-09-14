package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.CreateSubmissionRequest;
import com.register.backend.dto.response.SubmissionResponse;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads SubmissionController, not the app's SecurityConfig, so Spring Boot's default
// security auto-configuration would otherwise apply and require authentication for every request.
// addFilters = false disables the security filter chain since this test isn't exercising security at all
// (POST /api/submissions is genuinely public and covered end to end by SecurityIntegrationTest).
@WebMvcTest(SubmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubmissionService submissionService;

    @Test
    void createSubmissionReturns201WithMappedResponseWhenRequestIsValid() throws Exception {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                "Jane Doe", "jane@example.com", "0123456789", "Hello");
        SubmissionResponse response = new SubmissionResponse(
                1L, "Jane Doe", "jane@example.com", "0123456789", "Hello",
                SubmissionStatus.NEW, LocalDateTime.now(), LocalDateTime.now());

        when(submissionService.createSubmission(request)).thenReturn(response);

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.status").value("NEW"));

        verify(submissionService).createSubmission(request);
    }

    @Test
    void createSubmissionReturns400WhenFullNameIsBlank() throws Exception {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                "", "jane@example.com", "0123456789", "Hello");

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists());

        verifyNoInteractions(submissionService);
    }

    @Test
    void createSubmissionReturns400WhenEmailIsInvalid() throws Exception {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                "Jane Doe", "not-an-email", "0123456789", "Hello");

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(submissionService);
    }

}
