import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCalendarDays,
  LucideClipboardList,
  LucideLayoutDashboard,
  LucideLogOut,
  LucideMenu,
  LucideSearch,
  LucideUsers,
  LucideX
} from '@lucide/angular';

import { AuthService } from '../../core/services/auth.service';

/**
 * Structural shape a routed admin page may optionally expose so this layout's topbar can
 * relay its search input into a shared search box, without the layout inventing any new
 * filtering logic of its own — it only forwards to the page's existing `FormControl`.
 */
interface SearchableRouteComponent {
  searchControl: FormControl<string>;
}

/**
 * Type guard for {@link SearchableRouteComponent}.
 *
 * @param component the component instance activated by the nested `router-outlet`
 * @returns true if the component exposes a `searchControl` reactive form control
 */
function hasSearchControl(component: unknown): component is SearchableRouteComponent {
  return (
    typeof component === 'object' &&
    component !== null &&
    'searchControl' in component &&
    (component as { searchControl: unknown }).searchControl instanceof FormControl
  );
}

/**
 * Admin area shell: a dark left sidebar (navigation) plus a topbar (search relay, current
 * admin, logout) wrapping the routed admin pages (`OverviewComponent`, `StudentsComponent`,
 * `CoursesComponent`, `SubmissionDetailComponent`). Purely a layout/visual wrapper — it reuses
 * `AuthService` and the routed page's own `searchControl` rather than introducing new state or
 * business logic.
 */
@Component({
  selector: 'app-admin-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ReactiveFormsModule,
    LucideCalendarDays,
    LucideClipboardList,
    LucideLayoutDashboard,
    LucideLogOut,
    LucideMenu,
    LucideSearch,
    LucideUsers,
    LucideX
  ],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss'
})
export class AdminLayoutComponent {
  /** Exposed for the topbar template to reactively show the current admin's username. */
  protected readonly authService = inject(AuthService);

  private readonly router = inject(Router);

  /** `true` while the off-canvas sidebar is open on narrow (mobile) viewports. */
  readonly sidebarOpen = signal(false);

  /**
   * The routed page's own search `FormControl`, when the currently activated admin page
   * exposes one (currently only `StudentsComponent`); `null` otherwise, hiding the topbar
   * search box.
   */
  activeSearchControl: FormControl<string> | null = null;

  /**
   * Captures the routed page's search control (if any) when the nested `router-outlet`
   * activates a new component, so the topbar search box can relay into it.
   *
   * @param component the component instance the router just activated
   * @returns void
   */
  onOutletActivate(component: unknown): void {
    this.activeSearchControl = hasSearchControl(component) ? component.searchControl : null;
  }

  /**
   * Clears the relayed search control when the nested `router-outlet` deactivates the
   * current page (e.g. navigating away from the admin area).
   *
   * @returns void
   */
  onOutletDeactivate(): void {
    this.activeSearchControl = null;
  }

  /**
   * Toggles the off-canvas sidebar's visibility on narrow viewports.
   *
   * @returns void
   */
  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  /**
   * Closes the off-canvas sidebar, used after navigating to a menu item on narrow viewports.
   *
   * @returns void
   */
  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  /**
   * Logs the current admin out and navigates to the admin login page.
   *
   * @returns void
   */
  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/admin/login']);
  }
}
