import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { DemandeForm } from './demande-form';

const api = `${environment.apiUrl}/api`;

describe('Nouvelle demande de congé', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let form: DemandeForm;

  async function ouvrir(managerNomComplet: string | null = 'Karim Mansour'): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'leave-requests/new', component: DemandeForm }, { path: 'leave-requests/:id', children: [] }]),
        provideHttpClient(), provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    form = await harness.navigateByUrl('/leave-requests/new', DemandeForm);
    http.expectOne(`${api}/types-conge`).flush([
      { id: 1, code: 'CP', libelle: 'Congé annuel', deduitDuSolde: true, joursParAn: 22, retroactifAutorise: false },
      { id: 2, code: 'MAL', libelle: 'Congé maladie', deduitDuSolde: false, joursParAn: 0, retroactifAutorise: true },
    ]);
    http.expectOne(`${api}/conges/soldes/moi`).flush([{ typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel', annee: 2099, joursAlloues: 22, joursPris: 0, joursEnAttente: 0, joursDisponibles: 22 }]);
    http.match(`${api}/employes/me`).forEach(r => r.flush({ id: 4, prenom: 'Lina', nom: 'Gharbi', managerNomComplet, managerId: managerNomComplet ? 3 : null }));
    harness.detectChanges();
  }

  const texte = () => harness.routeNativeElement!.textContent!.replace(/\s+/g, ' ');

  afterEach(() => localStorage.clear());

  it('asks the API for the working days with the local calendar dates, and shows the proof', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 11, 14), dateFin: new Date(2099, 11, 18), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const req = http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`);
    expect(req.request.params.get('dateDebut')).toBe('2099-12-14');
    expect(req.request.params.get('dateFin')).toBe('2099-12-18');
    req.flush({ dateDebut: '2099-12-14', dateFin: '2099-12-18', joursOuvrables: 4, joursCalendaires: 5 });
    harness.detectChanges();
    expect(texte()).toContain('4 jours ouvrables');
    expect(texte()).toMatch(/22\s*→\s*18/);
    expect(texte()).toContain('Karim Mansour');
  }, 15_000); // heavy screen: passes in ~2 s alone, can pass 5 s when every file runs in parallel

  it('does not ask for a count when the end is before the start, and says why', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 11, 18), dateFin: new Date(2099, 11, 14), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectNone(r => r.url === `${api}/conges/demandes/jours-ouvrables`);
    expect(texte()).toContain('La date de fin doit être après la date de début');
  });

  it('warns before sending when the balance is not enough', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 0, 5), dateFin: new Date(2099, 1, 13), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`).flush({ dateDebut: '2099-01-05', dateFin: '2099-02-13', joursOuvrables: 30, joursCalendaires: 40 });
    harness.detectChanges();
    expect(texte()).toContain('Solde insuffisant');
  });

  it('says HR decide when the employee has no manager', async () => {
    await ouvrir(null);
    expect(texte()).toContain('Les RH');
  });

  it('sends the request with local dates and opens it', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 11, 14), dateFin: new Date(2099, 11, 18), commentaire: ' Vacances ' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`).flush({ dateDebut: '2099-12-14', dateFin: '2099-12-18', joursOuvrables: 4, joursCalendaires: 5 });
    harness.detectChanges();
    (harness.routeNativeElement!.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/demandes`);
    expect(req.request.body).toEqual({ typeCongeId: 1, dateDebut: '2099-12-14', dateFin: '2099-12-18', commentaire: 'Vacances' });
    req.flush({ id: 42 });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/leave-requests/42');
  });
  it('compares against the balance of the year the leave starts in', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2100, 0, 4), dateFin: new Date(2100, 0, 8), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const soldes = http.expectOne(r => r.url === `${api}/conges/soldes/moi` && r.params.get('annee') === '2100');
    soldes.flush([{ typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel', annee: 2100, joursAlloues: 25, joursPris: 0, joursEnAttente: 0, joursDisponibles: 25 }]);
    http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`).flush({ dateDebut: '2100-01-04', dateFin: '2100-01-08', joursOuvrables: 5, joursCalendaires: 5 });
    harness.detectChanges();
    expect(texte()).toMatch(/25s*→s*20/);
    expect(texte()).toContain('2100');
  });

  it('blocks a period without any working day, and says why', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 11, 19), dateFin: new Date(2099, 11, 20), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.match(r => r.url === `${api}/conges/soldes/moi`).forEach(r => r.flush([]));
    http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`).flush({ dateDebut: '2099-12-19', dateFin: '2099-12-20', joursOuvrables: 0, joursCalendaires: 2 });
    harness.detectChanges();
    expect(texte()).toContain('Aucun jour ouvrable');
    (harness.routeNativeElement!.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectNone(r => r.method === 'POST');
  });

  it('shows the server message under the field it concerns', async () => {
    await ouvrir();
    form.form.setValue({ typeCongeId: 1, dateDebut: new Date(2099, 11, 14), dateFin: new Date(2099, 11, 18), commentaire: '' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectOne(r => r.url === `${api}/conges/demandes/jours-ouvrables`).flush({ dateDebut: '2099-12-14', dateFin: '2099-12-18', joursOuvrables: 4, joursCalendaires: 5 });
    harness.detectChanges();
    (harness.routeNativeElement!.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectOne(r => r.method === 'POST').flush({ message: 'Données invalides', erreursChamps: { commentaire: 'Le commentaire ne doit pas dépasser 500 caractères' } }, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(texte()).toContain('Le commentaire ne doit pas dépasser 500 caractères');
  });
});
