/**
 * Admin dashboard summary counts, mirroring
 * `com.register.backend.dto.response.DashboardSummaryResponse` exactly (4-state shape as of the
 * D2 backend change).
 */
export interface DashboardSummary {
  total: number;
  pendingConsultation: number;
  confirmed: number;
  inProgress: number;
  graduated: number;
  submittedToday: number;
}
