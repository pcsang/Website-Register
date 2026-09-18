import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  LucideArrowRight,
  LucideBanknote,
  LucideCalendarDays,
  LucideGraduationCap,
  LucideUserPlus
} from '@lucide/angular';

import { DashboardService } from '../../core/services/dashboard.service';
import { SubmissionService } from '../../core/services/submission.service';
import { DashboardOverview, MonthlyRegistrationCount } from '../../models/dashboard-overview.model';
import { Submission } from '../../models/submission.model';

/** Number of most-recent submissions shown in the "Recent registrations" table. */
const RECENT_SUBMISSIONS_PAGE_SIZE = 5;

/**
 * A month chart data point with a pre-computed bar height percentage, ready for a plain
 * flexbox/div bar chart (no charting dependency).
 */
interface ChartBar extends MonthlyRegistrationCount {
  /** Bar height as a percentage (0-100) of the largest count across all months. */
  heightPercent: number;
  /** Short display label derived from `month` (`"yyyy-MM"` -> `"MM/yyyy"`). */
  label: string;
}

/**
 * Admin Overview page: 4 headline KPI cards, a "registrations by month" bar chart (plain
 * flexbox, no charting library), an upcoming-courses list, and a short recent-registrations
 * table — each with a "View all" link to the fuller Students/Courses pages.
 */
@Component({
  selector: 'app-overview',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    LucideArrowRight,
    LucideBanknote,
    LucideCalendarDays,
    LucideGraduationCap,
    LucideUserPlus
  ],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss'
})
export class OverviewComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly submissionService = inject(SubmissionService);
  private readonly snackBar = inject(MatSnackBar);

  /** The loaded dashboard overview, or `null` while loading/on error. */
  overview: DashboardOverview | null = null;

  /** `true` while the overview request is in flight. */
  overviewLoading = false;

  /** Month-by-month registration chart bars, derived from `overview.monthlyRegistrations`. */
  chartBars: ChartBar[] = [];

  /** The most recent submissions (newest first), or `null` while loading/on error. */
  recentSubmissions: Submission[] = [];

  /** `true` while the recent-submissions request is in flight. */
  recentLoading = false;

  /**
   * Loads the dashboard overview and the most recent submissions independently, so a failure
   * in one doesn't block the other.
   *
   * @returns void
   */
  ngOnInit(): void {
    this.loadOverview();
    this.loadRecentSubmissions();
  }

  /**
   * Loads the dashboard overview (KPIs, month chart, upcoming courses, settings) from the
   * backend and shows an error notification on failure.
   *
   * @returns void
   */
  loadOverview(): void {
    this.overviewLoading = true;
    this.dashboardService.getDashboardOverview().subscribe({
      next: (overview) => {
        this.overview = overview;
        this.chartBars = this.buildChartBars(overview.monthlyRegistrations);
        this.overviewLoading = false;
      },
      error: (error: unknown) => {
        this.overviewLoading = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to load dashboard overview.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Loads the most recent submissions (newest first, the backend's default sort) for the
   * "Recent registrations" table and shows an error notification on failure.
   *
   * @returns void
   */
  loadRecentSubmissions(): void {
    this.recentLoading = true;
    this.submissionService.listSubmissions(0, RECENT_SUBMISSIONS_PAGE_SIZE).subscribe({
      next: (page) => {
        this.recentSubmissions = page.content;
        this.recentLoading = false;
      },
      error: (error: unknown) => {
        this.recentLoading = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to load recent registrations.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Returns the current calendar month's registration count — the last (most recent) entry of
   * the chronological `monthlyRegistrations` array — used as the "new students this month" KPI.
   *
   * @returns the current month's registration count, or `0` if not yet loaded
   */
  newStudentsThisMonth(): number {
    const months = this.overview?.monthlyRegistrations ?? [];
    return months.length > 0 ? months[months.length - 1].count : 0;
  }

  /**
   * Formats the estimated-revenue-this-month KPI as a Vietnamese-dong currency string.
   *
   * @returns the formatted revenue string, or `'—'` if the overview hasn't loaded yet
   */
  formattedRevenue(): string {
    if (!this.overview) {
      return '—';
    }
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(
      this.overview.estimatedRevenueThisMonth
    );
  }

  /**
   * Formats the admin-configured pass-rate KPI as a percentage string.
   *
   * @returns the formatted pass rate (e.g. `"98.2%"`), or `'—'` if not yet configured/loaded
   */
  formattedPassRate(): string {
    const passRate = this.overview?.settings.passRatePercent;
    return passRate !== null && passRate !== undefined ? `${passRate}%` : '—';
  }

  /**
   * Builds the plain-flexbox chart's bar data from the raw monthly registration counts,
   * computing each bar's height as a percentage of the largest count in the series.
   *
   * @param months the raw monthly registration counts, chronological, oldest first
   * @returns the same data augmented with a display label and a bar height percentage
   */
  private buildChartBars(months: MonthlyRegistrationCount[]): ChartBar[] {
    const maxCount = Math.max(1, ...months.map((m) => m.count));
    return months.map((m) => ({
      ...m,
      heightPercent: (m.count / maxCount) * 100,
      label: this.formatMonthLabel(m.month)
    }));
  }

  /**
   * Formats a `"yyyy-MM"` month string into a short `"MM/yyyy"` display label.
   *
   * @param month the raw month string, e.g. `"2026-09"`
   * @returns the formatted label, e.g. `"09/2026"`
   */
  private formatMonthLabel(month: string): string {
    const [year, monthNumber] = month.split('-');
    return `${monthNumber}/${year}`;
  }

  /**
   * Extracts a user-facing error message from a failed HTTP request, falling back to a
   * generic message when the backend's error shape isn't present.
   *
   * @param error the error thrown by the HTTP client
   * @param fallback the message to show if no backend message can be extracted
   * @returns a human-readable message to display to the user
   */
  private extractErrorMessage(error: unknown, fallback: string): string {
    if (
      typeof error === 'object' &&
      error !== null &&
      'error' in error &&
      typeof (error as { error?: unknown }).error === 'object' &&
      (error as { error?: { message?: unknown } }).error !== null
    ) {
      const message = (error as { error: { message?: unknown } }).error.message;
      if (typeof message === 'string' && message.trim() !== '') {
        return message;
      }
    }
    return fallback;
  }
}
