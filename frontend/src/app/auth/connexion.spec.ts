import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Connexion } from './connexion';

describe('Login page notices', () => {
  async function ouvrir(url: string): Promise<string> {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'login', component: Connexion }]), provideHttpClient(), provideHttpClientTesting()] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement!.textContent!.replace(/\s+/g, ' ');
  }

  it('says the session was closed after 15 minutes of inactivity', async () => {
    expect(await ouvrir('/login?idle=1')).toContain('déconnecté après 15 minutes d’inactivité');
  });

  it('says nothing special on a normal visit', async () => {
    const texte = await ouvrir('/login');
    expect(texte).not.toContain('inactivité');
    expect(texte).not.toContain('expiré');
  });
});
