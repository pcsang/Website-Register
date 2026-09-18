import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  LucideArrowLeft,
  LucideCalendar,
  LucideCircleAlert,
  LucideFlag,
  LucideMail,
  LucideMessageCircle,
  LucidePhone,
  LucideRefreshCw,
  LucideSearchX,
  LucideUser
} from '@lucide/angular';

import { SubmissionService } from '../../core/services/submission.service';
import { Submission, SubmissionStatus } from '../../models/submission.model';

/** Status dropdown options offered on the detail page. */
const STATUS_OPTIONS: SubmissionStatus[] = ['PENDING_CONSULTATION', 'CONFIRMED', 'IN_PROGRESS', 'GRADUATED'];

/**
 * Admin Submission Detail page: loads a single submission by route `:id`, displays its
 * fields, and lets the admin change and save its status.
 */
@Component({
  selector: 'app-submission-detail',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    LucideArrowLeft,
    LucideCalendar,
    LucideCircleAlert,
    LucideFlag,
    LucideMail,
    LucideMessageCircle,
    LucidePhone,
    LucideRefreshCw,
    LucideSearchX,
    LucideUser
  ],
  templateUrl: './submission-detail.component.html',
  styleUrl: './submission-detail.component.scss'
})
export class SubmissionDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly submissionService = inject(SubmissionService);
  private readonly snackBar = inject(MatSnackBar);

  /** Status dropdown options rendered in the template. */
  readonly statusOptions = STATUS_OPTIONS;

  /** The loaded submission, or `null` while loading/on error. */
  submission: Submission | null = null;

  /** `true` while the initial submission fetch is in flight. */
  loading = false;

  /** `true` when the backend returned 404 for the requested ID. */
  notFound = false;

  /** `true` when the initial fetch failed for a reason other than 404. */
  loadError = false;

  /** `true` while a status update request is in flight. */
  updating = false;

  /** Reactive control for the status dropdown. */
  readonly statusControl = new FormControl<SubmissionStatus>('PENDING_CONSULTATION', { nonNullable: true });

  /**
   * Reads the `id` route parameter and loads the corresponding submission.
   *
   * @returns void
   */
  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam !== null ? Number(idParam) : NaN;
    if (idParam === null || Number.isNaN(id)) {
      this.notFound = true;
      return;
    }
    this.loadSubmission(id);
  }

  /**
   * Loads the submission with the given ID from the backend, handling the loading, not-found,
   * and generic-error states.
   *
   * @param id the submission ID to load
   * @returns void
   */
  loadSubmission(id: number): void {
    this.loading = true;
    this.notFound = false;
    this.loadError = false;

    this.submissionService.getSubmission(id).subscribe({
      next: (submission) => {
        this.submission = submission;
        this.statusControl.setValue(submission.status);
        this.loading = false;
      },
      error: (error: unknown) => {
        this.loading = false;
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notFound = true;
        } else {
          this.loadError = true;
          this.snackBar.open(this.extractErrorMessage(error, 'Failed to load submission.'), 'Close', {
            duration: 5000
          });
        }
      }
    });
  }

  /**
   * Submits the selected status to the backend, updating the displayed submission (including
   * its `updatedAt`) on success and showing a notification on either outcome.
   *
   * @returns void
   */
  updateStatus(): void {
    if (!this.submission || this.updating) {
      return;
    }
    const newStatus = this.statusControl.value;
    this.updating = true;

    this.submissionService.updateStatus(this.submission.id, newStatus).subscribe({
      next: (updated) => {
        this.submission = updated;
        this.statusControl.setValue(updated.status);
        this.updating = false;
        this.snackBar.open('Status updated successfully.', 'Close', { duration: 4000 });
      },
      error: (error: unknown) => {
        this.updating = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to update status.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Returns whether the "Update Status" button should be disabled: while a save is in flight,
   * while the submission hasn't loaded, or when the selected status matches the current one.
   *
   * @returns true if the update button should be disabled
   */
  isUpdateDisabled(): boolean {
    return !this.submission || this.updating || this.statusControl.value === this.submission.status;
  }

  /**
   * Navigates back to the admin Students page.
   *
   * @returns void
   */
  backToDashboard(): void {
    this.router.navigate(['/admin/students']);
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
