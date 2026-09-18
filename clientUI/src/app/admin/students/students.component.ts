import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LucideEye } from '@lucide/angular';

import { SubmissionService } from '../../core/services/submission.service';
import { CourseService } from '../../core/services/course.service';
import { Submission, SubmissionStatus } from '../../models/submission.model';
import { Course } from '../../models/course.model';

/** Status filter options for the dropdown, including the "no filter" ALL option. */
type StatusFilter = 'ALL' | SubmissionStatus;

/** Course filter options for the dropdown; `'ALL'` omits the `courseId` param entirely. */
type CourseFilter = 'ALL' | number;

/** Default page size requested from the backend, matching its own default. */
const DEFAULT_PAGE_SIZE = 20;

/** Debounce time (ms) applied to the search input before triggering a new list request. */
const SEARCH_DEBOUNCE_MS = 300;

/**
 * Admin Students page: a server-side paginated, searchable, filterable (status + course) table
 * of submissions with navigation to the submission detail page. Formerly the combined
 * "Dashboard" page — the KPI summary cards that used to live here moved to `OverviewComponent`.
 */
@Component({
  selector: 'app-students',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    LucideEye
  ],
  templateUrl: './students.component.html',
  styleUrl: './students.component.scss'
})
export class StudentsComponent implements OnInit, OnDestroy {
  private readonly submissionService = inject(SubmissionService);
  private readonly courseService = inject(CourseService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** Columns rendered by the submissions MatTable, in display order. */
  readonly displayedColumns: string[] = ['fullName', 'email', 'phone', 'status', 'createdAt', 'action'];

  /** Status filter options rendered in the dropdown. */
  readonly statusOptions: StatusFilter[] = ['ALL', 'PENDING_CONSULTATION', 'CONFIRMED', 'IN_PROGRESS', 'GRADUATED'];

  /** Courses available for the course filter dropdown, loaded once on init. */
  courses: Course[] = [];

  /** Current page of submissions to render in the table. */
  submissions: Submission[] = [];

  /** `true` while a submissions list request is in flight. */
  listLoading = false;

  /** Total number of matching submissions on the backend, used as the paginator's length. */
  totalElements = 0;

  /** Zero-based current page index. */
  pageIndex = 0;

  /** Current page size. */
  pageSize = DEFAULT_PAGE_SIZE;

  /** Reactive control for the search input, debounced before triggering a list reload. */
  readonly searchControl = new FormControl<string>('', { nonNullable: true });

  /** Reactive control for the status filter dropdown. */
  readonly statusControl = new FormControl<StatusFilter>('ALL', { nonNullable: true });

  /** Reactive control for the course filter dropdown. */
  readonly courseControl = new FormControl<CourseFilter>('ALL', { nonNullable: true });

  /** Emits whenever the debounced search term should be applied to the list request. */
  private readonly searchTerm$ = new Subject<string>();

  /** Subscriptions created by this component, torn down in `ngOnDestroy`. */
  private readonly subscriptions = new Subscription();

  /**
   * Wires up the debounced search stream, loads the course filter options, and loads the
   * initial submissions page.
   *
   * @returns void
   */
  ngOnInit(): void {
    this.subscriptions.add(
      this.searchTerm$
        .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged())
        .subscribe(() => {
          this.pageIndex = 0;
          this.loadSubmissions();
        })
    );

    this.subscriptions.add(
      this.searchControl.valueChanges.subscribe((value) => this.searchTerm$.next(value))
    );

    this.subscriptions.add(
      this.statusControl.valueChanges.subscribe(() => {
        this.pageIndex = 0;
        this.loadSubmissions();
      })
    );

    this.subscriptions.add(
      this.courseControl.valueChanges.subscribe(() => {
        this.pageIndex = 0;
        this.loadSubmissions();
      })
    );

    this.loadCourses();
    this.loadSubmissions();
  }

  /**
   * Releases the subscriptions created in `ngOnInit`.
   *
   * @returns void
   */
  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  /**
   * Loads the course list used to populate the course filter dropdown. Keeps a simple flat
   * dropdown of every course (a single page at a generous size) rather than a fancy searchable
   * picker, per this page's scope.
   *
   * @returns void
   */
  loadCourses(): void {
    this.courseService.listCoursesForAdmin(0, 100).subscribe({
      next: (page) => {
        this.courses = page.content;
      },
      error: () => {
        // Non-fatal: the course filter simply stays empty if this fails.
      }
    });
  }

  /**
   * Loads the current page of submissions from the backend, applying the current search term,
   * status filter, and course filter, and shows an error notification on failure.
   *
   * @returns void
   */
  loadSubmissions(): void {
    this.listLoading = true;
    const search = this.searchControl.value.trim();
    const status = this.statusControl.value;
    const courseId = this.courseControl.value;

    this.submissionService
      .listSubmissions(
        this.pageIndex,
        this.pageSize,
        search !== '' ? search : undefined,
        status !== 'ALL' ? status : undefined,
        courseId !== 'ALL' ? courseId : undefined
      )
      .subscribe({
        next: (page) => {
          this.submissions = page.content;
          this.totalElements = page.totalElements;
          this.pageIndex = page.page;
          this.pageSize = page.size;
          this.listLoading = false;
        },
        error: (error: unknown) => {
          this.listLoading = false;
          this.snackBar.open(this.extractErrorMessage(error, 'Failed to load submissions.'), 'Close', {
            duration: 5000
          });
        }
      });
  }

  /**
   * Handles a MatPaginator page-change event by updating the current page/size and
   * reloading submissions from the backend.
   *
   * @param event the paginator's page-change event
   * @returns void
   */
  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadSubmissions();
  }

  /**
   * Navigates to the detail page for the given submission.
   *
   * @param submission the submission to view
   * @returns void
   */
  viewSubmission(submission: Submission): void {
    this.router.navigate(['/admin/submissions', submission.id]);
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
