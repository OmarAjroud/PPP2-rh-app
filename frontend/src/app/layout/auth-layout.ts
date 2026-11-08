import { Component, DestroyRef, ViewEncapsulation, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from '../core/theme/theme.service';

/**
 * Public pages (login, forgotten password, account links): one centred panel on a deep
 * background lit by a soft violet halo. Always dark, whatever theme the app uses: the layout
 * forces the dark theme on <html> while it is shown (PrimeNG declares its component variables
 * on :root, so a dark subtree alone cannot restyle them) and gives the choice back on leaving.
 * This is the only place the system allows a halo (DESIGN.md, Login).
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="auth">
      <div class="auth-halo" aria-hidden="true"></div>
      <header class="auth-marque"><span class="auth-logo" aria-hidden="true"></span>RH App</header>
      <main class="auth-panneau">
        <router-outlet />
      </main>
      <p class="auth-mention">Stage 2026 · données de démonstration</p>
    </div>
  `,
  styles: `
    .auth {
      position: relative;
      isolation: isolate;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--rh-space-6);
      padding: 48px 16px;
      background: var(--rh-bg);
      color: var(--rh-ink);
      font-size: var(--rh-text-lg);
      overflow: hidden;
    }
    .auth-halo {
      position: absolute;
      z-index: -1;
      inset: -30vh -20vw auto;
      height: 75vh;
      background:
        radial-gradient(closest-side at 50% 60%, rgb(124 110 255 / 0.28), transparent),
        radial-gradient(closest-side at 30% 40%, rgb(94 106 210 / 0.22), transparent 80%),
        radial-gradient(closest-side at 72% 45%, rgb(96 165 250 / 0.12), transparent 80%);
      filter: blur(20px);
      pointer-events: none;
    }
    .auth-marque { display: inline-flex; align-items: center; gap: 10px; font-weight: 600; font-size: 18px; }
    .auth-logo { width: 26px; height: 26px; border-radius: 7px; background: linear-gradient(135deg, var(--rh-indigo-300), var(--rh-indigo-500)); box-shadow: 0 0 24px rgb(124 110 255 / 0.5); }
    .auth-panneau {
      width: min(440px, 100%);
      padding: 36px;
      border: 1px solid var(--rh-line);
      border-radius: var(--rh-radius-lg);
      background: color-mix(in srgb, var(--rh-surface) 82%, transparent);
      backdrop-filter: blur(12px);
      box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.05), 0 24px 60px -20px rgb(0 0 0 / 0.8);
    }
    .auth-panneau > :not(router-outlet) { display: block; }
    .auth-mention { font-size: var(--rh-text-md); color: var(--rh-muted); }

    /* Forms rendered inside the panel (connexion, mot de passe oublié, liens de compte) */
    .auth-titre { font: 600 28px/1.2 var(--rh-font); letter-spacing: -0.02em; }
    .auth-sous-titre { margin-top: 6px; color: var(--rh-muted); }
    .auth-form { display: grid; gap: 18px; margin-top: 28px; }
    /* Login reads at 15px: larger than the dense app (13px), it is a first impression */
    .auth .champ { gap: 8px; }
    .auth .champ > label { font-size: 14px; }
    .auth .champ :is(.aide, .err) { font-size: 13px; }
    .auth :is(.p-inputtext, .p-password-input) { height: 42px; font-size: 15px; padding-inline: 12px; }
    .auth .auth-form .p-button { height: 42px; font-size: 15px; }
    .auth-form .p-button { width: 100%; justify-content: center; }
    .auth .auth-form .p-button:not(.p-button-secondary):not(.p-button-outlined):not(.p-button-text):not(.p-button-danger) {
      background: var(--rh-ink);
      border-color: var(--rh-ink);
      color: var(--rh-bg);
    }
    .auth .auth-form .p-button:not(.p-button-secondary):not(.p-button-outlined):not(.p-button-text):not(.p-button-danger):not(:disabled):hover {
      background: var(--rh-ink-2);
      border-color: var(--rh-ink-2);
    }
    .auth-liens { display: flex; justify-content: flex-end; gap: var(--rh-space-3); font-size: 14px; }
    .auth-liens a { font-weight: 500; text-decoration: none; }
    .auth-liens a:hover { text-decoration: underline; }
    .auth-pied { margin-top: 28px; padding-top: 18px; border-top: 1px solid var(--rh-line); font-size: 13px; line-height: 1.8; color: var(--rh-muted); }
    .auth-pied code { color: var(--rh-ink-2); background: var(--rh-surface-2); border: 1px solid var(--rh-line); border-radius: 4px; padding: 1px 5px; margin-right: 4px; white-space: nowrap; }

    @media (max-width: 600px) {
      .auth { justify-content: flex-start; padding-top: 32px; }
      .auth-panneau { padding: 24px; }
    }
    @media (prefers-reduced-transparency: reduce) {
      .auth-panneau { background: var(--rh-surface); backdrop-filter: none; }
    }
  `,
})
export class AuthLayout {
  constructor() {
    const theme = inject(ThemeService);
    theme.forcerSombre(true);
    inject(DestroyRef).onDestroy(() => theme.forcerSombre(false));
  }
}
