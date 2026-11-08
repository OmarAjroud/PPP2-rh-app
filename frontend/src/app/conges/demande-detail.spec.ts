import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { DemandeConge } from '../core/api/models';
import { DemandeDetail } from './demande-detail';

const api = `${environment.apiUrl}/api`;
const demande = (d: Partial<DemandeConge> = {}): DemandeConge => ({
  id: 7, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2099-12-14', dateFin: '2099-12-18', nombreJours: 4, commentaire: 'Vacances',
  statut: 'EN_ATTENTE', dateCreation: '2099-11-08T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...d,
});

describe('Détail d’une demande de congé', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, employeId: number, d: DemandeConge = demande()): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'leave-requests/:id', component: DemandeDetail }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/leave-requests/7');
    http.expectOne(`${api}/conges/demandes/7`).flush(d);
    http.match(() => true).forEach(r => r.flush({}));
    harness.detectChanges();
  }

  const el = () => harness.routeNativeElement!;
  const bouton = (texte: string) => [...el().querySelectorAll('button')].find(b => b.textContent?.trim() === texte);
  const saisir = (texte: string) => {
    const t = el().querySelector<HTMLTextAreaElement>('textarea#motif')!;
    t.value = texte;
    t.dispatchEvent(new Event('input'));
    harness.detectChanges();
  };

  afterEach(() => localStorage.clear());

  it('shows the request with its working days and comment', async () => {
    await ouvrir('EMPLOYE', 4);
    expect(el().textContent).toContain('Congé annuel');
    expect(el().textContent).toContain('4 jours');
    expect(el().textContent).toContain('Vacances');
    expect(el().textContent).toContain('En attente');
  });

  it('refuses nothing without a reason', async () => {
    await ouvrir('MANAGER', 3);
    bouton('Refuser')!.click();
    harness.detectChanges();
    http.expectNone(r => r.url.endsWith('/refus'));
    expect(el().textContent).toContain('Indiquez le motif du refus');
  });

  it('refuses with the reason given', async () => {
    await ouvrir('MANAGER', 3);
    saisir('Période chargée');
    bouton('Refuser')!.click();
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/demandes/7/refus`);
    expect(req.request.body).toEqual({ motif: 'Période chargée' });
    req.flush(demande({ statut: 'REFUSEE', motifDecision: 'Période chargée', decideParNomComplet: 'Karim Mansour' }));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(el().textContent).toContain('Refusée');
    expect(el().textContent).toContain('Karim Mansour');
  });

  it('approves with the optional comment', async () => {
    await ouvrir('MANAGER', 3);
    saisir('Bonnes vacances');
    bouton('Approuver')!.click();
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/demandes/7/validation`);
    expect(req.request.body).toEqual({ motif: 'Bonnes vacances' });
  });

  it('lets the owner cancel but not decide', async () => {
    await ouvrir('EMPLOYE', 4);
    expect(bouton('Annuler la demande')).toBeDefined();
    expect(bouton('Approuver')).toBeUndefined();
  });

  it('does not let a manager cancel someone else’s request', async () => {
    await ouvrir('MANAGER', 3);
    expect(bouton('Annuler la demande')).toBeUndefined();
  });

  it('lets HR interrupt an approved leave that is not over, with a reason', async () => {
    await ouvrir('RH', 2, demande({ statut: 'APPROUVEE', dateDebut: '2020-01-06', dateFin: '2099-12-31' }));
    saisir('Retour anticipé');
    bouton('Interrompre le congé')!.click();
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/demandes/7/interruption`);
    expect(req.request.body).toEqual({ motif: 'Retour anticipé' });
  });
  it('does not offer HR a decision on a fresh request that is still the manager’s to decide', async () => {
    await ouvrir('RH', 2, demande({ dateEscalade: null }));
    expect(bouton('Approuver')).toBeUndefined();
    expect(bouton('Refuser')).toBeUndefined();
    expect(el().textContent).toContain('À traiter par le manager');
  });

  it('offers HR the decision once the request has been passed to HR', async () => {
    await ouvrir('RH', 2, demande({ dateEscalade: '2099-11-11T08:00:00' }));
    expect(bouton('Approuver')).toBeDefined();
  });

  it('asks the owner to confirm before cancelling', async () => {
    await ouvrir('EMPLOYE', 4);
    bouton('Annuler la demande')!.click();
    harness.detectChanges();
    http.expectNone(r => r.url.endsWith('/annulation'));
    bouton('Confirmer l’annulation')!.click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/demandes/7/annulation`).flush(demande({ statut: 'ANNULEE' }));
  });
});
