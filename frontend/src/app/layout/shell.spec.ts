import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { Notifications } from '../core/notifications/notifications';

// The first navigation compiles the lazy pages: slower than 5 s while the whole suite runs in parallel
describe('Shell: navigation drawer on small screens', { timeout: 15000 }, () => {
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
  }, 15_000); // hooks have their own limit (10 s), not the describe timeout

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

  it('makes the page behind the open drawer inert and gives focus back to the menu button on close', () => {
    const main = doc().querySelector<HTMLElement>('main.travail')!;
    bouton().click();
    harness.detectChanges();
    expect(main.hasAttribute('inert')).toBe(true);
    // The test runs at desktop width, where the menu button is hidden: check the call, not document.activeElement
    const focus = vi.spyOn(bouton(), 'focus');
    doc().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    harness.detectChanges();
    expect(main.hasAttribute('inert')).toBe(false);
    expect(focus).toHaveBeenCalled();
  });
  it('shows the unread count next to « Notifications » and as a dot on the menu button', () => {
    TestBed.inject(Notifications).nonLues.set(3);
    harness.detectChanges();
    const lien = [...doc().querySelectorAll('nav .lien')].find(a => a.textContent?.includes('Notifications'))!;
    expect(lien.querySelector('.compte')?.textContent?.trim()).toBe('3');
    expect(lien.getAttribute('aria-label')).toBe('Notifications, 3 non lues');
    expect(bouton().querySelector('.point')).not.toBeNull();
  });
});
