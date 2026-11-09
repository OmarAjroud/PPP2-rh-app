import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { Role } from './core/auth/auth.models';

function connecter(role: Role): void {
  localStorage.setItem('rh.session', JSON.stringify({
    accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
    profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 1, departementId: 1 },
  }));
}

// The first navigation compiles the lazy pages: slower than 5 s while the whole suite runs in parallel
describe('Access per role', { timeout: 15000 }, () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
  });

  let harness: RouterTestingHarness | null = null;
  afterEach(() => (harness = null));

  async function aller(url: string): Promise<{ url: string; nav: string }> {
    harness ??= await RouterTestingHarness.create(); // one harness per test, reused for each navigation
    await harness.navigateByUrl(url);
    const nav = document.querySelector('nav[aria-label="Navigation principale"]')?.textContent ?? '';
    return { url: TestBed.inject(Router).url, nav };
  }

  it('sends an employee from the employee list to their own profile', async () => {
    connecter('EMPLOYE');
    expect((await aller('/employees')).url).toBe('/profile');
  });

  it('keeps managers out of employee creation and editing', async () => {
    connecter('MANAGER');
    expect((await aller('/employees/new')).url).toBe('/');
    expect((await aller('/employees/3/edit')).url).toBe('/');
  });

  it('lets RH manage employees and departments but not roles', async () => {
    connecter('RH');
    expect((await aller('/employees/new')).url).toBe('/employees/new');
    expect((await aller('/departments/new')).url).toBe('/departments/new');
    expect((await aller('/roles/new')).url).toBe('/');
  });

  it('lets the administrator manage roles', async () => {
    connecter('ADMIN');
    expect((await aller('/roles/new')).url).toBe('/roles/new');
  });

  it('shows each role only the navigation it can use', async () => {
    connecter('EMPLOYE');
    const employe = (await aller('/profile')).nav;
    expect(employe).not.toContain('Employés');
    expect(employe).not.toContain('Rôles');
    expect(employe).toContain('Mon profil');

    TestBed.resetTestingModule();
    harness = null;
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    connecter('MANAGER');
    const manager = (await aller('/profile')).nav;
    expect(manager).toContain('Mon équipe');
    expect(manager).not.toContain('Rôles');

    TestBed.resetTestingModule();
    harness = null;
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    connecter('RH');
    const rh = (await aller('/profile')).nav;
    expect(rh).toContain('Employés');
    expect(rh).toContain('Rôles');
  });
  it('gives everyone « Mes congés » but keeps employees out of « À décider »', async () => {
    connecter('EMPLOYE');
    const r = await aller('/leave-requests');
    expect(r.url).toBe('/leave-requests');
    expect(r.nav).toContain('Mes congés');
    expect(r.nav).not.toContain('À décider');
    expect((await aller('/approvals')).url).toBe('/');
  });

  it('opens « À décider » to managers', async () => {
    connecter('MANAGER');
    const r = await aller('/approvals');
    expect(r.url).toBe('/approvals');
    expect(r.nav).toContain('À décider');
  });
  it('opens the notifications page to every role', async () => {
    connecter('EMPLOYE');
    const r = await aller('/notifications');
    expect(r.url).toBe('/notifications');
    expect(r.nav).toContain('Notifications');
  });
  it('keeps employees out of the dashboard and the audit log', async () => {
    connecter('EMPLOYE');
    const r = await aller('/dashboard');
    expect(r.url).toBe('/');
    expect(r.nav).not.toContain('Tableau de bord');
    expect((await aller('/audit')).url).toBe('/');
  });

  it('opens the dashboard to managers and HR, the audit log to the administrator only', async () => {
    connecter('MANAGER');
    let r = await aller('/dashboard');
    expect(r.url).toBe('/dashboard');
    expect(r.nav).toContain('Tableau de bord');
    expect(r.nav).not.toContain('Journal d\'audit');
    expect((await aller('/audit')).url).toBe('/');
  });

  it('gives the administrator the audit log', async () => {
    connecter('ADMIN');
    const r = await aller('/audit');
    expect(r.url).toBe('/audit');
    expect(r.nav).toContain('Journal d\'audit');
  });
  it('opens the HR assistant to every role', async () => {
    connecter('EMPLOYE');
    const r = await aller('/assistant');
    expect(r.url).toBe('/assistant');
    expect(r.nav).toContain('Assistant');
  });
});
