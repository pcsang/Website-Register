import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AdminUserSummary } from '../../models/admin-user.model';

/**
 * HTTP client wrapper for admin/consultant account endpoints. Centralizes the API base URL (via
 * `environment.apiBaseUrl`) so components never call `HttpClient` directly.
 */
@Injectable({
  providedIn: 'root'
})
export class AdminUserService {
  private readonly http = inject(HttpClient);

  /** Base URL for all admin user endpoints. */
  private readonly adminUsersUrl = `${environment.apiBaseUrl}/api/admin/users`;

  /**
   * Lists all admin/consultant accounts (no pagination — the account list is small).
   *
   * @returns an Observable emitting every admin user summary
   */
  listAdminUsers(): Observable<AdminUserSummary[]> {
    return this.http.get<AdminUserSummary[]>(this.adminUsersUrl);
  }
}
