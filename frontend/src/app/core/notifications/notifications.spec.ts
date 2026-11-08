import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { Notifications } from './notifications';

const api = `${environment.apiUrl}/api/notifications`;

/** Minimal EventSource stand-in: records its URL, lets the test push named events or fail it. */
class FauxFlux extends EventTarget {
  static instances: FauxFlux[] = [];
  onerror: ((e: Event) => void) | null = null;
  ferme = false;
  constructor(readonly url: string) { super(); FauxFlux.instances.push(this); }
  close(): void { this.ferme = true; }
  emettre(nom: string, data: unknown): void { this.dispatchEvent(new MessageEvent(nom, { data: JSON.stringify(data) })); }
  echouer(): void { this.onerror?.(new Event('error')); }
}

describe('Notifications (flux temps réel)', () => {
  let http: HttpTestingController;
  let service: Notifications;
  const origine = (globalThis as { EventSource?: unknown }).EventSource;

  function connecter(): void {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
  }

  function demarrer(): void {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(Notifications);
    TestBed.tick();
  }

  const ticket = (valeur: string) => http.expectOne(r => r.method === 'POST' && r.url === `${api}/flux/ticket`).flush({ ticket: valeur, expireDans: 30 });

  beforeEach(() => {
    localStorage.clear();
    FauxFlux.instances = [];
    (globalThis as { EventSource?: unknown }).EventSource = FauxFlux;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    (globalThis as { EventSource?: unknown }).EventSource = origine;
    localStorage.clear();
  });

  it('trades the session for a ticket, then opens the stream with it', () => {
    connecter();
    demarrer();
    ticket('t-1');
    expect(FauxFlux.instances).toHaveLength(1);
    expect(FauxFlux.instances[0].url).toBe(`${api}/flux?ticket=t-1`);
  });

  it('follows the unread count and keeps the last notification received', () => {
    connecter();
    demarrer();
    ticket('t-1');
    const flux = FauxFlux.instances[0];
    flux.emettre('non-lues', { nombre: 2 });
    expect(service.nonLues()).toBe(2);
    flux.emettre('notification', { notification: { id: 9, type: 'CONGE_APPROUVE', titre: 'Demande approuvée', message: 'Votre congé du 14 déc. est approuvé.', lien: '/leave-requests/7', lue: false, dateCreation: '2026-11-08T10:00:00', dateLecture: null }, nombreNonLues: 3 });
    expect(service.nonLues()).toBe(3);
    expect(service.derniere()?.titre).toBe('Demande approuvée');
  });

  it('asks for a new ticket after the stream fails, waiting longer each time', () => {
    connecter();
    demarrer();
    ticket('t-1');
    FauxFlux.instances[0].echouer();
    expect(FauxFlux.instances[0].ferme).toBe(true);
    http.expectNone(`${api}/flux/ticket`);
    vi.advanceTimersByTime(5000);
    ticket('t-2');
    expect(FauxFlux.instances[1].url).toBe(`${api}/flux?ticket=t-2`);
    FauxFlux.instances[1].echouer();
    vi.advanceTimersByTime(5000);
    http.expectNone(`${api}/flux/ticket`); // the second retry waits 10 s
    vi.advanceTimersByTime(5000);
    ticket('t-3');
  });

  it('closes the stream on logout and does not reconnect', async () => {
    connecter();
    demarrer();
    ticket('t-1');
    const auth = TestBed.inject(AuthService);
    void auth.logout();
    http.match(() => true).forEach(r => r.flush({}));
    TestBed.tick();
    expect(FauxFlux.instances[0].ferme).toBe(true);
    expect(service.nonLues()).toBe(0);
    vi.advanceTimersByTime(120_000);
    http.expectNone(`${api}/flux/ticket`);
  });

  it('reads the count once when the browser has no EventSource', () => {
    (globalThis as { EventSource?: unknown }).EventSource = undefined;
    connecter();
    demarrer();
    http.expectOne(`${api}/non-lues/nombre`).flush({ nombre: 4 });
    expect(service.nonLues()).toBe(4);
  });
});
