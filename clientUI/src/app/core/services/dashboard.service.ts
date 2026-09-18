import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DashboardSummary } from '../../models/dashboard-summary.model';
import { DashboardOverview, DashboardSettings, UpdateDashboardSettingsRequest } from '../../models/dashboard-overview.model';

/**
 * HTTP client wrapper for all admin dashboard endpoints (summary, expanded overview, and
 * pass-rate settings). Centralizes the API base URL (via `environment.apiBaseUrl`) so
 * components never call `HttpClient` directly.
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly http = inject(HttpClient);

  /** Base URL for the admin dashboard endpoints. */
  private readonly dashboardUrl = `${environment.apiBaseUrl}/api/admin/dashboard`;

  /**
   * Retrieves the admin dashboard summary counts (used by the Students page's status filter and,
   * historically, the combined dashboard).
   *
   * @returns an Observable emitting the dashboard summary
   */
  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.dashboardUrl}/summary`);
  }

  /**
   * Retrieves the expanded admin dashboard overview: registrations by month, the upcoming course
   * schedule, an estimated revenue figure, and the current pass-rate settings.
   *
   * @returns an Observable emitting the dashboard overview
   */
  getDashboardOverview(): Observable<DashboardOverview> {
    return this.http.get<DashboardOverview>(`${this.dashboardUrl}/overview`);
  }

  /**
   * Retrieves the current admin-configured dashboard settings (pass rate / exam count).
   *
   * @returns an Observable emitting the current settings
   */
  getDashboardSettings(): Observable<DashboardSettings> {
    return this.http.get<DashboardSettings>(`${this.dashboardUrl}/settings`);
  }

  /**
   * Updates the admin-configured dashboard settings.
   *
   * @param request the new settings to apply
   * @returns an Observable emitting the updated settings
   */
  updateDashboardSettings(request: UpdateDashboardSettingsRequest): Observable<DashboardSettings> {
    return this.http.patch<DashboardSettings>(`${this.dashboardUrl}/settings`, request);
  }
}
