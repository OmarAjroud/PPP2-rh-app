import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Logged out after this long without a key press, click, scroll or touch, in any tab. */
export const DELAI_INACTIVITE = 15 * 60_000;
/** The warning shows this long before. */
export const PREAVIS = 60_000;
/** Last activity (ms), shared by every tab through localStorage. */
export const CLE_ACTIVITE = 'rh.activite';

const EVENEMENTS = ['keydown', 'pointerdown', 'wheel', 'touchstart', 'scroll'] as const;
/** Activity is written at most this often (a scroll fires many events). */
const ECRITURE_MIN = 5_000;

/**
 * Closes the session of someone who walked away: a shared computer, an open tab left for the night
 * (the live notifications would otherwise keep renewing the session forever).
 * Activity in one tab keeps every tab alive. One minute before, `restant` gives the seconds left so the
 * warning can offer to stay.
 */
@Injectable({ providedIn: 'root' })
export class Inactivite {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Seconds left while the warning shows, otherwise null. */
  readonly restant = signal<number | null>(null);

  private minuterie: ReturnType<typeof setInterval> | null = null;
  private derniere = 0;
  private ecrite = 0;
  private enCours = false;
  private readonly surActivite = () => this.noter(false);

  demarrer(): void {
    if (this.minuterie) return;
    this.enCours = false;
    this.noter(true);
    for (const e of EVENEMENTS) document.addEventListener(e, this.surActivite, { passive: true, capture: true });
    this.minuterie = setInterval(() => this.verifier(), 1000);
  }

  arreter(): void {
    if (this.minuterie) clearInterval(this.minuterie);
    this.minuterie = null;
    for (const e of EVENEMENTS) document.removeEventListener(e, this.surActivite, { capture: true });
    this.restant.set(null);
  }

  /** "Rester connecté". */
  rester(): void {
    this.noter(true);
    this.restant.set(null);
  }

  /** "Se déconnecter" from the warning. */
  deconnecter(): void {
    this.arreter();
    void this.auth.logout();
  }

  private noter(forcer: boolean): void {
    // Back after the deadline (laptop waking up, frozen tab): the first click must not save the session
    if (!forcer && this.depasse()) {
      this.expirer();
      return;
    }
    const maintenant = Date.now();
    this.derniere = maintenant;
    if (!forcer && maintenant - this.ecrite < ECRITURE_MIN) return;
    this.ecrite = maintenant;
    try { localStorage.setItem(CLE_ACTIVITE, String(maintenant)); } catch { /* storage unavailable: this tab only */ }
  }

  /** Last activity in any tab. A date in the future (clock set back) is ignored, or nobody would ever be logged out. */
  private derniereActivite(): number {
    let autreOnglet = 0;
    try { autreOnglet = Number(localStorage.getItem(CLE_ACTIVITE)) || 0; } catch { /* storage unavailable */ }
    return Math.max(this.derniere, autreOnglet > Date.now() ? 0 : autreOnglet);
  }

  private depasse(): boolean {
    return this.auth.connecte() && !this.enCours && Date.now() - this.derniereActivite() >= DELAI_INACTIVITE;
  }

  private expirer(): void {
    if (this.enCours) return;
    this.enCours = true;
    this.arreter();
    // Another tab may have logged out a moment ago (its storage event is on its way): the refresh token is already revoked
    let encoreLa = true;
    try { encoreLa = localStorage.getItem('rh.session') !== null; } catch { /* storage unavailable */ }
    if (encoreLa) {
      void this.auth.logout('inactivite');
    } else {
      // Already revoked by the other tab (or the session only lived in this tab's memory): just close it here
      this.auth.oublier();
      void this.router.navigate(['/login'], { queryParams: { idle: 1 } });
    }
  }

  private verifier(): void {
    if (!this.auth.connecte() || this.enCours) {
      this.restant.set(null);
      return;
    }
    const reste = this.derniereActivite() + DELAI_INACTIVITE - Date.now();
    if (reste <= 0) {
      this.expirer();
    } else {
      this.restant.set(reste <= PREAVIS ? Math.ceil(reste / 1000) : null);
    }
  }
}
