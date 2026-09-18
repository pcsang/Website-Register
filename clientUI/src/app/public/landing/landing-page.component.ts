import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  LucideCalendarDays,
  LucideCar,
  LucideCircleCheck,
  LucideCircleCheckBig,
  LucideMail,
  LucideMapPin,
  LucidePhone,
  LucideStar,
  LucideUsers
} from '@lucide/angular';

import { CourseService } from '../../core/services/course.service';
import { SubmissionService } from '../../core/services/submission.service';
import { Course, LicenseClass } from '../../models/course.model';
import { CreateSubmissionRequest } from '../../models/submission.model';

/** A Vietnamese-format mobile phone number, e.g. `0912345678` or `+84912345678`. */
const VN_PHONE_PATTERN = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;

/** One step in the "Quy trình" (process) section's 4-step flow. */
interface ProcessStep {
  readonly step: number;
  readonly title: string;
  readonly description: string;
}

/** One card in the "Đánh giá" (reviews) section. */
interface Testimonial {
  readonly name: string;
  readonly role: string;
  readonly quote: string;
  readonly initials: string;
}

/**
 * Public landing page (`/`) — the 8-section marketing/registration page described in
 * `driveup-claude-cli-prompt-design-UI-UX.md` section 2: nav, hero, features, courses,
 * process, reviews, a registration form, and a footer. Replaces the old standalone `/form`
 * page; the registration form here covers the same job (create a `Submission`) plus an
 * optional course selection.
 */
@Component({
  selector: 'app-landing-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    LucideCalendarDays,
    LucideCar,
    LucideCircleCheck,
    LucideCircleCheckBig,
    LucideMail,
    LucideMapPin,
    LucidePhone,
    LucideStar,
    LucideUsers
  ],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss'
})
export class LandingPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly courseService = inject(CourseService);
  private readonly submissionService = inject(SubmissionService);
  private readonly snackBar = inject(MatSnackBar);

  /** License classes offered, in display order, used to drive the courses grid's stable ordering. */
  readonly licenseClassOptions: LicenseClass[] = ['B1', 'B2', 'C'];

  /** The 4 fixed steps rendered in the "Quy trình" section. */
  readonly processSteps: ProcessStep[] = [
    {
      step: 1,
      title: 'Đăng ký thông tin',
      description: 'Điền thông tin đăng ký tư vấn ngay trên website, chỉ mất chưa đến 1 phút.'
    },
    {
      step: 2,
      title: 'Tư vấn & chọn khoá',
      description: 'Tư vấn viên liên hệ lại, hỗ trợ bạn chọn khoá học và hạng bằng phù hợp.'
    },
    {
      step: 3,
      title: 'Đóng học phí',
      description: 'Hoàn tất học phí theo khoá học đã chọn, nhận lịch khai giảng cụ thể.'
    },
    {
      step: 4,
      title: 'Bắt đầu học',
      description: 'Bắt đầu lịch học lý thuyết và thực hành theo thời khoá biểu đã đăng ký.'
    }
  ];

  /** The 3 fixed testimonial cards rendered in the "Đánh giá" section. */
  readonly testimonials: Testimonial[] = [
    {
      name: 'Nguyễn Thị Mai',
      role: 'Học viên hạng B2',
      quote:
        'Giáo viên nhiệt tình, chỉ dẫn tận tâm từng chút một. Mình đậu ngay lần thi đầu tiên, rất hài lòng với DriveUp!',
      initials: 'M'
    },
    {
      name: 'Trần Văn Khoa',
      role: 'Học viên hạng B1',
      quote:
        'Lịch học linh hoạt, dễ dàng sắp xếp giữa công việc. Xe tập lái đời mới nên mình tự tin hơn hẳn khi ra đường.',
      initials: 'K'
    },
    {
      name: 'Lê Hoàng Phúc',
      role: 'Học viên hạng C',
      quote:
        'Thủ tục hồ sơ được hỗ trợ trọn gói, không phải lo lắng gì cả. Đội ngũ tư vấn chuyên nghiệp, nhiệt tình.',
      initials: 'P'
    }
  ];

  /** Star array (fixed length 5) used to render a 5-star rating row via `@for`. */
  readonly stars = [1, 2, 3, 4, 5];

  /** Courses loaded from the real `GET /api/courses` endpoint, rendered in the pricing grid. */
  courses: Course[] = [];

  /** `true` while the initial courses request is in flight. */
  coursesLoading = true;

  /** `true` once the courses request has completed (successfully or not), even with zero results. */
  coursesLoadFailed = false;

  /** `true` while the create-submission request is in flight. */
  submitting = false;

  /** `true` once the registration form has been submitted successfully; hides the form and shows the success state. */
  submitted = false;

  /**
   * Reactive form for the "Đăng ký tư vấn" registration section. Validators are intentionally
   * stricter than the backend's `CreateSubmissionRequest` (which makes `email`/`phone`
   * optional) per this page's own design spec, which requires all 4 fields.
   */
  readonly registrationForm = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    phone: ['', [Validators.required, Validators.pattern(VN_PHONE_PATTERN), Validators.maxLength(30)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    licenseClass: ['' as LicenseClass | '', [Validators.required]]
  });

  /**
   * Loads the public course list on init, used both by the "Khoá học" pricing grid and to
   * resolve a `courseId` for the registration form.
   *
   * @returns void
   */
  ngOnInit(): void {
    this.courseService.listPublicCourses(0, 20).subscribe({
      next: (page) => {
        this.courses = page.content;
        this.coursesLoading = false;
      },
      error: () => {
        this.coursesLoading = false;
        this.coursesLoadFailed = true;
      }
    });
  }

  /**
   * Builds the 3 feature bullet lines shown on a course pricing card, combining the course's
   * real `durationMonths`/`practiceHours` fields with a short static feature phrase per
   * license class (the backend has no "feature list" field to source this from).
   *
   * @param course the course to build feature bullets for
   * @returns an array of up to 3 short bullet strings
   */
  courseFeatures(course: Course): string[] {
    const monthsLabel = `${course.durationMonths} tháng`;
    const bullets: string[] = [monthsLabel];

    if (course.licenseClass === 'C') {
      bullets.push('Thực hành xe tải thực tế');
    } else if (course.licenseClass === 'B2') {
      bullets.push(`${course.practiceHours} giờ thực hành + sa hình`);
    } else {
      bullets.push(`${course.practiceHours} giờ thực hành`);
    }

    if (course.description) {
      bullets.push(course.description);
    }

    return bullets;
  }

  /**
   * Formats a course's price as Vietnamese-locale-grouped digits with a trailing "đ" suffix
   * (e.g. `7.500.000đ`), matching the design mockup's currency format exactly.
   *
   * @param course the course to format the price for
   * @returns the formatted price string
   */
  formattedPrice(course: Course): string {
    return `${course.price.toLocaleString('vi-VN')}đ`;
  }

  /**
   * Whether a course should render with the "most popular" treatment (thicker primary border +
   * floating badge) — applied to the B2 license class, matching the mockup's specific emphasis.
   *
   * @param course the course to check
   * @returns true if the course is the B2 license class
   */
  isPopular(course: Course): boolean {
    return course.licenseClass === 'B2';
  }

  /**
   * Pre-selects a course's license class in the registration form's dropdown, used when a
   * visitor clicks a pricing card's "Chọn khoá học" button (which also anchor-scrolls to the
   * registration section via its `href`).
   *
   * @param licenseClass the license class to pre-select
   * @returns void
   */
  selectCourseClass(licenseClass: LicenseClass): void {
    this.registrationForm.controls.licenseClass.setValue(licenseClass);
  }

  /**
   * Validates and submits the registration form. On success, hides the form and shows the
   * success state; on failure, shows the backend's error message via a snack bar. Guards
   * against re-entrant submission while a request is already in flight.
   *
   * @returns void
   */
  onSubmit(): void {
    if (this.submitting || this.registrationForm.invalid) {
      this.registrationForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const value = this.registrationForm.getRawValue();
    const licenseClass = value.licenseClass as LicenseClass;

    const request: CreateSubmissionRequest = {
      fullName: (value.fullName ?? '').trim(),
      phone: (value.phone ?? '').trim(),
      email: (value.email ?? '').trim(),
      courseId: this.resolveCourseId(licenseClass)
    };

    this.submissionService.createSubmission(request).subscribe({
      next: () => {
        this.submitting = false;
        this.submitted = true;
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.snackBar.open(this.extractErrorMessage(error), 'Đóng', { duration: 5000 });
      }
    });
  }

  /**
   * Resolves a `courseId` for the selected license class by picking the first loaded course
   * that matches it. Returns `undefined` (courseId omitted from the request) if no course of
   * that class is currently loaded, since `CreateSubmissionRequest.courseId` is optional.
   *
   * @param licenseClass the license class selected in the registration form
   * @returns the matching course's ID, or undefined if none is loaded
   */
  private resolveCourseId(licenseClass: LicenseClass): number | undefined {
    return this.courses.find((course) => course.licenseClass === licenseClass)?.id;
  }

  /**
   * Extracts a user-facing error message from a failed HTTP request, falling back to a
   * generic message when the backend's error shape isn't present.
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
    return 'Đã có lỗi xảy ra khi gửi đăng ký. Vui lòng thử lại.';
  }
}
