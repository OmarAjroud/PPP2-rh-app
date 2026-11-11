import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { NoeudOrganigramme } from '../core/api/models';
import { Organigramme } from './organigramme';

const n = (id: number, prenom: string, nom: string, managerId: number | null, roleNom = 'EMPLOYE'): NoeudOrganigramme => ({
  id, nom, prenom, email: `${prenom.toLowerCase()}@rh.com`, roleNom, departementId: 1, departementNom: 'Informatique', managerId,
});
const societe = [n(1, 'Amine', 'Ben Salah', null, 'ADMIN'), n(3, 'Karim', 'Mansour', 1, 'MANAGER'), n(4, 'Lina', 'Gharbi', 3), n(2, 'Sara', 'Trabelsi', 1, 'RH')];

describe('Organigramme', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'rh', role: 'RH', employeId: 2, departementId: 2 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'org-chart', component: Organigramme }, { path: 'employees/:id', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/org-chart');
    http.expectOne(`${environment.apiUrl}/api/organigramme`).flush(societe);
    harness.detectChanges();
  });
  afterEach(() => { http.verify(); localStorage.clear(); });

  const el = () => harness.routeNativeElement!;
  const visibles = () => [...el().querySelectorAll<HTMLElement>('.carte .nom')].map(c => c.textContent!.trim());
  const champ = () => el().querySelector<HTMLInputElement>('#organigramme-recherche')!;
  const taper = (t: string) => { champ().value = t; champ().dispatchEvent(new Event('input')); harness.detectChanges(); };
  const bascule = (prenom: string) => [...el().querySelectorAll<HTMLButtonElement>('button.equipe')].find(b => b.getAttribute('aria-label')?.includes(prenom))!;

  it('shows the top of the company with its direct teams open, each person with role, department and a link', () => {
    expect(visibles()).toEqual(['Amine Ben Salah', 'Karim Mansour', 'Sara Trabelsi']);
    const amine = el().querySelector('.carte')!;
    expect(amine.textContent).toContain('Administrateur');
    expect(amine.textContent).toContain('Informatique');
    expect(el().querySelector('a[href="/employees/3"]')).not.toBeNull();
  });

  it('opens and closes a team, saying how many people are in it', () => {
    const karim = bascule('Karim');
    expect(karim.getAttribute('aria-expanded')).toBe('false');
    expect(karim.getAttribute('aria-label')).toBe('Équipe de Karim Mansour : 1 personne');
    expect(karim.hasAttribute('aria-controls')).toBe(false); // the team is not on the page while closed
    expect(karim.textContent).toContain('1');
    karim.click();
    harness.detectChanges();
    expect(karim.getAttribute('aria-expanded')).toBe('true');
    expect(visibles()).toContain('Lina Gharbi');
    karim.click();
    harness.detectChanges();
    expect(visibles()).not.toContain('Lina Gharbi');
  });

  it('opens or closes everything at once', () => {
    [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.trim() === 'Tout déplier')!.click();
    harness.detectChanges();
    expect(visibles()).toContain('Lina Gharbi');
    [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.trim() === 'Tout replier')!.click();
    harness.detectChanges();
    expect(visibles()).toEqual(['Amine Ben Salah']);
  });

  it('finds a person, opens the way to them and marks their card', async () => {
    const champ = el().querySelector<HTMLInputElement>('#organigramme-recherche')!;
    champ.value = 'lina';
    champ.dispatchEvent(new Event('input'));
    harness.detectChanges();
    const resultat = [...el().querySelectorAll<HTMLButtonElement>('.resultats button')].find(b => b.textContent?.includes('Lina Gharbi'))!;
    expect(resultat).toBeDefined();
    resultat.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(visibles()).toContain('Lina Gharbi');
    expect(el().querySelector('.carte.choisie .nom')!.textContent!.trim()).toBe('Lina Gharbi');
    expect(el().querySelector('.resultats')).toBeNull();
    expect(document.activeElement?.textContent?.trim()).toBe('Lina Gharbi');
  });

  it('says when nobody matches', () => {
    const champ = el().querySelector<HTMLInputElement>('#organigramme-recherche')!;
    champ.value = 'zzz';
    champ.dispatchEvent(new Event('input'));
    harness.detectChanges();
    expect(el().textContent).toContain('Aucune personne');
  });

  it('tells screen readers how many people match, as the text changes', () => {
    document.body.appendChild(el());
    taper('a');
    const statut = el().querySelector('.recherche [role="status"]')!;
    expect(statut.textContent).toContain('4 résultats');
    taper('lina');
    expect(statut.textContent).toContain('1 résultat');
    expect(champ().getAttribute('aria-expanded')).toBe('true');
    expect(champ().getAttribute('aria-controls')).toBe('organigramme-resultats');
    el().remove();
  });

  it('moves through the results with the arrow keys, and Escape goes back to the field', () => {
    document.body.appendChild(el());
    champ().focus();
    taper('a');
    champ().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    const resultats = [...el().querySelectorAll<HTMLButtonElement>('.resultats button')];
    expect(document.activeElement).toBe(resultats[0]);
    resultats[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    expect(document.activeElement).toBe(resultats[1]);
    resultats[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    resultats[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    expect(document.activeElement).toBe(champ());
    champ().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    harness.detectChanges();
    expect(document.activeElement).toBe(champ());
    expect(el().querySelector('.resultats')).toBeNull();
    el().remove();
  });

  it('closes the results when the keyboard leaves the search', () => {
    taper('lina');
    el().querySelector('.recherche')!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: el().querySelector('button.equipe') }));
    harness.detectChanges();
    expect(el().querySelector('.resultats')).toBeNull();
  });

  it('counts teams as drawn in the chart', () => {
    expect(el().textContent).toContain('4 personnes · 2 équipes');
  });
});
