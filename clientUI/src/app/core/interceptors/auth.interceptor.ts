import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/** Requests whose URL starts with this prefix are treated as admin API calls. */
const ADMIN_API_PREFIX = `${environment.apiBaseUrl}/api/admin/`;

/**
 * Functional HTTP interceptor for the admin API surface. Attaches
 * `Authorization: Bearer <token>` only to requests whose URL starts with
 * `${environment.apiBaseUrl}/api/admin/` — the public form (`/api/submissions`), the health
 * check (`/api/health`), and the login endpoint itself (`/api/auth/login`) never match this
 * prefix, so the token is never sent to them. Also acts as the app's global 401 handler for
 * admin requests: on a 401 response (missing/invalid/expired token), it clears the stored auth
 * state and redirects to `/admin/login`, since the token is unusable regardless of which admin
 * call triggered the failure — cheaper and more consistent than handling it in every component.
 *
 * @param req the outgoing request
 * @param next the next handler in the interceptor chain
 * @returns the (possibly modified) request's response stream
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAdminRequest = req.url.startsWith(ADMIN_API_PREFIX);
  const token = authService.getToken();

  const outgoingReq =
    isAdminRequest && token !== null
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(outgoingReq).pipe(
    catchError((error: unknown) => {
      if (isAdminRequest && error instanceof HttpErrorResponse && error.status === 401) {
        authService.logout();
        router.navigate(['/admin/login']);
      }
      return throwError(() => error);
    })
  );
};
