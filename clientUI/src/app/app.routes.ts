import { Routes } from '@angular/router';
import { LandingPageComponent } from './public/landing/landing-page.component';
import { OverviewComponent } from './admin/overview/overview.component';
import { StudentsComponent } from './admin/students/students.component';
import { CoursesComponent } from './admin/courses/courses.component';
import { SubmissionDetailComponent } from './admin/submission-detail/submission-detail.component';
import { LoginComponent } from './admin/login/login.component';
import { AdminLayoutComponent } from './admin/layout/admin-layout.component';
import { authGuard } from './core/guards/auth.guard';

/**
 * Top-level application routes.
 *
 * - `/` — the public 8-section landing page (public), including the "Đăng ký tư vấn"
 *   registration section that replaced the old standalone `/form` page.
 * - `/form` — redirects to `/`, so old bookmarks/links keep working.
 * - `/admin/login` — the admin login page (public, full-page — no sidebar).
 * - `/admin/overview` — the admin overview page (KPIs, month chart, upcoming courses, recent
 *   registrations; protected by `authGuard`), nested under `AdminLayoutComponent`'s sidebar +
 *   topbar.
 * - `/admin/students` — the full searchable/filterable/paginated submissions table (protected by
 *   `authGuard`), also nested under `AdminLayoutComponent`. Formerly the combined `/admin/dashboard`.
 * - `/admin/courses` — the admin course list + create/edit dialog (protected by `authGuard`),
 *   also nested under `AdminLayoutComponent`.
 * - `/admin/dashboard` — redirects to `/admin/overview`, so old bookmarks/links (and the
 *   `authGuard`'s redirect-to-login-then-back behavior) keep working.
 * - `/admin/submissions/:id` — the admin detail page for a single submission (protected by
 *   `authGuard`), also nested under `AdminLayoutComponent`.
 *
 * Unauthenticated access to a protected route redirects to `/admin/login`.
 */
export const routes: Routes = [
  { path: '', component: LandingPageComponent },
  { path: 'form', redirectTo: '', pathMatch: 'full' },
  { path: 'admin/login', component: LoginComponent },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    children: [
      { path: 'dashboard', redirectTo: 'overview', pathMatch: 'full' },
      { path: 'overview', component: OverviewComponent, canActivate: [authGuard] },
      { path: 'students', component: StudentsComponent, canActivate: [authGuard] },
      { path: 'courses', component: CoursesComponent, canActivate: [authGuard] },
      { path: 'submissions/:id', component: SubmissionDetailComponent, canActivate: [authGuard] }
    ]
  }
];
