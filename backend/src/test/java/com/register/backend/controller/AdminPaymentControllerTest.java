package com.register.backend.controller;

import com.register.backend.dto.response.PaymentResponse;
import com.register.backend.enums.PaymentStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads AdminPaymentController, not the app's SecurityConfig - addFilters = false disables
// the security filter chain since this test isn't exercising security at all, matching the pattern used by
// AdminSubmissionControllerTest.
@WebMvcTest(AdminPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createOrGetPendingPaymentReturns201WithPaymentBody() throws Exception {
        Long submissionId = 1L;
        PaymentResponse response = new PaymentResponse(
                10L, submissionId, new BigDecimal("12000000.00"), PaymentStatus.PENDING, "DUP000010",
                "http://qr.example/img.png", null, LocalDateTime.now(), LocalDateTime.now());

        when(paymentService.createOrGetPendingPayment(submissionId)).thenReturn(response);

        mockMvc.perform(post("/api/admin/submissions/{submissionId}/payment", submissionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.submissionId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentCode").value("DUP000010"));
    }

    @Test
    void createOrGetPendingPaymentReturns404WhenSubmissionDoesNotExist() throws Exception {
        Long submissionId = 999L;
        when(paymentService.createOrGetPendingPayment(submissionId))
                .thenThrow(new ResourceNotFoundException("Submission not found with id: " + submissionId));

        mockMvc.perform(post("/api/admin/submissions/{submissionId}/payment", submissionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Submission not found with id: " + submissionId));
    }

    @Test
    void getPaymentReturns200WithPaymentBody() throws Exception {
        Long submissionId = 1L;
        PaymentResponse response = new PaymentResponse(
                10L, submissionId, new BigDecimal("12000000.00"), PaymentStatus.PAID, "DUP000010",
                "http://qr.example/img.png", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());

        when(paymentService.getPaymentForSubmission(submissionId)).thenReturn(response);

        mockMvc.perform(get("/api/admin/submissions/{submissionId}/payment", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void getPaymentReturns404WhenNoPaymentExists() throws Exception {
        Long submissionId = 999L;
        when(paymentService.getPaymentForSubmission(submissionId))
                .thenThrow(new ResourceNotFoundException("No payment found for submission id: " + submissionId));

        mockMvc.perform(get("/api/admin/submissions/{submissionId}/payment", submissionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

}
