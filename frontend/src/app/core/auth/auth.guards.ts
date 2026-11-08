import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './auth.models';

/** Private pages: without a session, go to the login page and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.connecte() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login and account-link pages: someone already logged in goes to the home page. */
export const inviteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return !auth.connecte() || inject(Router).createUrlTree(['/']);
};

/**
 * Pages reserved to some roles. Hiding is a convenience: the API enforces the same rules.
 * Others go to the home page.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const role = inject(AuthService).profil()?.role;
    return (role !== undefined && roles.includes(role)) || inject(Router).createUrlTree(['/']);
  };
}

/** Employees only see their own record: the employee list sends them to their profile. */
export const listeEmployesGuard: CanActivateFn = () =>
  inject(AuthService).profil()?.role !== 'EMPLOYE' || inject(Router).createUrlTree(['/profile']);
