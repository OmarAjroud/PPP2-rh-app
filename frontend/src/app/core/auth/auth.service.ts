import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, Session } from './auth.models';

const CLE = 'rh.session';
const VERROU = 'rh-refresh';
/** Why the session ended, for the tabs that only see it disappear (e.g. 'inactivite'). */
const RAISON = 'rh.raison';

/**
 * Session handling.
 *
 * Tokens live in localStorage so every tab shares one session; the "storage" event keeps tabs in sync.
 * The backend's refresh tokens are single-use and a reused one closes every session of the account,
 * so a refresh must never run twice for the same token: one refresh at a time per tab (shared promise)
 * and across tabs (Web Locks), and inside the lock we first check whether another tab already refreshed.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = `${environment.apiUrl}/api/auth`;

  private readonly etat = signal<Session | null>(lire());
  readonly session = this.etat.asReadonly();
  readonly profil = computed(() => this.etat()?.profil ?? null);
  readonly connecte = computed(() => this.etat() !== null);

  private refreshEnCours: Promise<string> | null = null;
  /** Changes at every logout: a renewal that started before must not bring the session back. */
  private generation = 0;

  constructor() {
    // Another tab logged in, refreshed or logged out
    window.addEventListener('storage', e => {
      if (e.key !== CLE) return;
      const avant = this.etat();
      this.etat.set(lire());
      if (avant && !this.etat()) {
        let raison: string | null = null;
        try { raison = localStorage.getItem(RAISON); } catch { /* storage unavailable */ }
        if (raison === 'inactivite') void this.router.navigate(['/login'], { queryParams: { idle: 1 } });
        else void this.router.navigateByUrl('/login');
      }
    });
  }

  get accessToken(): string | null {
    return this.etat()?.accessToken ?? null;
  }

  async login(nomUtilisateur: string, motDePasse: string): Promise<void> {
    const r = await firstValueFrom(this.http.post<AuthResponse>(`${this.api}/login`, { nomUtilisateur, motDePasse }));
    this.enregistrer(r);
  }

  /**
   * Revokes the refresh token server-side (best effort) and forgets the session locally.
   * 'inactivite': the login page then explains why (logged out after 15 minutes without activity).
   */
  async logout(raison?: 'inactivite'): Promise<void> {
    const refreshToken = this.etat()?.refreshToken;
    // Left before the session goes, so the other tabs can explain it too
    if (raison) { try { localStorage.setItem(RAISON, raison); } catch { /* storage unavailable */ } }
    this.oublier();
    if (refreshToken) {
      try { await firstValueFrom(this.http.post(`${this.api}/logout`, { refreshToken })); } catch { /* already gone */ }
    }
    if (raison) await this.router.navigate(['/login'], { queryParams: { idle: 1 } });
    else await this.router.navigateByUrl('/login');
  }

  /** Closes every session of this account, on every device. */
  async logoutPartout(): Promise<void> {
    await firstValueFrom(this.http.post(`${this.api}/deconnexion-partout`, {}));
    this.oublier();
    await this.router.navigateByUrl('/login');
  }

  /** The other sessions are closed by the server; this one receives new tokens. */
  async changerMotDePasse(motDePasseActuel: string, nouveauMotDePasse: string): Promise<void> {
    const r = await firstValueFrom(this.http.put<AuthResponse>(`${this.api}/mot-de-passe`, { motDePasseActuel, nouveauMotDePasse }));
    this.enregistrer(r);
  }

  /**
   * Returns a fresh access token, or throws when the session cannot be renewed
   * (the session is then forgotten). Concurrent callers share one refresh.
   */
  refresh(): Promise<string> {
    this.refreshEnCours ??= this.refreshExclusif().finally(() => (this.refreshEnCours = null));
    return this.refreshEnCours;
  }

  /** Forgets the session in this browser (all tabs). */
  oublier(): void {
    this.generation++;
    try { localStorage.removeItem(CLE); } catch { /* storage unavailable */ }
    this.etat.set(null);
  }

  private async refreshExclusif(): Promise<string> {
    const utilise = this.etat()?.refreshToken;
    if (!utilise) throw new Error('Aucune session');
    const tache = async (): Promise<string> => {
      // Another tab may have refreshed while we waited for the lock
      const actuelle = lire();
      if (actuelle && actuelle.refreshToken !== utilise) {
        this.etat.set(actuelle);
        return actuelle.accessToken;
      }
      const generation = this.generation;
      let r: AuthResponse;
      try {
        r = await firstValueFrom(this.http.post<AuthResponse>(`${this.api}/refresh`, { refreshToken: utilise }));
      } catch (e) {
        this.oublier();
        throw e;
      }
      // Logged out while the renewal was on its way (here or in another tab): keep it that way,
      // and revoke the new refresh token instead of leaving it valid on the server
      if (generation !== this.generation || !this.etat()) {
        this.http.post(`${this.api}/logout`, { refreshToken: r.refreshToken }).subscribe({ error: () => undefined });
        throw new Error('Session fermée pendant le renouvellement');
      }
      this.enregistrer(r);
      return r.accessToken;
    };
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    return locks ? locks.request(VERROU, tache) : tache();
  }

  private enregistrer(r: AuthResponse): void {
    const s: Session = {
      accessToken: r.accessToken,
      refreshToken: r.refreshToken,
      expiresAt: Date.now() + r.expiresIn * 1000,
      profil: r.profil,
    };
    try {
      localStorage.setItem(CLE, JSON.stringify(s));
      localStorage.removeItem(RAISON);
    } catch { /* storage unavailable: session lasts for this tab */ }
    this.etat.set(s);
  }
}

function lire(): Session | null {
  try {
    const brut = localStorage.getItem(CLE);
    return brut ? (JSON.parse(brut) as Session) : null;
  } catch {
    return null;
  }
}
