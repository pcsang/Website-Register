import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatToolbarModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  /** Exposed for the nav header template to reactively show Login/Logout and the current username. */
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  title = 'clientUI';

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
