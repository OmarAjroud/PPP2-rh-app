import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Public pages (login, account links from emails): one centred panel, no navigation. */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  template: `
    <main class="auth">
      <p class="brand">RH App</p>
      <router-outlet />
    </main>
  `,
  styles: `
    .auth {
      min-height: 100vh;
      display: grid;
      align-content: center;
      justify-items: center;
      gap: var(--rh-space-5);
      padding: var(--rh-space-8) max(16px, env(safe-area-inset-right, 0px)) var(--rh-space-8) max(16px, env(safe-area-inset-left, 0px));
      background: var(--rh-bg);
    }
    .brand { margin: 0; font-size: var(--rh-text-xl); font-weight: var(--rh-weight-strong); letter-spacing: var(--rh-tracking-title); }
  `,
})
export class AuthLayout {}
