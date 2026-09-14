/**
 * Request body for `POST /api/auth/login`, mirroring
 * `com.register.backend.dto.request.LoginRequest`.
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Response body for a successful `POST /api/auth/login`, mirroring
 * `com.register.backend.dto.response.LoginResponse`.
 * `role` is literally `"ROLE_ADMIN"` for this single-role application.
 */
export interface LoginResponse {
  token: string;
  username: string;
  role: string;
}
