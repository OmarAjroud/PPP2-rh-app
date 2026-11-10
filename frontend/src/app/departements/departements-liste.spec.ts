import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { By } from '@angular/platform-browser';
import { environment } from '../../environments/environment';
import { ExportListe } from '../shared/export-liste';
import { DepartementsListe } from './departements-liste';

describe('Department list', () => {
  afterEach(() => localStorage.clear());

  it('offers the department list in Excel and PDF to everyone', async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'departments', component: DepartementsListe }]), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/departments');
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/api/departements`).flush([{ id: 1, nom: 'Informatique', description: null, nombreEmployes: 4 }]);
    harness.detectChanges();
    const e = harness.routeDebugElement!.query(By.directive(ExportListe))?.componentInstance as ExportListe;
    expect(e.liste()).toBe('departements');
    expect(e.filtres()).toEqual({});
  });
});
