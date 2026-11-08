import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Public pages (login, forgotten password, account links): split screen.
 * Left, the navy panel with the product promise and a calculation sample (the app's signature);
 * right, the routed form.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  template: `
    <div class="split">
      <section class="marque" aria-label="RH App">
        <p class="logo">RH<span>·</span>App</p>
        <div class="promesse">
          <h2>Les congés, les équipes, sans zone grise.</h2>
          <p>Chaque chiffre montre son calcul, chaque demande dit qui décide et quand.</p>
          <dl class="exemple" aria-label="Exemple de calcul">
            <dt>Jours en semaine</dt><dd>5</dd>
            <dt>Jeu. 17 · férié</dt><dd>− 1</dd>
            <dt class="total">Jours ouvrables</dt><dd class="total">4</dd>
          </dl>
        </div>
        <p class="pied">Stage 2026 · données de démonstration</p>
      </section>
      <main class="formulaire">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    .split { min-height: 100vh; display: grid; grid-template-columns: 1fr 1fr; background: var(--rh-bg); }
    .marque {
      display: flex; flex-direction: column; justify-content: space-between; gap: 40px;
      padding: 56px 64px;
      background: radial-gradient(700px 520px at 0 0, #2a3d85, transparent 70%), radial-gradient(500px 400px at 100% 100%, #16245a, transparent 70%), var(--rh-rail);
      color: var(--rh-rail-ink);
    }
    .logo { font: 700 26px var(--rh-font-display); letter-spacing: -0.02em; color: #fff; }
    .logo span { color: var(--rh-rail-accent); }
    h2 { font: 700 48px/1.05 var(--rh-font-display); letter-spacing: -0.035em; color: #fff; max-width: 13ch; }
    .promesse p { margin-top: 18px; max-width: 42ch; color: #c3cbe6; font-size: var(--rh-text-lg); }
    .exemple {
      margin: 34px 0 0; width: min(360px, 100%);
      display: grid; grid-template-columns: 1fr auto; gap: 4px 20px;
      padding: 16px 18px; border: 1px solid rgb(255 255 255 / 0.12); border-radius: 14px; background: rgb(255 255 255 / 0.04);
      font: 13px var(--rh-font-mono); color: #c3cbe6;
    }
    .exemple dt, .exemple dd { margin: 0; }
    .exemple dd { text-align: right; }
    .exemple .total { color: #fff; }
    .pied { font-size: var(--rh-text-sm); color: var(--rh-rail-muted); }
    .formulaire { display: grid; place-items: center; padding: 48px 24px; }
    @media (max-width: 960px) {
      .split { grid-template-columns: minmax(0, 1fr); }
      .marque { padding: 28px max(16px, env(safe-area-inset-left, 0px)); gap: 16px; }
      .promesse p, .exemple, .pied { display: none; }
      h2 { font-size: 28px; max-width: none; }
      .formulaire { place-items: start center; padding: 32px 16px 48px; }
    }
  `,
})
export class AuthLayout {}
