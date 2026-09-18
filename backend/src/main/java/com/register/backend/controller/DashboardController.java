package com.register.backend.controller;

import com.register.backend.dto.request.UpdateDashboardSettingsRequest;
import com.register.backend.dto.response.DashboardOverviewResponse;
import com.register.backend.dto.response.DashboardSettingsResponse;
import com.register.backend.dto.response.DashboardSummaryResponse;
import com.register.backend.service.DashboardService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Retrieves the admin dashboard summary: total submissions, counts per status, and submissions
     * created today.
     *
     * @return the dashboard summary
     */
    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    /**
     * Retrieves the expanded admin dashboard overview: registrations by month (last 6 months), the
     * upcoming course schedule, an estimated revenue figure, and the current pass-rate settings.
     *
     * @return the dashboard overview
     */
    @GetMapping("/overview")
    public DashboardOverviewResponse getOverview() {
        return dashboardService.getOverview();
    }

    /**
     * Retrieves the current admin-configured dashboard settings (pass rate / exam count).
     *
     * @return the current settings
     */
    @GetMapping("/settings")
    public DashboardSettingsResponse getSettings() {
        return dashboardService.getSettings();
    }

    /**
     * Updates the admin-configured dashboard settings.
     *
     * @param request the validated update request
     * @return the updated settings
     */
    @PatchMapping("/settings")
    public DashboardSettingsResponse updateSettings(@Valid @RequestBody UpdateDashboardSettingsRequest request) {
        return dashboardService.updateSettings(request);
    }

}
