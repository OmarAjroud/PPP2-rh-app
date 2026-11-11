import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { EmployeForm } from './employe-form';

const api = `${environment.apiUrl}/api`;

describe('Employee form: phone and salary in dinars', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(url: string): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'rh', role: 'RH', employeId: 2, departementId: 2 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'employees/new', component: EmployeForm }, { path: 'employees/:id/edit', component: EmployeForm }, { path: 'employees/:id', children: [] }]),
        provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http.match(r => r.url.startsWith(`${api}/departements`) || r.url.startsWith(`${api}/roles`) || r.url.startsWith(`${api}/employes`) && r.method === 'GET' && !/\/employes\/\d+$/.test(r.url))
      .forEach(r => r.flush(r.request.url.includes('/employes') ? { contenu: [], numeroPage: 0, taillePage: 100, totalElements: 0, totalPages: 0, dernierePage: true } : []));
    harness.detectChanges();
  }
  afterEach(() => localStorage.clear());

  const el = () => harness.routeNativeElement!;
  const saisir = (id: string, v: string) => { const i = el().querySelector<HTMLInputElement>(`#${id}`)!; i.value = v; i.dispatchEvent(new Event('input')); i.dispatchEvent(new Event('blur')); };
  const form = () => harness.routeDebugElement!.componentInstance.form;

  it('asks for a phone number, and says which format when it is wrong', async () => {
    await ouvrir('/employees/new');
    const tel = el().querySelector<HTMLInputElement>('#telephone')!;
    expect(tel).not.toBeNull();
    expect(tel.type).toBe('tel');
    expect(el().querySelector('label[for="telephone"]')!.textContent).toContain('Téléphone');
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    harness.detectChanges();
    expect(el().textContent).toContain('Indiquez un numéro de téléphone');
    saisir('telephone', 'appelez-moi');
    harness.detectChanges();
    expect(el().textContent).toContain('ex. +216 22 345 678');
  });

  it('sends the phone number with the new employee', async () => {
    await ouvrir('/employees/new');
    form().setValue({
      nom: 'Haddad', prenom: 'Sami', email: 'sami@rh.com', telephone: ' +216 22 345 678 ',
      dateEmbauche: new Date(2026, 0, 5), salaire: 2345.678, departementId: 1, roleId: 4, managerId: null,
    });
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/employes`);
    expect(req.request.body.telephone).toBe('+216 22 345 678');
    expect(req.request.body.salaire).toBe(2345.678);
  });

  it('shows the salary field in dinars', async () => {
    await ouvrir('/employees/new');
    expect(el().querySelector('label[for="salaire"]')!.textContent).toContain('DT');
  });

  it('fills the phone number when editing', async () => {
    await ouvrir('/employees/4/edit');
    http.expectOne(`${api}/employes/4`).flush({
      id: 4, nom: 'Gharbi', prenom: 'Lina', email: 'employe@rh.com', telephone: '+216 98 562 114', dateEmbauche: '2023-09-01',
      salaire: 2500, departementId: 1, departementNom: 'Informatique', roleId: 4, roleNom: 'EMPLOYE', managerId: 3, managerNomComplet: 'Karim Mansour',
    });
    await harness.fixture.whenStable();
    expect(form().controls.telephone.value).toBe('+216 98 562 114');
  });

  it('refuses a number with too few digits, and accepts spaces pasted from a document', async () => {
    await ouvrir('/employees/new');
    const tel = harness.routeDebugElement!.componentInstance.form.controls.telephone;
    tel.setValue('1------2');
    expect(tel.valid).toBe(false);
    tel.setValue('+216 22 345 678');
    expect(tel.valid).toBe(true);
  });
});
