import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Barres, Colonnes, Donnee } from './graphiques';

@Component({
  imports: [Barres, Colonnes],
  template: `
    <app-barres titre="Effectif par département" [donnees]="donnees()" [format]="employes" />
    <app-colonnes titre="Embauches par mois" [donnees]="donnees()" />
  `,
})
class Hote {
  readonly donnees = signal<Donnee[]>([]);
  readonly employes = (v: number) => `${v} ${v > 1 ? 'employés' : 'employé'}`;
}

describe('Graphiques', () => {
  function rendre(donnees: Donnee[]): HTMLElement {
    const f = TestBed.createComponent(Hote);
    f.componentInstance.donnees.set(donnees);
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  it('draws bars in proportion to the largest value, value written at the tip', () => {
    const el = rendre([{ libelle: 'Informatique', valeur: 4 }, { libelle: 'Finance', valeur: 2 }]);
    // the bar is sized from its share (--p) of a track that excludes the value label, so it never shrinks
    const barres = el.querySelectorAll<HTMLElement>('app-barres .barre');
    expect(barres[0].style.getPropertyValue('--p')).toBe('1');
    expect(barres[1].style.getPropertyValue('--p')).toBe('0.5');
    const colonnes = el.querySelectorAll<HTMLElement>('app-colonnes .colonne');
    expect(colonnes[0].style.getPropertyValue('--p')).toBe('1');
    expect(colonnes[1].style.getPropertyValue('--p')).toBe('0.5');
    expect(el.querySelector('app-barres')!.textContent).toContain('4 employés');
  });

  it('draws no bar for a zero and never divides by zero', () => {
    const el = rendre([{ libelle: 'Finance', valeur: 0 }, { libelle: 'Juridique', valeur: 0 }]);
    expect(el.querySelectorAll('app-barres .barre')).toHaveLength(0);
    expect(el.querySelectorAll('app-colonnes .colonne')).toHaveLength(0);
    expect(el.textContent).not.toContain('NaN');
    expect(el.querySelector('app-barres')!.textContent).toContain('0 employé');
  });

  it('gives each mark an accessible name with its value, reachable by keyboard', () => {
    const el = rendre([{ libelle: 'Informatique', valeur: 3 }]);
    const ligne = el.querySelector('app-barres [tabindex="0"]')!;
    expect(ligne.getAttribute('aria-label')).toBe('Informatique : 3 employés');
    expect(el.querySelector('app-colonnes [tabindex="0"]')!.getAttribute('aria-label')).toBe('Informatique : 3');
  });

  it('offers the same numbers as a table', () => {
    const el = rendre([{ libelle: 'Informatique', valeur: 3 }, { libelle: 'Finance', valeur: 1 }]);
    const lignes = el.querySelectorAll('app-barres details table tbody tr');
    expect(lignes).toHaveLength(2);
    expect(lignes[0].textContent).toContain('Informatique');
    expect(lignes[0].textContent).toContain('3 employés');
    expect(el.querySelector('app-barres details caption')!.textContent).toContain('Effectif par département');
  });

  it('reads each column as « label : value » to screen readers, the visual value hidden', () => {
    const el = rendre([{ libelle: 'Mars', valeur: 3 }]);
    const colonne = el.querySelector('app-colonnes li')!;
    expect(colonne.querySelector('.sr-only')!.textContent!.trim()).toBe('Mars : 3');
    expect(colonne.querySelector('.valeur')!.getAttribute('aria-hidden')).toBe('true');
  });
});
