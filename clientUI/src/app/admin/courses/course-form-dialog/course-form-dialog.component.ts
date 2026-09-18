import { Component, Inject, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { CourseService } from '../../../core/services/course.service';
import { Course, CreateCourseRequest, LicenseClass, UpdateCourseRequest } from '../../../models/course.model';

/** License class dropdown options offered on the form. */
const LICENSE_CLASS_OPTIONS: LicenseClass[] = ['B1', 'B2', 'C'];

/**
 * Data passed into `CourseFormDialogComponent` via `MAT_DIALOG_DATA`. `course` is `null` when
 * the dialog is opened to create a new course, or the course being edited otherwise.
 */
export interface CourseFormDialogData {
  course: Course | null;
}

/**
 * Reactive-form `MatDialog` content for creating or editing a course. Validators mirror the
 * backend's `CreateCourseRequest`/`UpdateCourseRequest` Jakarta Validation constraints exactly.
 * Reuses the same request shape for both create and update, since the backend's update endpoint
 * is a full-field-replacement PATCH.
 */
@Component({
  selector: 'app-course-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule
  ],
  templateUrl: './course-form-dialog.component.html',
  styleUrl: './course-form-dialog.component.scss'
})
export class CourseFormDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly courseService = inject(CourseService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<CourseFormDialogComponent>);

  /** License class dropdown options rendered in the template. */
  readonly licenseClassOptions = LICENSE_CLASS_OPTIONS;

  /** The course being edited, or `null` when creating a new one. */
  readonly editingCourse: Course | null;

  /** `true` while the create/update request is in flight. */
  submitting = false;

  /** Reactive form for the course fields, validators mirroring the backend's request DTOs exactly. */
  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    licenseClass: this.formBuilder.control<LicenseClass | null>(null, [Validators.required]),
    price: [null as number | null, [Validators.required, Validators.min(0)]],
    durationMonths: [null as number | null, [Validators.required, Validators.min(1)]],
    practiceHours: [null as number | null, [Validators.required, Validators.min(1)]],
    description: ['', [Validators.maxLength(2000)]],
    branch: ['', [Validators.maxLength(100)]],
    teacherName: ['', [Validators.maxLength(200)]],
    seatsTotal: [null as number | null, [Validators.required, Validators.min(1)]],
    startDate: ['', [Validators.required]]
  });

  /**
   * Pre-fills the form when editing an existing course.
   *
   * @param data the dialog data injected by `MatDialog.open(...)`
   */
  constructor(@Inject(MAT_DIALOG_DATA) data: CourseFormDialogData) {
    this.editingCourse = data.course;
    if (this.editingCourse) {
      this.form.patchValue({
        name: this.editingCourse.name,
        licenseClass: this.editingCourse.licenseClass,
        price: this.editingCourse.price,
        durationMonths: this.editingCourse.durationMonths,
        practiceHours: this.editingCourse.practiceHours,
        description: this.editingCourse.description ?? '',
        branch: this.editingCourse.branch ?? '',
        teacherName: this.editingCourse.teacherName ?? '',
        seatsTotal: this.editingCourse.seatsTotal,
        startDate: this.editingCourse.startDate
      });
    }
  }

  /**
   * Submits the form if valid and not already submitting — creates a new course or updates the
   * one being edited, depending on `editingCourse`. Closes the dialog with the resulting course
   * on success, or shows an error snack bar on failure.
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
    const request: CreateCourseRequest | UpdateCourseRequest = {
      name: value.name ?? '',
      licenseClass: (value.licenseClass ?? 'B1') as LicenseClass,
      price: value.price ?? 0,
      durationMonths: value.durationMonths ?? 0,
      practiceHours: value.practiceHours ?? 0,
      description: value.description?.trim() ? value.description.trim() : undefined,
      branch: value.branch?.trim() ? value.branch.trim() : undefined,
      teacherName: value.teacherName?.trim() ? value.teacherName.trim() : undefined,
      seatsTotal: value.seatsTotal ?? 0,
      startDate: value.startDate ?? ''
    };

    const request$ = this.editingCourse
      ? this.courseService.updateCourse(this.editingCourse.id, request)
      : this.courseService.createCourse(request);

    request$.subscribe({
      next: (course) => {
        this.submitting = false;
        this.dialogRef.close(course);
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.snackBar.open(this.extractErrorMessage(error), 'Close', { duration: 5000 });
      }
    });
  }

  /**
   * Closes the dialog without saving.
   *
   * @returns void
   */
  onCancel(): void {
    this.dialogRef.close();
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
    return 'Failed to save the course. Please try again.';
  }
}
