import { Component, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { RhApi } from '../core/api/rh-api';
import { Notifications } from '../core/notifications/notifications';

const DUREE = 8_000;

/** The notification just pushed by the stream, bottom right; closes by itself after 8 s. */
@Component({
  selector: 'app-toast-notification',
  template: `
    <div class="zone" role="status" aria-live="polite">
      @if (notifs.derniere(); as n) {
        <div class="toast" (mouseenter)="survol.set(true)" (mouseleave)="survol.set(false)" (focusin)="survol.set(true)" (focusout)="survol.set(false)">
          <i class="pi pi-bell" aria-hidden="true"></i>
          <div class="texte">
            <b>{{ n.titre }}</b>
            <span>{{ n.message }}</span>
          </div>
          <div class="actions">
            @if (n.lien) { <button type="button" class="voir" (click)="voir()">Voir</button> }
            <button type="button" class="fermer" aria-label="Fermer la notification" (click)="notifs.fermerToast()"><i class="pi pi-times" aria-hidden="true"></i></button>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .zone { position: fixed; right: 16px; bottom: 16px; z-index: 50; width: min(380px, calc(100vw - 32px)); }
    .toast {
      display: flex;
      align-items: flex-start;
      gap: var(--rh-space-3);
      padding: var(--rh-space-4);
      border: 1px solid var(--rh-line);
      border-radius: var(--rh-radius);
      background: var(--rh-surface);
      color: var(--rh-ink);
      box-shadow: var(--rh-shadow-overlay);
      animation: entree var(--rh-base) var(--rh-ease);
    }
    .toast > i { margin-top: 3px; color: var(--rh-accent-text); }
    .texte { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .texte span { color: var(--rh-ink-2); font-size: var(--rh-text-sm); overflow-wrap: anywhere; }
    .actions { display: flex; align-items: center; gap: var(--rh-space-1); }
    button { border: 0; background: none; font: inherit; cursor: pointer; border-radius: var(--rh-radius-sm); }
    .voir { padding: 4px 8px; color: var(--rh-accent-text); font-weight: 600; }
    .voir:hover { background: var(--rh-surface-2); }
    .fermer { width: 32px; height: 32px; color: var(--rh-muted); }
    .fermer:hover { color: var(--rh-ink); background: var(--rh-surface-2); }
    @keyframes entree { from { opacity: 0; transform: translateY(6px); } }
    @media (prefers-reduced-motion: reduce) { .toast { animation: none; } }
  `,
})
export class ToastNotification {
  protected readonly notifs = inject(Notifications);
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  protected readonly survol = signal(false);

  constructor() {
    // Each new notification restarts the 8-second timer; it pauses while the pointer or keyboard focus is on the toast
    effect(onCleanup => {
      const n = this.notifs.derniere();
      if (!n || this.survol()) return;
      const t = setTimeout(() => untracked(() => { if (this.notifs.derniere() === n) this.notifs.fermerToast(); }), DUREE);
      onCleanup(() => clearTimeout(t));
    });
  }

  protected async voir(): Promise<void> {
    const n = this.notifs.derniere();
    if (!n?.lien) return;
    this.notifs.fermerToast();
    if (!n.lue) {
      this.api.marquerLue(n.id).subscribe({ next: () => this.notifs.resynchroniser(), error: () => this.notifs.resynchroniser() });
    }
    await this.router.navigateByUrl(n.lien);
  }
}
