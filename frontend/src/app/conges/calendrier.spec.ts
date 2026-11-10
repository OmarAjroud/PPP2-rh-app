import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { DemandeConge } from '../core/api/models';
import { Calendrier } from './calendrier';

const api = `${environment.apiUrl}/api`;
const d = (o: Partial<DemandeConge>): DemandeConge => ({
  id: 1, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2026-12-14', dateFin: '2026-12-18', nombreJours: 5, commentaire: null,
  statut: 'APPROUVEE', dateCreation: '2026-11-01T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...o,
});
const page = (contenu: DemandeConge[], numeroPage = 0, dernierePage = true) => ({ contenu, numeroPage, taillePage: 100, totalElements: contenu.length, totalPages: 1, dernierePage });

describe('Calendrier des congés', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, url = '/calendar?mois=2026-12'): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 3, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'calendar', component: Calendrier }, { path: 'leave-requests/:id', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http.match(r => r.url === `${api}/jours-feries`).forEach(r => r.flush([{ id: 1, date: '2026-12-25', libelle: 'Noël' }]));
    http.match(r => r.url === `${api}/departements`).forEach(r => r.flush([{ id: 1, nom: 'Informatique' }]));
  }
  afterEach(() => { http.verify(); localStorage.clear(); });
  const el = () => harness.routeNativeElement!;

  it('reads every page of the month for HR, then draws one row per person', async () => {
    await ouvrir('RH');
    const p0 = http.expectOne(r => r.url === `${api}/conges/demandes` && r.params.get('page') === '0');
    expect(p0.request.params.get('du')).toBe('2026-12-01');
    expect(p0.request.params.get('au')).toBe('2026-12-31');
    expect(p0.request.params.get('size')).toBe('100');
    p0.flush(page([d({})], 0, false));
    http.expectOne(r => r.url === `${api}/conges/demandes` && r.params.get('page') === '1')
      .flush(page([d({ id: 2, employeId: 5, employeNomComplet: 'Youssef Ben Ali', dateDebut: '2026-12-01', dateFin: '2026-12-02', statut: 'EN_ATTENTE' })], 1, true));
    harness.detectChanges();
    expect([...el().querySelectorAll('.personne')].map(p => p.textContent!.trim())).toEqual(['Lina Gharbi', 'Youssef Ben Ali']);
    const barre = el().querySelector<HTMLAnchorElement>('a.barre[href="/leave-requests/1"]')!;
    expect(barre.getAttribute('aria-label')).toBe('Lina Gharbi, Congé annuel, du 14 au 18 décembre, approuvé');
    expect(el().querySelector('a.barre.en-attente')).not.toBeNull();
  });

  it('reads the manager\'s team for the year', async () => {
    await ouvrir('MANAGER');
    const req = http.expectOne(r => r.url === `${api}/conges/demandes/equipe`);
    expect(req.request.params.get('annee')).toBe('2026');
    req.flush(page([d({})]));
    harness.detectChanges();
    expect(el().querySelector('[aria-label="Filtrer par département"]')).toBeNull();
    expect([...el().querySelectorAll('.personne')].map(p => p.textContent!.trim())).toEqual(['Lina Gharbi']);
  });

  it('moves to the next month', async () => {
    await ouvrir('RH');
    http.expectOne(r => r.url === `${api}/conges/demandes`).flush(page([]));
    harness.detectChanges();
    el().querySelector<HTMLButtonElement>('button[aria-label="Mois suivant"]')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/calendar?mois=2027-01');
    http.match(r => r.url === `${api}/jours-feries`).forEach(r => r.flush([]));
    const req = http.expectOne(r => r.url === `${api}/conges/demandes`);
    expect(req.request.params.get('du')).toBe('2027-01-01');
    req.flush(page([]));
  });

  it('offers the same information as a list', async () => {
    await ouvrir('RH');
    http.expectOne(r => r.url === `${api}/conges/demandes`).flush(page([d({})]));
    harness.detectChanges();
    el().querySelector<HTMLButtonElement>('button.vue-liste')!.click();
    harness.detectChanges();
    expect(el().querySelector('.liste-absences')!.textContent!.replace(/\s+/g, ' ')).toContain('Lina Gharbi');
    expect(el().querySelector('.liste-absences')!.textContent!.replace(/\s+/g, ' ')).toContain('14 – 18 déc.');
  });

  it('says so when nobody is off that month', async () => {
    await ouvrir('RH');
    http.expectOne(r => r.url === `${api}/conges/demandes`).flush(page([]));
    harness.detectChanges();
    expect(el().textContent).toContain('Personne n’est absent');
  });

  it('sends the department chosen in the address', async () => {
    await ouvrir('RH', '/calendar?mois=2027-01&departement=2');
    const req = http.expectOne(r => r.url === `${api}/conges/demandes`);
    expect(req.request.params.get('departementId')).toBe('2');
    req.flush(page([]));
  });

  it('asks a manager for one year only, even in January (a request never spans two years)', async () => {
    await ouvrir('MANAGER', '/calendar?mois=2027-01');
    const req = http.expectOne(r => r.url === `${api}/conges/demandes/equipe`);
    expect(req.request.params.get('annee')).toBe('2027');
    req.flush(page([]));
  });
});
