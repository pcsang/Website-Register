import { Course } from './course.model';

/**
 * One data point in the dashboard overview's "registrations by month" chart, mirroring
 * `com.register.backend.dto.response.MonthlyRegistrationCountResponse` exactly.
 */
export interface MonthlyRegistrationCount {
  /** The calendar month, formatted `"yyyy-MM"` (e.g. `"2026-09"`). */
  month: string;
  count: number;
}

/**
 * The admin-configurable, non-transactional dashboard settings, mirroring
 * `com.register.backend.dto.response.DashboardSettingsResponse` exactly. `passRatePercent`/
 * `examCount` are `null` until an admin sets them.
 */
export interface DashboardSettings {
  passRatePercent: number | null;
  examCount: number | null;
  updatedAt: string;
}

/**
 * The admin dashboard's expanded overview, mirroring
 * `com.register.backend.dto.response.DashboardOverviewResponse` exactly.
 */
export interface DashboardOverview {
  /** Submission counts for each of the last 6 calendar months (including the current one), oldest first. */
  monthlyRegistrations: MonthlyRegistrationCount[];
  /** Courses starting today or later, soonest first, capped server-side. */
  upcomingCourses: Course[];
  /** An estimate, not real transactional/payment data — see the backend Javadoc for the calculation rule. */
  estimatedRevenueThisMonth: number;
  settings: DashboardSettings;
}

/**
 * Request body for updating the admin-configured dashboard settings, mirroring
 * `com.register.backend.dto.request.UpdateDashboardSettingsRequest` exactly.
 */
export interface UpdateDashboardSettingsRequest {
  passRatePercent: number;
  examCount?: number;
}
