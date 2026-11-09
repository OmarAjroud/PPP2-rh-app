import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { DemandeConge } from '../core/api/models';
import { ADecider } from './a-decider';

const api = `${environment.apiUrl}/api/conges/demandes`;
const demande = (d: Partial<DemandeConge> = {}): DemandeConge => ({
  id: 7, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2099-12-14', dateFin: '2099-12-18', nombreJours: 4, commentaire: null,
  statut: 'EN_ATTENTE', dateCreation: '2099-11-08T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...d,
});
const page = (contenu: DemandeConge[]) => ({ contenu, numeroPage: 0, taillePage: 10, totalElements: contenu.length, totalPages: 1, dernierePage: true });

describe('À décider', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, url = '/approvals'): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 3, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'approvals', component: ADecider }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
  }

  const el = () => harness.routeNativeElement!;
  const onglets = () => [...el().querySelectorAll('.onglets a')].map(t => t.textContent!.trim());

  afterEach(() => localStorage.clear());

  it('shows a manager what they can decide, with no « Toutes » tab', async () => {
    await ouvrir('MANAGER');
    http.expectOne(r => r.url === `${api}/a-decider`).flush(page([demande()]));
    harness.detectChanges();
    expect(onglets()).toEqual(['À décider', 'Équipe']);
    expect(el().textContent).toContain('Lina Gharbi');
    expect(el().querySelector('a[href="/leave-requests/7"]')).not.toBeNull();
  });

  it('gives HR a « Toutes » tab listing every request', async () => {
    await ouvrir('RH', '/approvals?vue=toutes');
    expect(onglets()).toEqual(['À décider', 'Équipe', 'Toutes']);
    http.expectOne(r => r.url === api).flush(page([]));
  });

  it('approves from the list and reloads it', async () => {
    await ouvrir('MANAGER');
    http.expectOne(r => r.url === `${api}/a-decider`).flush(page([demande()]));
    harness.detectChanges();
    [...el().querySelectorAll('button')].find(b => b.textContent?.trim() === 'Approuver')!.click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/7/validation`).flush(demande({ statut: 'APPROUVEE' }));
    await harness.fixture.whenStable();
    http.expectOne(r => r.url === `${api}/a-decider`).flush(page([]));
  });

  it('marks the current view with aria-current and says nothing is waiting in words that fit HR', async () => {
    await ouvrir('RH');
    http.expectOne(r => r.url === `${api}/a-decider`).flush(page([]));
    harness.detectChanges();
    expect(el().querySelector('.onglets a[aria-current="page"]')!.textContent!.trim()).toBe('À décider');
    expect(el().querySelector('[role="tab"]')).toBeNull();
    expect(el().textContent).not.toContain('de votre équipe');
  });
});
