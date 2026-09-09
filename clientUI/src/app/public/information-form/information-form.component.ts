import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { SubmissionService } from '../../core/services/submission.service';
import { CreateSubmissionRequest } from '../../models/submission.model';

/**
 * Public "Information Form" page. Lets an anonymous visitor submit a contact/inquiry
 * entry, which is persisted by the backend as a new `Submission` with status `NEW`.
 */
@Component({
  selector: 'app-information-form',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './information-form.component.html',
  styleUrl: './information-form.component.scss'
})
export class InformationFormComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly submissionService = inject(SubmissionService);
  private readonly snackBar = inject(MatSnackBar);

  /** `true` while the create-submission request is in flight; used to disable the submit button and guard against double submission. */
  submitting = false;

  /**
   * Reactive form for the public submission fields. Validators mirror the backend's
   * `CreateSubmissionRequest` Jakarta Validation constraints exactly: `fullName` is
   * required (max 200 chars); `email`, `phone`, and `message` are all optional, with
   * `email` additionally validated as a well-formed address (and max 255) when present.
   */
  readonly form = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(30)]],
    message: ['', [Validators.maxLength(2000)]]
  });

  /**
   * Submits the form to the backend if it is valid and not already submitting.
   * On success, resets the form and shows a success notification; on failure,
   * shows the error via a snack bar. Guards against re-entrant submission while
   * a request is already in flight.
   *
   * @returns void
   */
  onSubmit(): void {
    if (this.submitting || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const value = this.form.getRawValue();
    const request: CreateSubmissionRequest = {
      fullName: value.fullName ?? '',
      email: value.email?.trim() ? value.email.trim() : undefined,
      phone: value.phone?.trim() ? value.phone.trim() : undefined,
      message: value.message?.trim() ? value.message.trim() : undefined
    };

    this.submissionService.createSubmission(request).subscribe({
      next: () => {
        this.submitting = false;
        this.form.reset();
        this.snackBar.open('Submission sent successfully.', 'Close', { duration: 4000 });
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.snackBar.open(this.extractErrorMessage(error), 'Close', { duration: 5000 });
      }
    });
  }

  /**
   * Extracts a user-facing error message from a failed HTTP request.
   *
   * @param error the error thrown by the HTTP client
   * @returns a human-readable message to display to the user
   */
  private extractErrorMessage(error: unknown): string {
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
    return 'Something went wrong while submitting the form. Please try again.';
  }
}
