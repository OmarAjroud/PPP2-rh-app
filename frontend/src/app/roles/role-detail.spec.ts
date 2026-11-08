import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { RoleDetailPage } from './role-detail';

describe('Role detail', () => {
  beforeEach(() => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'admin', role: 'ADMIN', employeId: 1, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'roles/:id', component: RoleDetailPage }]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => localStorage.clear());

  it('titles the system role ADMIN « Administrateur »', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/roles/1');
    TestBed.inject(HttpTestingController)
      .expectOne(r => r.url === `${environment.apiUrl}/api/roles/1`)
      .flush({ id: 1, nom: 'ADMIN', description: 'Accès complet' });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('h1')!.textContent!.trim()).toBe('Administrateur');
  });
});
