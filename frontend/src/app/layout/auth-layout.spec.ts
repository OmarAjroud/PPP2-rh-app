import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';

describe('AuthLayout: login screens', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('rh-dark');
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient()] });
  });

  it('forces the dark theme even when the app is in light, and gives the choice back on leaving', async () => {
    localStorage.setItem('rh.theme', 'clair');
    // Signed in, so the account link page (no guard) can be left for an app page
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'rh', role: 'RH', employeId: 2, departementId: 1 },
    }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/activate-account?token=x');
    expect(document.documentElement.classList).toContain('rh-dark');
    await harness.navigateByUrl('/security');
    expect(document.documentElement.classList).not.toContain('rh-dark');
  });

  // Kept as a guard: the login screens stay free of overlay components (dropdowns, date pickers).
  for (const url of ['/login', '/forgot-password', '/activate-account?token=x', '/reset-password?token=x']) {
    it(`uses no overlay component on ${url}`, async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      const page = harness.routeNativeElement!.ownerDocument.querySelector('app-auth-layout')!;
      expect(page.querySelector('router-outlet + *')).not.toBeNull();
      expect(page.querySelector('p-select, p-datepicker, p-autocomplete, p-multiselect, p-cascadeselect, p-treeselect')).toBeNull();
    });
  }
});
