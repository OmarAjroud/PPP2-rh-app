import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { HistoriqueSoldes, lireJours } from './historique-soldes';

const api = `${environment.apiUrl}/api`;
const annee = new Date().getFullYear();
const mouvements = [
  { id: 1, dateMouvement: `${annee}-01-01T00:00:00`, typeCongeCode: 'CP', annee, typeMouvement: 'ALLOCATION', jours: 22, soldeApres: 22, demandeId: null, commentaire: `Allocation annuelle ${annee}`, creeParNomComplet: null },
  { id: 2, dateMouvement: `${annee}-03-10T09:00:00`, typeCongeCode: 'CP', annee, typeMouvement: 'DEDUCTION', jours: -4, soldeApres: 18, demandeId: 7, commentaire: null, creeParNomComplet: 'Karim Mansour' },
  { id: 3, dateMouvement: `${annee}-04-02T15:00:00`, typeCongeCode: 'CP', annee, typeMouvement: 'AJUSTEMENT', jours: 2.5, soldeApres: 20.5, demandeId: null, commentaire: 'Report de l’an dernier', creeParNomComplet: 'Sara Trabelsi' },
];

@Component({ imports: [HistoriqueSoldes], template: `<app-historique-soldes [employeId]="employeId()" [ajustable]="ajustable()" (modifie)="modifs = modifs + 1" />` })
class Hote {
  readonly employeId = signal<number | null>(null);
  readonly ajustable = signal(false);
  modifs = 0;
}

describe('Historique des soldes', () => {
  let http: HttpTestingController;

  function rendre(employeId: number | null, ajustable = false) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(Hote);
    f.componentInstance.employeId.set(employeId);
    f.componentInstance.ajustable.set(ajustable);
    f.detectChanges();
    http.match(`${api}/types-conge`).forEach(r => r.flush([
      { id: 2, code: 'MAL', libelle: 'Congé maladie', deduitDuSolde: false, joursParAn: 0, retroactifAutorise: true },
      { id: 1, code: 'CP', libelle: 'Congé annuel', deduitDuSolde: true, joursParAn: 22, retroactifAutorise: false },
    ]));
    return f;
  }
  const texte = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');

  it('shows my movements newest first, signed, with the balance after each one', () => {
    const f = rendre(null);
    const req = http.expectOne(r => r.url === `${api}/conges/soldes/moi/mouvements`);
    expect(req.request.params.get('annee')).toBe(String(annee));
    req.flush(mouvements);
    f.detectChanges();
    const lignes = [...(f.nativeElement as HTMLElement).querySelectorAll('.mouvement')].map(l => texte(l as HTMLElement));
    expect(lignes).toHaveLength(3);
    expect(lignes[0]).toContain('Ajustement');
    expect(lignes[0]).toContain('+2,5');
    expect(lignes[0]).toContain('Report de l’an dernier');
    expect(lignes[1]).toContain('Congé validé');
    expect(lignes[1]).toContain('−4');
    expect(lignes[1]).toContain('18');
    expect(lignes[2]).toContain('Allocation');
    expect((f.nativeElement as HTMLElement).querySelector('a[href="/leave-requests/7"]')).not.toBeNull();
  });

  it('reads an employee\'s history when given their id, without the adjustment form unless allowed', () => {
    const f = rendre(4);
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush(mouvements);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('form.ajustement')).toBeNull();
  });

  it('lets HR adjust a balance with a reason, then reloads and tells the page', async () => {
    const f = rendre(4, true);
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush(mouvements);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Ajuster le solde')!.click();
    f.detectChanges();
    const form = el.querySelector<HTMLFormElement>('form.ajustement')!;
    const saisir = (sel: string, v: string) => { const i = form.querySelector<HTMLInputElement>(sel)!; i.value = v; i.dispatchEvent(new Event('input')); };
    saisir('#ajust-jours', '-1,5');
    saisir('#ajust-motif', 'Erreur de saisie');
    f.detectChanges();
    form.dispatchEvent(new Event('submit'));
    const req = http.expectOne(r => r.method === 'POST' && r.url === `${api}/conges/soldes/4/ajustements`);
    expect(req.request.body).toEqual({ typeCongeId: 1, annee, jours: -1.5, commentaire: 'Erreur de saisie' });
    req.flush({});
    await f.whenStable();
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush(mouvements);
    expect(f.componentInstance.modifs).toBe(1);
  });

  it('refuses an adjustment of zero days or without a reason', () => {
    const f = rendre(4, true);
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Ajuster le solde')!.click();
    f.detectChanges();
    el.querySelector<HTMLFormElement>('form.ajustement')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
    http.expectNone(r => r.method === 'POST');
    expect(texte(el)).toContain('Indiquez un nombre de jours');
    expect(texte(el)).toContain('Indiquez le motif');
  });

  it('offers only the types that have a balance, the one adjusted being the one shown', () => {
    const f = rendre(4, true);
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Ajuster le solde')!.click();
    f.detectChanges();
    const options = [...el.querySelectorAll<HTMLOptionElement>('#ajust-type option')];
    expect(options.map(o => o.textContent!.trim())).toEqual(['Congé annuel']);
    expect(options[0].selected).toBe(true);
  });

  it('stays hidden, without an alarm, when the history is not visible to the reader (404, 403)', () => {
    const f = rendre(9);
    http.expectOne(r => r.url === `${api}/conges/soldes/9/mouvements`).flush({ message: 'Introuvable' }, { status: 404, statusText: 'Not Found' });
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('section')).toBeNull();
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('moves focus into the form and back, ties errors to their fields and announces the save', async () => {
    const f = rendre(4, true);
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    document.body.appendChild(el);
    [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Ajuster le solde')!.click();
    f.detectChanges();
    await f.whenStable();
    expect(document.activeElement?.id).toBe('ajust-jours');
    el.querySelector<HTMLFormElement>('form.ajustement')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
    const jours = el.querySelector<HTMLInputElement>('#ajust-jours')!;
    expect(jours.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#' + jours.getAttribute('aria-describedby'))!.textContent).toContain('Indiquez un nombre de jours');
    const saisir = (sel: string, v: string) => { const i = el.querySelector<HTMLInputElement>(sel)!; i.value = v; i.dispatchEvent(new Event('input')); };
    saisir('#ajust-jours', '2');
    saisir('#ajust-motif', 'Report');
    el.querySelector<HTMLFormElement>('form.ajustement')!.dispatchEvent(new Event('submit'));
    http.expectOne(r => r.method === 'POST').flush({});
    await f.whenStable();
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`).flush([]);
    f.detectChanges();
    await f.whenStable();
    expect(el.querySelector('.annonce')!.textContent).toContain('Ajustement enregistré');
    expect(document.activeElement?.textContent?.trim()).toBe('Ajuster le solde');
    el.remove();
  });

  it('never lets an older answer replace a newer one when the year changes quickly', () => {
    const f = rendre(4);
    const vieux = http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements`);
    const el = f.nativeElement as HTMLElement;
    const select = el.querySelector<HTMLSelectElement>('#historique-annee')!;
    select.value = String(annee - 1);
    select.dispatchEvent(new Event('change'));
    f.detectChanges();
    http.expectOne(r => r.url === `${api}/conges/soldes/4/mouvements` && r.params.get('annee') === String(annee - 1)).flush([]);
    expect(vieux.cancelled).toBe(true);
  });
});

describe('lireJours', () => {
  it('accepts the ways people type signed days', () => {
    expect(lireJours('-1,5')).toBe(-1.5);
    expect(lireJours('−2')).toBe(-2);
    expect(lireJours('–2')).toBe(-2);
    expect(lireJours('- 2')).toBe(-2);
    expect(lireJours('+3')).toBe(3);
    expect(lireJours(',5')).toBe(0.5);
  });
  it('refuses zero and what the API would refuse', () => {
    expect(lireJours('0,0')).toBeNull();
    expect(lireJours('1000')).toBeNull();
    expect(lireJours('1,25')).toBeNull();
    expect(lireJours('abc')).toBeNull();
  });
});
