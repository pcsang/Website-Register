import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LucidePencil, LucidePlus } from '@lucide/angular';

import { CourseService } from '../../core/services/course.service';
import { Course, LicenseClass } from '../../models/course.model';
import { CourseFormDialogComponent, CourseFormDialogData } from './course-form-dialog/course-form-dialog.component';

/** License class filter options for the dropdown, including the "no filter" ALL option. */
type LicenseClassFilter = 'ALL' | LicenseClass;

/** Default page size requested from the backend, matching its own default. */
const DEFAULT_PAGE_SIZE = 20;

/**
 * Admin Courses page: a server-side paginated, license-class/branch-filterable table of
 * courses (name/branch, start date, teacher, a seats-availability progress bar), with a
 * "+ Add Course" button opening a `MatDialog` create/edit form.
 */
@Component({
  selector: 'app-courses',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    LucidePencil,
    LucidePlus
  ],
  templateUrl: './courses.component.html',
  styleUrl: './courses.component.scss'
})
export class CoursesComponent implements OnInit {
  private readonly courseService = inject(CourseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** License class filter options rendered in the dropdown. */
  readonly licenseClassOptions: LicenseClassFilter[] = ['ALL', 'B1', 'B2', 'C'];

  /** Distinct branch values, derived from a one-time unfiltered load, for the branch dropdown. */
  branches: string[] = [];

  /** Current page of courses to render in the table. */
  courses: Course[] = [];

  /** `true` while a courses list request is in flight. */
  listLoading = false;

  /** Total number of matching courses on the backend, used as the paginator's length. */
  totalElements = 0;

  /** Zero-based current page index. */
  pageIndex = 0;

  /** Current page size. */
  pageSize = DEFAULT_PAGE_SIZE;

  /** Reactive control for the license class filter dropdown. */
  readonly licenseClassControl = new FormControl<LicenseClassFilter>('ALL', { nonNullable: true });

  /** Reactive control for the branch filter dropdown. */
  readonly branchControl = new FormControl<string>('ALL', { nonNullable: true });

  /**
   * Wires up the filter dropdowns, loads the branch filter options, and loads the initial
   * courses page.
   *
   * @returns void
   */
  ngOnInit(): void {
    this.licenseClassControl.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.loadCourses();
    });
    this.branchControl.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.loadCourses();
    });

    this.loadBranchOptions();
    this.loadCourses();
  }

  /**
   * Loads a large unfiltered page of courses once, purely to derive the distinct set of branch
   * values for the branch filter dropdown (a flat dropdown, per this page's scope — the branch
   * filter is an exact-match filter server-side, so a free-text input would be error-prone).
   *
   * @returns void
   */
  loadBranchOptions(): void {
    this.courseService.listCoursesForAdmin(0, 200).subscribe({
      next: (page) => {
        const distinct = new Set<string>();
        for (const course of page.content) {
          if (course.branch) {
            distinct.add(course.branch);
          }
        }
        this.branches = Array.from(distinct).sort();
      },
      error: () => {
        // Non-fatal: the branch filter simply stays empty if this fails.
      }
    });
  }

  /**
   * Loads the current page of courses from the backend, applying the current license-class and
   * branch filters, and shows an error notification on failure.
   *
   * @returns void
   */
  loadCourses(): void {
    this.listLoading = true;
    const licenseClass = this.licenseClassControl.value;
    const branch = this.branchControl.value;

    this.courseService
      .listCoursesForAdmin(
        this.pageIndex,
        this.pageSize,
        licenseClass !== 'ALL' ? licenseClass : undefined,
        branch !== 'ALL' ? branch : undefined
      )
      .subscribe({
        next: (page) => {
          this.courses = page.content;
          this.totalElements = page.totalElements;
          this.pageIndex = page.page;
          this.pageSize = page.size;
          this.listLoading = false;
        },
        error: (error: unknown) => {
          this.listLoading = false;
          this.snackBar.open(this.extractErrorMessage(error, 'Failed to load courses.'), 'Close', {
            duration: 5000
          });
        }
      });
  }

  /**
   * Handles a MatPaginator page-change event by updating the current page/size and
   * reloading courses from the backend.
   *
   * @param event the paginator's page-change event
   * @returns void
   */
  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadCourses();
  }

  /**
   * Opens the create/edit dialog for a new course. On success, reloads the current page (and
   * the branch filter options, since a new branch value may have just been introduced).
   *
   * @returns void
   */
  openCreateDialog(): void {
    this.openCourseDialog(null);
  }

  /**
   * Opens the create/edit dialog pre-filled for an existing course. On success, reloads the
   * current page (and the branch filter options).
   *
   * @param course the course to edit
   * @returns void
   */
  openEditDialog(course: Course): void {
    this.openCourseDialog(course);
  }

  /**
   * Returns the seats-filled percentage (capped at 100) for a course's progress bar.
   *
   * @param course the course to compute the percentage for
   * @returns a value between 0 and 100
   */
  seatsPercent(course: Course): number {
    if (course.seatsTotal <= 0) {
      return 0;
    }
    return Math.min(100, (course.seatsRegistered / course.seatsTotal) * 100);
  }

  /**
   * Maps a course's availability status to the shared `.availability-badge`/progress-bar CSS
   * class suffix.
   *
   * @param course the course to compute the class for
   * @returns the lowercase, hyphenated class suffix (e.g. `"filling-up"`)
   */
  availabilityClass(course: Course): string {
    return course.availabilityStatus.toLowerCase().replace('_', '-');
  }

  /**
   * Opens the shared create/edit `MatDialog` and reloads the courses list (and branch filter
   * options) if the dialog closes with a saved course.
   *
   * @param course the course to edit, or `null` to create a new one
   * @returns void
   */
  private openCourseDialog(course: Course | null): void {
    const dialogRef = this.dialog.open<CourseFormDialogComponent, CourseFormDialogData, Course | undefined>(
      CourseFormDialogComponent,
      { data: { course }, width: '600px', maxWidth: '95vw' }
    );

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open(course ? 'Course updated.' : 'Course created.', 'Close', { duration: 4000 });
        this.loadBranchOptions();
        this.loadCourses();
      }
    });
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
