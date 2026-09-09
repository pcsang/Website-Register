import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateSubmissionRequest, Submission, SubmissionStatus } from '../../models/submission.model';
import { DashboardSummary } from '../../models/dashboard-summary.model';
import { PageResponse } from '../../models/page-response.model';

/**
 * HTTP client wrapper for all submission and dashboard-summary related backend endpoints.
 * Centralizes the API base URL (via `environment.apiBaseUrl`) and query-param construction
 * so components never call `HttpClient` directly.
 */
@Injectable({
  providedIn: 'root'
})
export class SubmissionService {
  private readonly http = inject(HttpClient);

  /** Base URL for all submission-related endpoints, e.g. `http://localhost:8080/api/submissions`. */
  private readonly submissionsUrl = `${environment.apiBaseUrl}/api/submissions`;

  /** Base URL for all admin submission endpoints, e.g. `http://localhost:8080/api/admin/submissions`. */
  private readonly adminSubmissionsUrl = `${environment.apiBaseUrl}/api/admin/submissions`;

  /** URL for the admin dashboard summary endpoint. */
  private readonly dashboardSummaryUrl = `${environment.apiBaseUrl}/api/admin/dashboard/summary`;

  /**
   * Submits a new information-collection form entry.
   *
   * @param request the submission data to create
   * @returns an Observable emitting the created submission (status forced to `NEW` server-side)
   */
  createSubmission(request: CreateSubmissionRequest): Observable<Submission> {
    return this.http.post<Submission>(this.submissionsUrl, request);
  }

  /**
   * Lists submissions for the admin area with server-side pagination, optional search, and
   * optional status filtering. Only parameters that are actually provided are sent — an
   * undefined/empty `search` or `status` is omitted entirely rather than sent as a blank value,
   * since the backend distinguishes "missing" from "blank" for these filters.
   *
   * @param page   zero-based page number to request; omitted if not provided (backend default: 0)
   * @param size   page size to request; omitted if not provided (backend default: 20)
   * @param search optional case-insensitive substring to match against fullName/email/phone
   * @param status optional status to filter by
   * @returns an Observable emitting a page of submissions
   */
  listSubmissions(
    page?: number,
    size?: number,
    search?: string,
    status?: SubmissionStatus
  ): Observable<PageResponse<Submission>> {
    let params = new HttpParams();
    if (page !== undefined) {
      params = params.set('page', page);
    }
    if (size !== undefined) {
      params = params.set('size', size);
    }
    if (search !== undefined && search.trim() !== '') {
      params = params.set('search', search);
    }
    if (status !== undefined) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<Submission>>(this.adminSubmissionsUrl, { params });
  }

  /**
   * Retrieves a single submission by ID for the admin area.
   *
   * @param id the submission ID
   * @returns an Observable emitting the matching submission
   */
  getSubmission(id: number): Observable<Submission> {
    return this.http.get<Submission>(`${this.adminSubmissionsUrl}/${id}`);
  }

  /**
   * Updates the status of a single submission.
   *
   * @param id     the submission ID
   * @param status the new status to apply
   * @returns an Observable emitting the updated submission
   */
  updateStatus(id: number, status: SubmissionStatus): Observable<Submission> {
    return this.http.patch<Submission>(`${this.adminSubmissionsUrl}/${id}/status`, { status });
  }

  /**
   * Retrieves the admin dashboard summary counts.
   *
   * @returns an Observable emitting the dashboard summary
   */
  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(this.dashboardSummaryUrl);
  }
}
