import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

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
