import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Routing and guards', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient()] });
  });

  it('sends a visitor without a session from the home page "/" to the login page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2F');
  });

  it('opens the public pages without a session', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/forgot-password');
    expect(TestBed.inject(Router).url).toBe('/forgot-password');
  });

  it('sends a visitor without a session to the login page, remembering where they were going', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/security');
    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fsecurity');
  });

  it('shows the shell with navigation and logout to a signed-in user', async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'manager', role: 'MANAGER', employeId: 3, departementId: 1 },
    }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/security');
    const nav = harness.routeNativeElement?.ownerDocument.querySelector('nav[aria-label="Navigation principale"]');
    expect(nav?.textContent).toContain('Manager');
    expect(nav?.textContent).toContain('Déconnexion');
  });
});
