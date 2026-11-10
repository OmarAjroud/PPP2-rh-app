import { Component, ElementRef, Injector, afterNextRender, computed, effect, inject, untracked, viewChild } from '@angular/core';
import { Inactivite } from '../core/auth/inactivite';

/**
 * One minute before the logout for inactivity: a modal warning with the seconds left.
 * The keyboard goes to "Rester connecté"; the dialog is announced once (alertdialog), not every second.
 */
@Component({
  selector: 'app-avertissement-inactivite',
  template: `
    @if (visible()) {
      <div class="voile">
        <div class="dialogue" role="alertdialog" aria-modal="true" aria-labelledby="inactif-titre" aria-describedby="inactif-texte">
          <h2 id="inactif-titre">Toujours là ?</h2>
          <p id="inactif-texte">Sans activité, vous serez déconnecté dans <b class="decompte">{{ inactivite.restant() }} s</b>, pour protéger les données de l’application.</p>
          <div class="actions">
            <button type="button" class="secondaire" (click)="inactivite.deconnecter()">Se déconnecter</button>
            <button #rester type="button" class="principal" (click)="inactivite.rester()">Rester connecté</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .voile { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: var(--rh-space-4); background: color-mix(in srgb, #000 45%, transparent); }
    .dialogue { width: min(420px, 100%); display: grid; gap: var(--rh-space-3); padding: var(--rh-space-5); border: 1px solid var(--rh-line); border-radius: var(--rh-radius-lg, 12px); background: var(--rh-surface); color: var(--rh-ink); box-shadow: var(--rh-shadow-lg, 0 20px 50px rgb(0 0 0 / .3)); }
    h2 { margin: 0; font-size: var(--rh-text-lg); font-weight: 600; }
    p { margin: 0; color: var(--rh-ink-2); }
    .decompte { white-space: nowrap; font-weight: 600; font-variant-numeric: tabular-nums; color: var(--rh-ink); }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--rh-space-2); }
    button { min-height: var(--rh-control-height, 40px); padding: 0 16px; border-radius: var(--rh-radius-sm); font: inherit; font-weight: 500; cursor: pointer; }
    .secondaire { border: 1px solid var(--rh-line-strong); background: var(--rh-surface-2); color: var(--rh-ink); }
    .principal { border: 0; background: var(--rh-accent); color: var(--rh-accent-ink); }
    button:focus-visible { outline: 2px solid var(--rh-accent); outline-offset: 2px; }
  `,
})
export class AvertissementInactivite {
  protected readonly inactivite = inject(Inactivite);
  private readonly injector = inject(Injector);
  private readonly bouton = viewChild<ElementRef<HTMLButtonElement>>('rester');
  protected readonly visible = computed(() => this.inactivite.restant() !== null);
  /** Where the keyboard was before the warning: it goes back there when the warning closes. */
  private avant: HTMLElement | null = null;

  constructor() {
    // Focus once when the warning appears (not on every second of the countdown)
    effect(() => {
      const visible = this.visible();
      untracked(() => {
        if (visible) {
          this.avant = document.activeElement instanceof HTMLElement && document.activeElement !== document.body ? document.activeElement : null;
          afterNextRender(() => this.bouton()?.nativeElement.focus(), { injector: this.injector });
        } else if (this.avant) {
          const cible = this.avant;
          this.avant = null;
          afterNextRender(() => { if (cible.isConnected) cible.focus(); }, { injector: this.injector });
        }
      });
    });
  }
}
