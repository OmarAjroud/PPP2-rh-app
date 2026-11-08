import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { Accueil } from './accueil';

const api = environment.apiUrl;
const page = (contenu: unknown[], totalElements = contenu.length) => ({ contenu, numeroPage: 0, taillePage: 3, totalElements, totalPages: 1, dernierePage: true });

describe('Accueil', () => {
  let http: HttpTestingController;

  function connecter(role: Role): void {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 4, departementId: 1 },
    }));
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  function render(): HTMLElement {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /** Answers the leave calls (balances, my last requests, the profile) so each test looks at one thing. */
  function repondreConges(demandes: unknown[] = []): void {
    http.match(`${api}/api/conges/soldes/moi`).forEach(r => r.flush([{ typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel', annee: 2026, joursAlloues: 22, joursPris: 0, joursEnAttente: 0, joursDisponibles: 22 }]));
    http.match(r => r.url === `${api}/api/conges/demandes/moi`).forEach(r => r.flush(page(demandes)));
    http.match(`${api}/api/employes/me`).forEach(r => r.flush({ id: 4, prenom: 'Lina', nom: 'Gharbi' }));
  }

  it('shows UP when the API health check answers UP', () => {
    const el = render();
    http.expectOne(`${api}/actuator/health`).flush({ status: 'UP' });
    repondreConges();
    TestBed.tick();
    expect(el.querySelector('.status')?.textContent).toContain('Serveur disponible');
  });

  it('explains how to start the API when it cannot be reached', () => {
    const el = render();
    http.expectOne(`${api}/actuator/health`).error(new ProgressEvent('error'));
    http.match(() => true).forEach(r => r.error(new ProgressEvent('error')));
    TestBed.tick();
    expect(el.querySelector('.status')?.textContent).toContain('Serveur injoignable');
    expect(el.textContent).toContain('docker compose up -d');
  });

  it('shows an employee their balances and last requests, without asking what to decide', () => {
    connecter('EMPLOYE');
    const el = render();
    http.expectOne(`${api}/actuator/health`).flush({ status: 'UP' });
    repondreConges([{ id: 7, dateDebut: '2099-12-14', dateFin: '2099-12-18', nombreJours: 4, typeCongeLibelle: 'Congé annuel', statut: 'EN_ATTENTE', dateEscalade: null }]);
    TestBed.tick();
    http.expectNone(r => r.url.endsWith('/a-decider'));
    expect(el.textContent).toContain('Soldes');
    expect(el.textContent).toContain('Congé annuel');
    expect(el.querySelector('a[href="/leave-requests/7"]')).not.toBeNull();
  });

  it('tells a manager how many requests wait for their decision', () => {
    connecter('MANAGER');
    const el = render();
    http.expectOne(`${api}/actuator/health`).flush({ status: 'UP' });
    repondreConges();
    http.expectOne(r => r.url === `${api}/api/conges/demandes/a-decider`).flush(page([{}, {}], 2));
    TestBed.tick();
    const lien = el.querySelector<HTMLAnchorElement>('a[href="/approvals"]');
    expect(lien?.textContent?.replace(/\s+/g, ' ')).toContain('2 demandes à décider');
  });
});
