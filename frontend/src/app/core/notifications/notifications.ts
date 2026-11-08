import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { RhApi } from '../api/rh-api';
import { Notification } from '../api/models';
import { AuthService } from '../auth/auth.service';

const DELAI_INITIAL = 5_000;
const DELAI_MAX = 60_000;

/**
 * Real-time notifications while a session exists.
 * The stream (SSE) needs a one-use ticket, traded for the JWT: EventSource cannot send headers.
 * When the stream fails or closes (it ends with the access token's lifetime), a new ticket is
 * asked after 5 s, then 10 s, 20 s… up to 60 s, so a long outage never floods the API.
 */
@Injectable({ providedIn: 'root' })
export class Notifications {
  private readonly api = inject(RhApi);
  private readonly auth = inject(AuthService);

  /** Unread count, kept right by the stream (including reads made in other tabs). */
  readonly nonLues = signal(0);
  /** The last notification pushed by the stream, for the toast; null once dismissed. */
  readonly derniere = signal<Notification | null>(null);

  private flux: EventSource | null = null;
  private demandeTicket: Subscription | undefined;
  private minuteur: ReturnType<typeof setTimeout> | undefined;
  private delai = DELAI_INITIAL;
  private actif = false;

  constructor() {
    effect(() => {
      const connecte = this.auth.connecte();
      untracked(() => (connecte ? this.demarrer() : this.arreter()));
    });
  }

  /**
   * After a read made here: the count is asked from the server, never computed locally
   * (the stream may already have updated it, or another tab may have read the same one).
   */
  resynchroniser(): void {
    this.api.nombreNonLues().subscribe({ next: r => this.nonLues.set(r.nombre), error: () => {} });
  }

  fermerToast(): void {
    this.derniere.set(null);
  }

  private demarrer(): void {
    this.arreter();
    this.actif = true;
    if (typeof EventSource === 'undefined') {
      this.resynchroniser();
      return;
    }
    this.ouvrir();
  }

  private ouvrir(): void {
    this.demandeTicket?.unsubscribe();
    this.demandeTicket = this.api.ticketFlux().subscribe({
      next: ({ ticket }) => {
        if (!this.actif) return;
        const flux = new EventSource(this.api.urlFlux(ticket));
        this.flux = flux;
        flux.addEventListener('non-lues', e => {
          this.delai = DELAI_INITIAL;
          this.nonLues.set(lire<{ nombre: number }>(e)?.nombre ?? this.nonLues());
        });
        flux.addEventListener('notification', e => {
          this.delai = DELAI_INITIAL;
          const p = lire<{ notification: Notification; nombreNonLues: number }>(e);
          if (!p) return;
          this.nonLues.set(p.nombreNonLues);
          this.derniere.set(p.notification);
        });
        // A used ticket cannot reopen the stream: close it and come back with a new one
        flux.onerror = () => {
          flux.close();
          if (this.flux === flux) this.flux = null;
          this.planifier();
        };
      },
      error: () => this.planifier(),
    });
  }

  private planifier(): void {
    if (!this.actif) return;
    clearTimeout(this.minuteur);
    this.minuteur = setTimeout(() => this.ouvrir(), this.delai);
    this.delai = Math.min(this.delai * 2, DELAI_MAX);
  }

  private arreter(): void {
    this.actif = false;
    clearTimeout(this.minuteur);
    // A ticket still on its way belongs to this session: it must not open a stream for the next one
    this.demandeTicket?.unsubscribe();
    this.demandeTicket = undefined;
    this.flux?.close();
    this.flux = null;
    this.delai = DELAI_INITIAL;
    this.nonLues.set(0);
    this.derniere.set(null);
  }
}

function lire<T>(e: Event): T | null {
  try {
    return JSON.parse((e as MessageEvent<string>).data) as T;
  } catch {
    return null;
  }
}
