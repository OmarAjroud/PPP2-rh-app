import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { EmployesListe } from './employes-liste';

describe('Employee list', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'rh', role: 'RH', employeId: 2, departementId: 2 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'employees', component: EmployesListe }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.clear());

  it('turns the URL (search, filter, sort, page) into the API query', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/employees?q=lina&dept=1&sort=departement&dir=desc&page=2&size=25');
    const req = http.expectOne(r => r.url === `${environment.apiUrl}/api/employes`);
    expect(req.request.params.get('recherche')).toBe('lina');
    expect(req.request.params.get('departementId')).toBe('1');
    expect(req.request.params.get('sort')).toBe('departement.nom,desc'); // a field the API accepts
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
  });

  it('sorts by name by default', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/employees');
    const req = http.expectOne(r => r.url === `${environment.apiUrl}/api/employes`);
    expect(req.request.params.get('sort')).toBe('nom,asc');
    expect(req.request.params.has('recherche')).toBe(false);
  });

  it('shows each person with department and role in French, repeated under the name for the stacked phone layout', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/employees');
    http.expectOne(r => r.url === `${environment.apiUrl}/api/employes`).flush({
      contenu: [{ id: 3, nom: 'Benali', prenom: 'Lina', email: 'lina@rh.com', departementId: 1, departementNom: 'Produit', roleId: 3, roleNom: 'MANAGER' }],
      numeroPage: 0, taillePage: 10, totalElements: 1, totalPages: 1, dernierePage: true,
    });
    http.match(() => true).forEach(r => r.flush([]));
    harness.detectChanges();
    const ligne = harness.routeNativeElement!.querySelector('tbody tr')!;
    expect(ligne.textContent).toContain('Lina Benali');
    expect(ligne.textContent).toContain('Produit');
    expect(ligne.textContent).toContain('Manager');
    expect(ligne.querySelector('td:first-child .meta')?.textContent?.replace(/s+/g, ' ').trim()).toBe('Produit · Manager');
  });
});
