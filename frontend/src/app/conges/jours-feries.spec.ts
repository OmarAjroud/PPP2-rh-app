import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { environment } from '../../environments/environment';
import { Role } from '../core/auth/auth.models';
import { JoursFeries } from './jours-feries';

registerLocaleData(localeFr);
const api = `${environment.apiUrl}/api/jours-feries`;
const annee = new Date().getFullYear();
const feries = [
  { id: 2, date: `${annee}-05-01`, libelle: 'Fête du Travail' },
  { id: 1, date: `${annee}-01-01`, libelle: 'Jour de l’An' },
];

describe('Jours fériés', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  async function ouvrir(role: Role, url = '/holidays'): Promise<void> {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: role.toLowerCase(), role, employeId: 2, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'holidays', component: JoursFeries }]), provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'fr' }],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
  }
  afterEach(() => { http.verify(); localStorage.clear(); });

  const el = () => harness.routeNativeElement!;
  const texte = () => el().textContent!.replace(/\s+/g, ' ');
  const liste = (a = annee) => http.expectOne(r => r.method === 'GET' && r.url === api && r.params.get('annee') === String(a));
  const bouton = (t: string) => [...el().querySelectorAll('button')].find(b => b.textContent?.trim() === t);

  it('lists the holidays of the year in date order, with the weekday, read-only for an employee', async () => {
    await ouvrir('EMPLOYE');
    liste().flush(feries);
    harness.detectChanges();
    const lignes = [...el().querySelectorAll('.ferie')].map(l => l.textContent!.replace(/\s+/g, ' '));
    expect(lignes[0]).toContain('Jour de l’An');
    expect(lignes[1]).toContain('Fête du Travail');
    expect(lignes[1]).toContain('1er mai'); // French: the first of the month is written « 1er »
    expect(el().querySelector('form.ajout')).toBeNull();
    expect(bouton('Supprimer')).toBeUndefined();
  });

  it('reads another year from the address', async () => {
    await ouvrir('EMPLOYE', `/holidays?annee=${annee + 1}`);
    liste(annee + 1).flush([]);
    harness.detectChanges();
    expect(texte()).toContain(`Aucun jour férié enregistré pour ${annee + 1}`);
  });

  it('lets HR add a holiday, then reloads the list', async () => {
    await ouvrir('RH');
    liste().flush(feries);
    harness.detectChanges();
    const cmp = harness.routeDebugElement!.componentInstance as { date: { set(d: Date): void }; libelle: { set(s: string): void } };
    cmp.date.set(new Date(annee, 6, 25));
    cmp.libelle.set(' Fête de la République ');
    harness.detectChanges();
    el().querySelector<HTMLFormElement>('form.ajout')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne(r => r.method === 'POST' && r.url === api);
    expect(req.request.body).toEqual({ date: `${annee}-07-25`, libelle: 'Fête de la République' });
    req.flush({ id: 3, date: `${annee}-07-25`, libelle: 'Fête de la République' });
    await harness.fixture.whenStable();
    liste().flush([...feries, { id: 3, date: `${annee}-07-25`, libelle: 'Fête de la République' }]);
    harness.detectChanges();
    expect(texte()).toContain('Fête de la République');
    expect(el().querySelector('.annonce')!.textContent).toContain('Jour férié ajouté');
  });

  it('shows the API message when the date is already taken', async () => {
    await ouvrir('RH');
    liste().flush(feries);
    harness.detectChanges();
    const cmp = harness.routeDebugElement!.componentInstance as { date: { set(d: Date): void }; libelle: { set(s: string): void } };
    cmp.date.set(new Date(annee, 4, 1));
    cmp.libelle.set('Doublon');
    el().querySelector<HTMLFormElement>('form.ajout')!.dispatchEvent(new Event('submit'));
    http.expectOne(r => r.method === 'POST').flush({ message: 'Un jour férié existe déjà à cette date' }, { status: 409, statusText: 'Conflict' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(el().querySelector('[role="alert"]')!.textContent).toContain('existe déjà');
  });

  it('lets HR delete a holiday after confirmation', async () => {
    await ouvrir('ADMIN');
    liste().flush(feries);
    harness.detectChanges();
    bouton('Supprimer')!.click();
    harness.detectChanges();
    http.expectNone(r => r.method === 'DELETE');
    bouton('Confirmer')!.click();
    http.expectOne(r => r.method === 'DELETE' && r.url === `${api}/1`).flush(null);
    await harness.fixture.whenStable();
    liste().flush([feries[0]]);
    harness.detectChanges();
    expect(texte()).not.toContain('Jour de l’An');
    expect(el().querySelector('.annonce')!.textContent).toContain('Jour férié supprimé');
  });

  it('tells HR that requests already filed keep their day count', async () => {
    await ouvrir('RH');
    liste().flush(feries);
    harness.detectChanges();
    expect(texte()).toContain('ne sont pas recalculées');
  });

  it('falls back to the current year when the address holds an unreadable year', async () => {
    for (const bad of ['1999', '1e3', '-5', '2026.5', 'abc']) {
      TestBed.resetTestingModule();
      await ouvrir('EMPLOYE', `/holidays?annee=${bad}`);
      liste().flush([]);
    }
  });

  it('keeps the keyboard where the person is when confirming or cancelling a delete', async () => {
    await ouvrir('ADMIN');
    liste().flush(feries);
    harness.detectChanges();
    document.body.appendChild(el());
    bouton('Supprimer')!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('Non');
    bouton('Non')!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('Supprimer');
    el().remove();
  });

  it('keeps the list on screen while it reloads', async () => {
    await ouvrir('ADMIN');
    liste().flush(feries);
    harness.detectChanges();
    bouton('Supprimer')!.click();
    harness.detectChanges();
    bouton('Confirmer')!.click();
    http.expectOne(r => r.method === 'DELETE').flush(null);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(texte()).not.toContain('Chargement');
    expect(el().querySelectorAll('.ferie').length).toBe(2);
    liste().flush([feries[0]]);
  });

  it('reloads when the holiday was already deleted by someone else', async () => {
    await ouvrir('ADMIN');
    liste().flush(feries);
    harness.detectChanges();
    bouton('Supprimer')!.click();
    harness.detectChanges();
    bouton('Confirmer')!.click();
    http.expectOne(r => r.method === 'DELETE').flush({ message: 'Jour férié introuvable' }, { status: 404, statusText: 'Not Found' });
    await harness.fixture.whenStable();
    liste().flush([feries[0]]);
  });
});
