import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { Securite } from './securite';

const url = `${environment.apiUrl}/api/admin/mail/test`;

describe('Security page: test email', () => {
  let http: HttpTestingController;

  function rendre(role: Role) {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 1, departementId: 1 },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(Securite);
    f.detectChanges();
    return f;
  }
  afterEach(() => { http.verify(); localStorage.clear(); });

  const bouton = (el: HTMLElement) => [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Envoyer un email de test');

  it('lets an administrator send a test email and shows where it went', async () => {
    const f = rendre('ADMIN');
    const el = f.nativeElement as HTMLElement;
    bouton(el)!.click();
    http.expectOne(r => r.method === 'POST' && r.url === url).flush({ message: 'Email de test en cours d’envoi à admin@rh-app.local' }, { status: 202, statusText: 'Accepted' });
    await f.whenStable();
    f.detectChanges();
    expect(el.querySelector('.envoi-mail [role="status"]')!.textContent).toContain('admin@rh-app.local');
  });

  it('shows why the email could not be sent', async () => {
    const f = rendre('ADMIN');
    const el = f.nativeElement as HTMLElement;
    bouton(el)!.click();
    http.expectOne(url).flush({ message: 'Employé introuvable' }, { status: 404, statusText: 'Not Found' });
    await f.whenStable();
    f.detectChanges();
    expect(el.querySelector('.envoi-mail [role="alert"]')!.textContent).toContain('Employé introuvable');
  });

  it('is offered to administrators only', () => {
    for (const role of ['RH', 'MANAGER', 'EMPLOYE'] as Role[]) {
      TestBed.resetTestingModule();
      const f = rendre(role);
      expect(bouton(f.nativeElement)).toBeUndefined();
    }
  });
});
