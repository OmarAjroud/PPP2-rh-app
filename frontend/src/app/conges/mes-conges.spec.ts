import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { DemandeConge } from '../core/api/models';
import { MesConges } from './mes-conges';

const api = `${environment.apiUrl}/api/conges`;
const demande = (d: Partial<DemandeConge>): DemandeConge => ({
  id: 7, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2099-12-14', dateFin: '2099-12-18', nombreJours: 5, commentaire: null,
  statut: 'EN_ATTENTE', dateCreation: '2026-11-08T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...d,
});
const page = (contenu: DemandeConge[]) => ({ contenu, numeroPage: 0, taillePage: 10, totalElements: contenu.length, totalPages: 1, dernierePage: true });

describe('Mes congés', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'leave-requests', component: MesConges }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => localStorage.clear());

  function flushSoldes(): void {
    http.expectOne(`${api}/soldes/moi`).flush([{ typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel', annee: 2026, joursAlloues: 22, joursPris: 2, joursEnAttente: 5, joursDisponibles: 15 }]);
  }

  it('lists my requests with their status and a link to each one, filtered by the URL', async () => {
    await harness.navigateByUrl('/leave-requests?statut=EN_ATTENTE');
    flushSoldes();
    const req = http.expectOne(r => r.url === `${api}/demandes/moi`);
    expect(req.request.params.get('statut')).toBe('EN_ATTENTE');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([demande({})]));
    harness.detectChanges();
    const el = harness.routeNativeElement!;
    expect(el.textContent).toContain('Congé annuel');
    expect(el.textContent).toContain('En attente');
    expect(el.querySelector('a[href="/leave-requests/7"]')).not.toBeNull();
    expect(el.textContent).toContain('15'); // available balance
  });

  it('cancels a pending request after confirmation, then reloads the list', async () => {
    await harness.navigateByUrl('/leave-requests');
    flushSoldes();
    http.expectOne(r => r.url === `${api}/demandes/moi`).flush(page([demande({})]));
    harness.detectChanges();
    const el = harness.routeNativeElement!;
    const bouton = (texte: string) => [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === texte)!;
    bouton('Annuler').click();
    harness.detectChanges();
    bouton('Confirmer').click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/demandes/7/annulation`).flush(demande({ statut: 'ANNULEE' }));
    await harness.fixture.whenStable(); // the reload starts once the cancellation has answered
    flushSoldes();
    http.expectOne(r => r.url === `${api}/demandes/moi`).flush(page([demande({ statut: 'ANNULEE' })]));
  });

  it('offers no cancellation for a refused request', async () => {
    await harness.navigateByUrl('/leave-requests');
    flushSoldes();
    http.expectOne(r => r.url === `${api}/demandes/moi`).flush(page([demande({ statut: 'REFUSEE' })]));
    harness.detectChanges();
    expect([...harness.routeNativeElement!.querySelectorAll('button')].some(b => b.textContent?.trim() === 'Annuler')).toBe(false);
  });

  it('moves focus to « Confirmer » when « Annuler » is pressed, so keyboard users keep their place', async () => {
    await harness.navigateByUrl('/leave-requests');
    flushSoldes();
    http.expectOne(r => r.url === `${api}/demandes/moi`).flush(page([demande({})]));
    harness.detectChanges();
    const el = harness.routeNativeElement!;
    [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Annuler')!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('Confirmer');
  });
  it('shows the history of my balance', async () => {
    await harness.navigateByUrl('/leave-requests');
    flushSoldes();
    http.expectOne(r => r.url === `${api}/demandes/moi`).flush(page([]));
    http.expectOne(r => r.url === `${api}/soldes/moi/mouvements`).flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Historique du solde');
  });
});
