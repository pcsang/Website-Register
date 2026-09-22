import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Payment } from '../../models/payment.model';

/**
 * HTTP client wrapper for the admin payment endpoints (SePay/VietQR tuition payment requests).
 * Centralizes the API base URL (via `environment.apiBaseUrl`) so components never call
 * `HttpClient` directly.
 */
@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private readonly http = inject(HttpClient);

  /** Base URL for all admin submission endpoints, e.g. `http://localhost:8080/api/admin/submissions`. */
  private readonly adminSubmissionsUrl = `${environment.apiBaseUrl}/api/admin/submissions`;

  /**
   * Creates a pending payment for the given submission, or returns the existing pending one if
   * one was already created for it (idempotent on the backend).
   *
   * @param submissionId the submission to create/get a payment for
   * @returns an Observable emitting the pending (or existing) payment
   */
  createOrGetPayment(submissionId: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.adminSubmissionsUrl}/${submissionId}/payment`, {});
  }

  /**
   * Retrieves the most recent payment for the given submission.
   *
   * @param submissionId the submission to look up the payment for
   * @returns an Observable emitting the matching payment (404 if none has ever been created)
   */
  getPayment(submissionId: number): Observable<Payment> {
    return this.http.get<Payment>(`${this.adminSubmissionsUrl}/${submissionId}/payment`);
  }
}
