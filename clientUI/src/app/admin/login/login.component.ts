import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LucideClipboardList, LucideLock, LucideUser } from '@lucide/angular';

import { AuthService } from '../../core/services/auth.service';

/**
 * Admin Login page. Authenticates a username/password against the backend via `AuthService`
 * and, on success, navigates to the admin dashboard.
 */
@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    LucideClipboardList,
    LucideLock,
    LucideUser
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** `true` while the login request is in flight; used to disable the submit button and guard against double submission. */
  submitting = false;

  /** Reactive form for the login fields; both are required (the backend has no constraints beyond "not blank"). */
  readonly form = this.formBuilder.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  /**
   * Submits the login form if it is valid and not already submitting. On success, navigates to
   * the admin dashboard; on failure, shows the error via a snack bar. Guards against re-entrant
   * submission while a request is already in flight.
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

    this.authService
      .login({ username: value.username ?? '', password: value.password ?? '' })
      .subscribe({
        next: () => {
          this.submitting = false;
          this.router.navigate(['/admin/overview']);
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
    return 'Invalid username or password.';
  }
}
