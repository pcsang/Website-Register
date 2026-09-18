import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Course, CreateCourseRequest, LicenseClass, UpdateCourseRequest } from '../../models/course.model';
import { PageResponse } from '../../models/page-response.model';

/**
 * HTTP client wrapper for all course-related backend endpoints (public listing + admin CRUD).
 * Centralizes the API base URL (via `environment.apiBaseUrl`) and query-param construction so
 * components never call `HttpClient` directly.
 */
@Injectable({
  providedIn: 'root'
})
export class CourseService {
  private readonly http = inject(HttpClient);

  /** Base URL for the public course listing endpoint. */
  private readonly coursesUrl = `${environment.apiBaseUrl}/api/courses`;

  /** Base URL for all admin course endpoints. */
  private readonly adminCoursesUrl = `${environment.apiBaseUrl}/api/admin/courses`;

  /**
   * Lists all courses via the public endpoint, soonest-starting first (backend default sort).
   *
   * @param page zero-based page number to request; omitted if not provided (backend default: 0)
   * @param size page size to request; omitted if not provided (backend default: 20)
   * @returns an Observable emitting a page of courses
   */
  listPublicCourses(page?: number, size?: number): Observable<PageResponse<Course>> {
    let params = new HttpParams();
    if (page !== undefined) {
      params = params.set('page', page);
    }
    if (size !== undefined) {
      params = params.set('size', size);
    }
    return this.http.get<PageResponse<Course>>(this.coursesUrl, { params });
  }

  /**
   * Lists courses for the admin area with server-side pagination and optional license-class /
   * branch filtering. Only parameters that are actually provided are sent.
   *
   * @param page         zero-based page number to request; omitted if not provided (backend default: 0)
   * @param size         page size to request; omitted if not provided (backend default: 20)
   * @param licenseClass optional license class to filter by
   * @param branch       optional branch to filter by
   * @returns an Observable emitting a page of courses
   */
  listCoursesForAdmin(
    page?: number,
    size?: number,
    licenseClass?: LicenseClass,
    branch?: string
  ): Observable<PageResponse<Course>> {
    let params = new HttpParams();
    if (page !== undefined) {
      params = params.set('page', page);
    }
    if (size !== undefined) {
      params = params.set('size', size);
    }
    if (licenseClass !== undefined) {
      params = params.set('licenseClass', licenseClass);
    }
    if (branch !== undefined && branch.trim() !== '') {
      params = params.set('branch', branch);
    }
    return this.http.get<PageResponse<Course>>(this.adminCoursesUrl, { params });
  }

  /**
   * Retrieves a single course by ID for the admin area.
   *
   * @param id the course ID
   * @returns an Observable emitting the matching course
   */
  getCourse(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.adminCoursesUrl}/${id}`);
  }

  /**
   * Creates a new course.
   *
   * @param request the course data to create
   * @returns an Observable emitting the created course
   */
  createCourse(request: CreateCourseRequest): Observable<Course> {
    return this.http.post<Course>(this.adminCoursesUrl, request);
  }

  /**
   * Updates an existing course's editable fields (full replacement).
   *
   * @param id      the course ID
   * @param request the full replacement course data
   * @returns an Observable emitting the updated course
   */
  updateCourse(id: number, request: UpdateCourseRequest): Observable<Course> {
    return this.http.patch<Course>(`${this.adminCoursesUrl}/${id}`, request);
  }
}
