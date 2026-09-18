package com.register.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.dto.request.UpdateDashboardSettingsRequest;
import com.register.backend.dto.response.DashboardOverviewResponse;
import com.register.backend.dto.response.DashboardSettingsResponse;
import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.dto.response.MonthlyRegistrationCountResponse;
import com.register.backend.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void getSummaryReturnsSummaryFromService() throws Exception {
        DashboardSummaryResponse summary = new DashboardSummaryResponse(150L, 30L, 25L, 40L, 55L, 12L);
        when(dashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(150))
                .andExpect(jsonPath("$.pendingConsultation").value(30))
                .andExpect(jsonPath("$.confirmed").value(25))
                .andExpect(jsonPath("$.inProgress").value(40))
                .andExpect(jsonPath("$.graduated").value(55))
                .andExpect(jsonPath("$.submittedToday").value(12));

        verify(dashboardService).getSummary();
    }

    @Test
    void getOverviewReturnsOverviewFromService() throws Exception {
        DashboardOverviewResponse overview = new DashboardOverviewResponse(
                List.of(new MonthlyRegistrationCountResponse("2026-09", 12L)),
                List.of(),
                new BigDecimal("40000000.00"),
                new DashboardSettingsResponse(new BigDecimal("95.50"), 640, LocalDateTime.now()));
        when(dashboardService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRegistrations[0].month").value("2026-09"))
                .andExpect(jsonPath("$.monthlyRegistrations[0].count").value(12))
                .andExpect(jsonPath("$.estimatedRevenueThisMonth").value(40000000.00))
                .andExpect(jsonPath("$.settings.passRatePercent").value(95.50))
                .andExpect(jsonPath("$.settings.examCount").value(640));

        verify(dashboardService).getOverview();
    }

    @Test
    void getSettingsReturnsSettingsFromService() throws Exception {
        DashboardSettingsResponse settings = new DashboardSettingsResponse(new BigDecimal("95.50"), 640, LocalDateTime.now());
        when(dashboardService.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/admin/dashboard/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passRatePercent").value(95.50))
                .andExpect(jsonPath("$.examCount").value(640));

        verify(dashboardService).getSettings();
    }

    @Test
    void updateSettingsReturnsUpdatedSettingsFromService() throws Exception {
        UpdateDashboardSettingsRequest request = new UpdateDashboardSettingsRequest(new BigDecimal("98.20"), 700);
        DashboardSettingsResponse updated = new DashboardSettingsResponse(new BigDecimal("98.20"), 700, LocalDateTime.now());
        when(dashboardService.updateSettings(request)).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/dashboard/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passRatePercent").value(98.20))
                .andExpect(jsonPath("$.examCount").value(700));

        verify(dashboardService).updateSettings(request);
    }

    @Test
    void updateSettingsReturns400WhenPassRatePercentMissing() throws Exception {
        String invalidJson = "{\"examCount\": 700}";

        mockMvc.perform(patch("/api/admin/dashboard/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSettingsReturns400WhenPassRatePercentOutOfRange() throws Exception {
        String invalidJson = "{\"passRatePercent\": 150, \"examCount\": 700}";

        mockMvc.perform(patch("/api/admin/dashboard/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

}
