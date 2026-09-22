/**
 * Summary view of an admin/consultant account, mirroring
 * `com.register.backend.dto.response.AdminUserSummaryResponse`. Never includes `passwordHash`/`role` —
 * the backend deliberately excludes them from this endpoint.
 */
export interface AdminUserSummary {
  id: number;
  username: string;
}
