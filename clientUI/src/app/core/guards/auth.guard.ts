import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Route guard protecting the admin area. Allows navigation when the user is currently
 * authenticated; otherwise redirects to `/admin/login`.
 *
 * @returns `true` if authenticated, or a `UrlTree` redirecting to `/admin/login` otherwise
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/admin/login']);
};
