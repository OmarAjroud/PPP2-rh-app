import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

const AUTH = `${environment.apiUrl}/api/auth/`;
/** Auth endpoints whose 401 means "wrong credentials or token", never "renew and retry". */
const SANS_RENOUVELLEMENT = ['login', 'refresh', 'logout', 'activation', 'mot-de-passe-oublie', 'reinitialisation'];

/**
 * Whether a request goes to our API (and so may carry the token). With an absolute API address (dev server)
 * the origin must match exactly; with the same-origin API (production behind nginx, apiUrl '') only our own
 * /api and /actuator paths qualify, never another site.
 */
export function versApi(url: string, base: string = environment.apiUrl): boolean {
  if (base) return url === base || url.startsWith(base + '/');
  return url.startsWith('/api/') || url.startsWith('/actuator/');
}

function avecJeton(req: HttpRequest<unknown>, jeton: string | null): HttpRequest<unknown> {
  return jeton ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;
}

/**
 * Adds the access token to API calls. On 401 it renews the session once and replays the request;
 * when renewal fails the user is sent to the login page, back to where they were afterwards.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!versApi(req.url)) return next(req);
  const auth = inject(AuthService);
  const router = inject(Router);
  const renouvelable = !SANS_RENOUVELLEMENT.some(p => req.url === AUTH + p);

  return next(avecJeton(req, auth.accessToken)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || !renouvelable || !auth.session()) {
        return throwError(() => err);
      }
      return from(auth.refresh()).pipe(
        catchError(() => {
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url, expired: 1 } });
          return throwError(() => err);
        }),
        switchMap(jeton => next(avecJeton(req, jeton))),
      );
    }),
  );
};
