import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { AuditEntree } from '../core/api/models';
import { JournalAudit } from './journal-audit';

const api = `${environment.apiUrl}/api/admin/audit`;
const entree = (o: Partial<AuditEntree> = {}): AuditEntree => ({
  id: 1, dateAction: '2026-11-08T10:15:00', utilisateurId: 2, nomUtilisateur: 'rh', action: 'MODIFICATION',
  entite: 'Employe', entiteId: '4', anciennesValeurs: { salaire: 2500, nom: 'Gharbi' }, nouvellesValeurs: { salaire: 2700, nom: 'Gharbi' },
  details: null, adresseIp: '127.0.0.1', ...o,
});
const page = (contenu: AuditEntree[]) => ({ contenu, numeroPage: 0, taillePage: 20, totalElements: contenu.length, totalPages: 1, dernierePage: true });

describe('Journal d’audit', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'admin', role: 'ADMIN', employeId: 1, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'audit', component: JournalAudit }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => localStorage.clear());

  const el = () => harness.routeNativeElement!;
  const listes = () => {
    http.match(`${api}/actions`).forEach(r => r.flush(['CONNEXION', 'MODIFICATION']));
    http.match(`${api}/entites`).forEach(r => r.flush(['Employe', 'Departement']));
  };

  it('turns the filters in the URL into the API query', async () => {
    await harness.navigateByUrl('/audit?utilisateur=rh&action=CONNEXION&entite=Employe&du=2026-11-01&au=2026-11-08&page=2');
    listes();
    const req = http.expectOne(r => r.url === api);
    expect(req.request.params.get('nomUtilisateur')).toBe('rh');
    expect(req.request.params.get('action')).toBe('CONNEXION');
    expect(req.request.params.get('entite')).toBe('Employe');
    expect(req.request.params.get('du')).toBe('2026-11-01');
    expect(req.request.params.get('au')).toBe('2026-11-08');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
  });

  it('opens a row to show what changed, before → after, and where from', async () => {
    await harness.navigateByUrl('/audit');
    listes();
    http.expectOne(r => r.url === api).flush(page([entree()]));
    harness.detectChanges();
    expect(el().textContent).toContain('Modification');
    const bouton = el().querySelector<HTMLButtonElement>('button[aria-expanded]')!;
    expect(bouton.getAttribute('aria-expanded')).toBe('false');
    bouton.click();
    harness.detectChanges();
    expect(bouton.getAttribute('aria-expanded')).toBe('true');
    const detail = el().querySelector('.detail')!.textContent!.replace(/\s+/g, ' ');
    expect(detail).toContain('salaire');
    expect(detail).toContain('2500');
    expect(detail).toContain('2700');
    expect(detail).not.toContain('nom'); // unchanged field hidden
    expect(detail).toContain('127.0.0.1');
  });

  it('shows a login without values, its details only', async () => {
    await harness.navigateByUrl('/audit');
    listes();
    http.expectOne(r => r.url === api).flush(page([entree({ action: 'CONNEXION', entite: null, entiteId: null, anciennesValeurs: null, nouvellesValeurs: null, details: 'Connexion réussie' })]));
    harness.detectChanges();
    el().querySelector<HTMLButtonElement>('button[aria-expanded]')!.click();
    harness.detectChanges();
    expect(el().querySelector('.detail')!.textContent).toContain('Connexion réussie');
    expect(el().querySelector('.detail table')).toBeNull();
  });
});
