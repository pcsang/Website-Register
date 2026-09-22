import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
  LucideUser,
  LucideUserCheck
} from '@lucide/angular';

import { SubmissionService } from '../../core/services/submission.service';
import { AdminUserService } from '../../core/services/admin-user.service';
import { Submission, SubmissionStatus } from '../../models/submission.model';
import { AdminUserSummary } from '../../models/admin-user.model';
import { SubmissionNote } from '../../models/submission-note.model';

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
    MatInputModule,
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
    LucideUser,
    LucideUserCheck
  ],
  templateUrl: './submission-detail.component.html',
  styleUrl: './submission-detail.component.scss'
})
export class SubmissionDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly submissionService = inject(SubmissionService);
  private readonly adminUserService = inject(AdminUserService);
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

  /** Admin/consultant accounts available for the "Assigned to" dropdown, loaded once on init. */
  adminUsers: AdminUserSummary[] = [];

  /** `true` while the admin user list request is in flight. */
  adminUsersLoading = false;

  /** `true` while an assignment update request is in flight. */
  assigning = false;

  /** Reactive control for the "Assigned to" dropdown; `null` represents "Unassigned". */
  readonly assignControl = new FormControl<number | null>(null);

  /** The loaded submission's internal notes, newest first (the backend's ordering). */
  notes: SubmissionNote[] = [];

  /** `true` while the notes list request is in flight. */
  notesLoading = false;

  /** `true` while a new note is being submitted. */
  addingNote = false;

  /** Reactive control for the new-note textarea. */
  readonly noteControl = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });

  /**
   * Reads the `id` route parameter and loads the corresponding submission, its notes, and the
   * admin user list independently, so a failure in one doesn't block the others.
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
    this.loadNotes(id);
    this.loadAdminUsers();
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
        this.assignControl.setValue(submission.assignedToId);
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
   * Loads the admin/consultant account list used to populate the "Assigned to" dropdown.
   *
   * @returns void
   */
  loadAdminUsers(): void {
    this.adminUsersLoading = true;
    this.adminUserService.listAdminUsers().subscribe({
      next: (adminUsers) => {
        this.adminUsers = adminUsers;
        this.adminUsersLoading = false;
      },
      error: (error: unknown) => {
        this.adminUsersLoading = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to load admin users.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Submits the selected "Assigned to" value to the backend, updating the displayed submission
   * (including its `updatedAt`) on success and showing a notification on either outcome.
   *
   * @returns void
   */
  assignSubmission(): void {
    if (!this.submission || this.assigning) {
      return;
    }
    const adminUserId = this.assignControl.value;
    this.assigning = true;

    this.submissionService.assignSubmission(this.submission.id, adminUserId).subscribe({
      next: (updated) => {
        this.submission = updated;
        this.assignControl.setValue(updated.assignedToId);
        this.assigning = false;
        this.snackBar.open('Assignment updated successfully.', 'Close', { duration: 4000 });
      },
      error: (error: unknown) => {
        this.assigning = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to update assignment.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Returns whether the "Assign" button should be disabled: while a save is in flight, while the
   * submission hasn't loaded, or when the selected value matches the current assignment.
   *
   * @returns true if the assign button should be disabled
   */
  isAssignDisabled(): boolean {
    return !this.submission || this.assigning || this.assignControl.value === this.submission.assignedToId;
  }

  /**
   * Resolves the currently loaded submission's `assignedToId` against the loaded admin user list
   * for display in the detail grid.
   *
   * @returns the assigned admin user's username, or `'—'` if unassigned or not yet resolvable
   */
  assignedUsername(): string {
    if (!this.submission || this.submission.assignedToId === null) {
      return '—';
    }
    const assignedUser = this.adminUsers.find((adminUser) => adminUser.id === this.submission?.assignedToId);
    return assignedUser?.username ?? '—';
  }

  /**
   * Loads the internal notes for the submission with the given ID, newest first.
   *
   * @param id the submission ID whose notes to load
   * @returns void
   */
  loadNotes(id: number): void {
    this.notesLoading = true;
    this.submissionService.listNotes(id).subscribe({
      next: (notes) => {
        this.notes = notes;
        this.notesLoading = false;
      },
      error: (error: unknown) => {
        this.notesLoading = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to load notes.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Submits the current note textarea content as a new internal note, reloading the notes list
   * and clearing the textarea on success.
   *
   * @returns void
   */
  addNote(): void {
    if (!this.submission || this.addingNote) {
      return;
    }
    const content = this.noteControl.value.trim();
    if (content === '') {
      return;
    }
    this.addingNote = true;

    this.submissionService.addNote(this.submission.id, content).subscribe({
      next: (note) => {
        this.notes = [note, ...this.notes];
        this.noteControl.setValue('');
        this.addingNote = false;
      },
      error: (error: unknown) => {
        this.addingNote = false;
        this.snackBar.open(this.extractErrorMessage(error, 'Failed to add note.'), 'Close', {
          duration: 5000
        });
      }
    });
  }

  /**
   * Returns whether the "Add note" button should be disabled: while blank or while a note is
   * already being submitted.
   *
   * @returns true if the add-note button should be disabled
   */
  isAddNoteDisabled(): boolean {
    return this.addingNote || this.noteControl.value.trim() === '';
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
