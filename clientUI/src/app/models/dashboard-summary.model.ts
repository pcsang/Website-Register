/**
 * Admin dashboard summary counts, mirroring
 * `com.register.backend.dto.response.DashboardSummaryResponse`.
 * NOTE: the backend's `newCount` record component is serialized under the JSON key `"new"`
 * (via `@JsonProperty("new")`), so the wire key — and this property — is `new`, not `newCount`.
 */
export interface DashboardSummary {
  total: number;
  new: number;
  inProgress: number;
  completed: number;
  submittedToday: number;
}
