import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { TableauDeBord as Donnees } from '../core/api/models';
import { TableauDeBord } from './tableau-de-bord';

registerLocaleData(localeFr); // the app formats amounts in French (app.config)
const api = `${environment.apiUrl}/api`;
const donnees = (o: Partial<Donnees> = {}): Donnees => ({
  annee: 2026, departementId: null,
  indicateurs: { nombreEmployes: 48, demandesEnAttente: 7, embauchesDuMois: 2, absentsAujourdhui: 3 },
  effectifParDepartement: [{ departementId: 1, departementNom: 'Informatique', nombreEmployes: 20 }, { departementId: 2, departementNom: 'Finance', nombreEmployes: 8 }],
  embauchesParMois: Array.from({ length: 12 }, (_, i) => ({ mois: i + 1, nombre: i === 2 ? 3 : 0 })),
  repartitionConges: [{ typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel', nombreDemandes: 12, nombreJours: 54 }],
  masseSalariale: [{ departementId: 1, departementNom: 'Informatique', nombreEmployes: 20, total: 62000, moyenne: 3100 }],
  ...o,
});

describe('Tableau de bord', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, url = '/dashboard'): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 3, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'dashboard', component: TableauDeBord }]), provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'fr' }],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
  }

  const el = () => harness.routeNativeElement!;
  const texte = () => el().textContent!.replace(/\s+/g, ' ');
  const charger = () => http.expectOne(r => r.url === `${api}/tableau-de-bord`);

  afterEach(() => localStorage.clear());

  it('shows HR the key figures, the charts and the payroll, with a department filter', async () => {
    await ouvrir('RH');
    http.match(`${api}/departements`).forEach(r => r.flush([{ id: 1, nom: 'Informatique' }, { id: 2, nom: 'Finance' }]));
    const req = charger();
    expect(req.request.params.has('departementId')).toBe(false);
    req.flush(donnees());
    harness.detectChanges();
    expect(texte()).toContain('48');
    expect(texte()).toContain('Demandes en attente');
    expect(texte()).toContain('Informatique');
    expect(texte()).toContain('Masse salariale');
    expect(texte()).toMatch(/62\s000,00\s€/);
    expect(el().querySelector('[aria-label="Filtrer par département"]')).not.toBeNull();
  });

  it('sends the department and year chosen in the URL', async () => {
    await ouvrir('ADMIN', '/dashboard?annee=2025&dept=2');
    http.match(`${api}/departements`).forEach(r => r.flush([]));
    const req = charger();
    expect(req.request.params.get('annee')).toBe('2025');
    expect(req.request.params.get('departementId')).toBe('2');
  });

  it('shows a manager their department only: no department filter, no payroll', async () => {
    await ouvrir('MANAGER');
    http.expectNone(`${api}/departements`);
    charger().flush(donnees({ departementId: 1, masseSalariale: null }));
    harness.detectChanges();
    expect(el().querySelector('[aria-label="Filtrer par département"]')).toBeNull();
    expect(texte()).not.toContain('Masse salariale');
  });

  it('shows a year from the address even when it is not among the three offered, and dates the tiles', async () => {
    await ouvrir('RH', '/dashboard?annee=2019');
    http.match(`${api}/departements`).forEach(r => r.flush([]));
    charger().flush(donnees({ annee: 2019 }));
    harness.detectChanges();
    const cmp = harness.routeDebugElement!.componentInstance as { annees(): { value: number }[] };
    expect(cmp.annees().map(a => a.value)).toContain(2019);
    expect(texte()).toContain('à ce jour');
  });
});
