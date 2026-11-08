import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';

describe('Shell: navigation drawer on small screens', () => {
  let harness: RouterTestingHarness;
  const doc = () => harness.routeNativeElement!.ownerDocument;
  const bouton = () => doc().querySelector<HTMLButtonElement>('button.menu')!;
  const rail = () => doc().querySelector<HTMLElement>('#rail')!;

  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'rh', role: 'RH', employeId: 2, departementId: 1 },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient()] });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/security');
  });

  it('opens and closes the drawer with the menu button', () => {
    expect(bouton().getAttribute('aria-expanded')).toBe('false');
    expect(bouton().getAttribute('aria-controls')).toBe('rail');
    bouton().click();
    harness.detectChanges();
    expect(bouton().getAttribute('aria-expanded')).toBe('true');
    expect(rail().classList).toContain('ouvert');
    bouton().click();
    harness.detectChanges();
    expect(rail().classList).not.toContain('ouvert');
  });

  it('closes the drawer after a navigation', async () => {
    bouton().click();
    harness.detectChanges();
    await harness.navigateByUrl('/profile');
    expect(rail().classList).not.toContain('ouvert');
  });

  it('closes the drawer on Escape', () => {
    bouton().click();
    harness.detectChanges();
    doc().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    harness.detectChanges();
    expect(rail().classList).not.toContain('ouvert');
    expect(bouton().getAttribute('aria-expanded')).toBe('false');
  });
});
