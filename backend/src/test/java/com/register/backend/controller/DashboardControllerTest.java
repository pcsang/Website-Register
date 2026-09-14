package com.register.backend.controller;

import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// This slice only loads DashboardController, not the app's SecurityConfig. addFilters = false disables
// the security filter chain since this test isn't exercising security at all — access control for
// /api/admin/** is verified end to end in SecurityIntegrationTest instead.
@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void getSummaryReturnsSummaryFromService() throws Exception {
        DashboardSummaryResponse summary = new DashboardSummaryResponse(150L, 30L, 40L, 80L, 12L);
        when(dashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(150))
                .andExpect(jsonPath("$.new").value(30))
                .andExpect(jsonPath("$.inProgress").value(40))
                .andExpect(jsonPath("$.completed").value(80))
                .andExpect(jsonPath("$.submittedToday").value(12));

        verify(dashboardService).getSummary();
    }

}
