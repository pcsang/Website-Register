import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateSubmissionRequest, Submission, SubmissionStatus } from '../../models/submission.model';
import { PageResponse } from '../../models/page-response.model';
import { CreateSubmissionNoteRequest, SubmissionNote } from '../../models/submission-note.model';

/**
 * HTTP client wrapper for all submission-related backend endpoints. Centralizes the API base URL
 * (via `environment.apiBaseUrl`) and query-param construction so components never call
 * `HttpClient` directly. Dashboard-related endpoints live in `DashboardService`.
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
   * Lists submissions for the admin area with server-side pagination, optional search, optional
   * status filtering, and optional course filtering. Only parameters that are actually provided
   * are sent — an undefined/empty `search`/`status`/`courseId` is omitted entirely rather than
   * sent as a blank value, since the backend distinguishes "missing" from "blank" for these
   * filters.
   *
   * @param page     zero-based page number to request; omitted if not provided (backend default: 0)
   * @param size     page size to request; omitted if not provided (backend default: 20)
   * @param search       optional case-insensitive substring to match against fullName/email/phone
   * @param status       optional status to filter by
   * @param courseId     optional course ID to filter by
   * @param assignedToId optional assigned admin/consultant account ID to filter by
   * @returns an Observable emitting a page of submissions
   */
  listSubmissions(
    page?: number,
    size?: number,
    search?: string,
    status?: SubmissionStatus,
    courseId?: number,
    assignedToId?: number
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
    if (courseId !== undefined) {
      params = params.set('courseId', courseId);
    }
    if (assignedToId !== undefined) {
      params = params.set('assignedToId', assignedToId);
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
   * Assigns (or un-assigns, when passing `null`) a submission to an admin/consultant account.
   *
   * @param id           the submission ID
   * @param adminUserId  the admin/consultant account ID to assign to, or `null` to un-assign
   * @returns an Observable emitting the updated submission
   */
  assignSubmission(id: number, adminUserId: number | null): Observable<Submission> {
    return this.http.patch<Submission>(`${this.adminSubmissionsUrl}/${id}/assign`, { adminUserId });
  }

  /**
   * Lists the internal notes on a submission's timeline, newest first (the backend's ordering).
   *
   * @param id the submission ID
   * @returns an Observable emitting the submission's notes
   */
  listNotes(id: number): Observable<SubmissionNote[]> {
    return this.http.get<SubmissionNote[]>(`${this.adminSubmissionsUrl}/${id}/notes`);
  }

  /**
   * Adds a new internal note to a submission's timeline, authored as the current admin user.
   *
   * @param id      the submission ID
   * @param content the note's text content
   * @returns an Observable emitting the created note
   */
  addNote(id: number, content: string): Observable<SubmissionNote> {
    const request: CreateSubmissionNoteRequest = { content };
    return this.http.post<SubmissionNote>(`${this.adminSubmissionsUrl}/${id}/notes`, request);
  }
}
