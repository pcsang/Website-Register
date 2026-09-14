import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse } from '../../models/auth.model';

/** `localStorage` key under which the current auth state is persisted as JSON. */
const STORAGE_KEY = 'auth';

/** Shape of the auth state persisted to `localStorage` and held in the in-memory signal. */
interface StoredAuth {
  token: string;
  username: string;
  role: string;
}

/**
 * Holds and manages the admin authentication state for the whole app: performs login against
 * the backend, persists the issued JWT (plus username/role) to `localStorage` so a page reload
 * doesn't force a re-login, and exposes reactive signals the nav header and route guard can
 * both read. Never sent to public endpoints — see `authInterceptor` for how/where the token is
 * actually attached to requests.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);

  /** URL for the admin login endpoint. */
  private readonly loginUrl = `${environment.apiBaseUrl}/api/auth/login`;

  /** Current auth state, seeded from `localStorage` on service construction; `null` when logged out. */
  private readonly authState = signal<StoredAuth | null>(this.readStoredAuth());

  /** `true` when an admin is currently logged in. */
  readonly isAuthenticated = computed(() => this.authState() !== null);

  /** The logged-in admin's username, or `null` when logged out. */
  readonly username = computed(() => this.authState()?.username ?? null);

  /**
   * Authenticates against the backend with the given credentials. On success, persists the
   * returned token/username/role so subsequent admin API calls and page reloads stay
   * authenticated until logout or token expiry.
   *
   * @param request the login credentials
   * @returns an Observable emitting the login response
   */
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(this.loginUrl, request)
      .pipe(tap((response) => this.setAuth(response)));
  }

  /**
   * Clears the current auth state, both in memory and in `localStorage`.
   *
   * @returns void
   */
  logout(): void {
    this.authState.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  /**
   * Returns the currently stored JWT, if any.
   *
   * @returns the current bearer token, or `null` if not logged in
   */
  getToken(): string | null {
    return this.authState()?.token ?? null;
  }

  /**
   * Stores a successful login response both in memory and in `localStorage`.
   *
   * @param response the login response returned by the backend
   * @returns void
   */
  private setAuth(response: LoginResponse): void {
    const auth: StoredAuth = {
      token: response.token,
      username: response.username,
      role: response.role
    };
    this.authState.set(auth);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  }

  /**
   * Reads and validates any previously persisted auth state from `localStorage`.
   *
   * @returns the stored auth state if present and well-formed, otherwise `null`
   */
  private readStoredAuth(): StoredAuth | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (
        typeof parsed === 'object' &&
        parsed !== null &&
        typeof (parsed as StoredAuth).token === 'string' &&
        typeof (parsed as StoredAuth).username === 'string' &&
        typeof (parsed as StoredAuth).role === 'string'
      ) {
        return parsed as StoredAuth;
      }
    } catch {
      // Malformed JSON in storage — treat as logged out.
    }
    return null;
  }
}
