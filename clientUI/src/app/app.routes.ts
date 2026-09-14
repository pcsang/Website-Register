import { Routes } from '@angular/router';
import { InformationFormComponent } from './public/information-form/information-form.component';
import { DashboardComponent } from './admin/dashboard/dashboard.component';
import { SubmissionDetailComponent } from './admin/submission-detail/submission-detail.component';
import { LoginComponent } from './admin/login/login.component';
import { authGuard } from './core/guards/auth.guard';

/**
 * Top-level application routes.
 *
 * - `/form` — the public information submission form (public).
 * - `/admin/login` — the admin login page (public).
 * - `/admin/dashboard` — the admin dashboard summary page (protected by `authGuard`).
 * - `/admin/submissions/:id` — the admin detail page for a single submission (protected by
 *   `authGuard`).
 *
 * Unauthenticated access to a protected route redirects to `/admin/login`.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'form', pathMatch: 'full' },
  { path: 'form', component: InformationFormComponent },
  { path: 'admin/login', component: LoginComponent },
  { path: 'admin/dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'admin/submissions/:id', component: SubmissionDetailComponent, canActivate: [authGuard] }
];
