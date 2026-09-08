import { Routes } from '@angular/router';
import { InformationFormComponent } from './public/information-form/information-form.component';
import { DashboardComponent } from './admin/dashboard/dashboard.component';
import { SubmissionDetailComponent } from './admin/submission-detail/submission-detail.component';

/**
 * Top-level application routes.
 *
 * - `/form` — the public information submission form.
 * - `/admin/dashboard` — the admin dashboard summary page.
 * - `/admin/submissions/:id` — the admin detail page for a single submission.
 *
 * All routed components are standalone placeholder pages for now; real UI/logic lands in later phases.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'form', pathMatch: 'full' },
  { path: 'form', component: InformationFormComponent },
  { path: 'admin/dashboard', component: DashboardComponent },
  { path: 'admin/submissions/:id', component: SubmissionDetailComponent }
];
