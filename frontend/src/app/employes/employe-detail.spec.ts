import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { EmployeDetailPage } from './employe-detail';

const api = `${environment.apiUrl}/api`;
const fiche = { id: 4, prenom: 'Lina', nom: 'Gharbi', email: 'employe@rh.com', telephone: '+216 98 562 114', departementId: 1, departementNom: 'Informatique', roleId: 4, roleNom: 'EMPLOYE', dateEmbauche: '2023-09-01', salaire: 2500, managerId: 3, managerNomComplet: 'Karim Mansour' };

describe('Fiche employé : historique du solde', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, employeId: number, url: string): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'employees/:id', component: EmployeDetailPage }, { path: 'profile', component: EmployeDetailPage, data: { moi: true } }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http.match(r => r.url.endsWith('/employes/4') || r.url.endsWith('/employes/me')).forEach(r => r.flush(fiche));
    http.match(`${api}/types-conge`).forEach(r => r.flush([{ id: 1, code: 'CP', libelle: 'Congé annuel', deduitDuSolde: true, joursParAn: 22, retroactifAutorise: false }]));
    harness.detectChanges();
    // the history component asks for the leave types once it appears
    http.match(`${api}/types-conge`).forEach(r => r.flush([{ id: 1, code: 'CP', libelle: 'Congé annuel', deduitDuSolde: true, joursParAn: 22, retroactifAutorise: false }]));
    harness.detectChanges();
  }
  afterEach(() => localStorage.clear());

  const bouton = () => [...harness.routeNativeElement!.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Ajuster le solde');

  it('lets HR adjust someone else\'s balance, then reloads the balances shown on the record', async () => {
    await ouvrir('RH', 2, '/employees/4');
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    http.match(r => r.url === `${api}/conges/soldes/4`).forEach(r => r.flush([]));
    harness.detectChanges();
    bouton()!.click();
    harness.detectChanges();
    const el = harness.routeNativeElement!;
    const saisir = (sel: string, v: string) => { const i = el.querySelector<HTMLInputElement>(sel)!; i.value = v; i.dispatchEvent(new Event('input')); };
    saisir('#ajust-jours', '2');
    saisir('#ajust-motif', 'Report');
    el.querySelector<HTMLFormElement>('form.ajustement')!.dispatchEvent(new Event('submit'));
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/soldes/4/ajustements`).flush({});
    await harness.fixture.whenStable();
    http.expectOne(r => r.url === `${api}/conges/soldes/4`).flush([]);
  });

  it('never offers HR to adjust their own balance, even on their record page', async () => {
    await ouvrir('RH', 4, '/employees/4');
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    harness.detectChanges();
    expect(bouton()).toBeUndefined();
  });

  it('shows a manager the history but no adjustment', async () => {
    await ouvrir('MANAGER', 3, '/employees/4');
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    harness.detectChanges();
    expect(bouton()).toBeUndefined();
  });

  it('shows the phone number as a link to call, and the salary in Tunisian dinars', async () => {
    await ouvrir('RH', 2, '/employees/4');
    http.match(r => r.url.startsWith(`${api}/conges/soldes/4`)).forEach(r => r.flush([]));
    harness.detectChanges();
    const el = harness.routeNativeElement!;
    const lien = el.querySelector<HTMLAnchorElement>('a[href^="tel:"]')!;
    expect(lien.getAttribute('href')).toBe('tel:+21698562114');
    expect(lien.textContent!.trim()).toBe('+216 98 562 114');
    expect(el.textContent!.replace(/[  ]/g, ' ')).toContain('2 500,000 DT');
    expect(el.textContent).not.toContain('€');
  });

  it('shows my own history on my profile, without adjustment', async () => {
    await ouvrir('RH', 4, '/profile');
    http.expectOne(r => r.url === `${api}/conges/soldes/moi/mouvements`).flush([]);
    harness.detectChanges();
    expect(bouton()).toBeUndefined();
  });
});
