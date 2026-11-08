import { Component, computed, input } from '@angular/core';

/** One category and its value; a chart draws a single series (no legend: the title names it). */
export interface Donnee {
  libelle: string;
  valeur: number;
}

type Format = (v: number) => string;
const brut: Format = v => new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(v);

/** Share of the largest value, in %: 0 when everything is 0 (no division by zero). */
function proportions(donnees: Donnee[]): number[] {
  const max = Math.max(0, ...donnees.map(d => d.valeur));
  return donnees.map(d => (max > 0 ? Math.round((d.valeur / max) * 1000) / 10 : 0));
}

const TABLEAU = `
  <details class="tableau">
    <summary>Voir le tableau</summary>
    <table>
      <caption>{{ titre() }}</caption>
      <thead><tr><th scope="col">{{ colonneLibelle() }}</th><th scope="col" class="num">{{ colonneValeur() }}</th></tr></thead>
      <tbody>
        @for (d of donnees(); track d.libelle) { <tr><th scope="row">{{ d.libelle }}</th><td class="num">{{ format()(d.valeur) }}</td></tr> }
      </tbody>
    </table>
  </details>
`;

const STYLES_COMMUNS = `
  :host { display: block; }
  .bulle {
    position: absolute; z-index: 5; pointer-events: none; opacity: 0;
    padding: 6px 10px; border: 1px solid var(--rh-line); border-radius: var(--rh-radius-sm);
    background: var(--rh-surface); box-shadow: var(--rh-shadow-overlay);
    color: var(--rh-ink-2); font-size: var(--rh-text-sm); white-space: nowrap;
    transition: opacity var(--rh-fast) var(--rh-ease);
  }
  .bulle b { color: var(--rh-ink); font-weight: 600; }
  [tabindex="0"]:hover .bulle, [tabindex="0"]:focus-visible .bulle { opacity: 1; }
  [tabindex="0"]:focus-visible { outline: none; box-shadow: var(--rh-focus); border-radius: var(--rh-radius-sm); }
  .tableau { margin-top: var(--rh-space-3); font-size: var(--rh-text-sm); }
  .tableau summary { cursor: pointer; color: var(--rh-accent-text); font-weight: 500; width: fit-content; }
  .tableau table { width: 100%; margin-top: var(--rh-space-2); border-collapse: collapse; }
  .tableau caption { text-align: left; color: var(--rh-muted); padding-bottom: var(--rh-space-1); }
  .tableau th, .tableau td { padding: 6px 4px; border-bottom: 1px solid var(--rh-line-row); text-align: left; font-weight: 400; }
  .tableau thead th { color: var(--rh-muted); }
  .tableau .num { text-align: right; font-family: var(--rh-font-mono); font-variant-numeric: tabular-nums; }
`;

/**
 * Horizontal bars (magnitude by category): bars at most 16px thick, square at the baseline,
 * rounded 4px at the value end, the value written at the tip, tooltip on hover and focus.
 */
@Component({
  selector: 'app-barres',
  template: `
    <ul class="barres" [attr.aria-label]="titre()">
      @for (d of donnees(); track d.libelle; let i = $index) {
        <li tabindex="0" [attr.aria-label]="d.libelle + ' : ' + format()(d.valeur)">
          <span class="libelle">{{ d.libelle }}</span>
          <span class="piste">
            @if (parts()[i] > 0) { <span class="barre" [style.width.%]="parts()[i]"></span> }
            <span class="valeur">{{ format()(d.valeur) }}</span>
          </span>
          <span class="bulle" aria-hidden="true"><b>{{ format()(d.valeur) }}</b> · {{ d.libelle }}@if (total() > 0) { · {{ pourcent(d) }} % }</span>
        </li>
      } @empty {
        <li class="vide">Aucune donnée.</li>
      }
    </ul>
    ${TABLEAU}
  `,
  styles: STYLES_COMMUNS + `
    .barres { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
    li[tabindex] { position: relative; display: grid; grid-template-columns: minmax(80px, 34%) minmax(0, 1fr); align-items: center; gap: var(--rh-space-3); min-height: 32px; padding: 0 4px; }
    li[tabindex]:hover { background: var(--rh-surface-2); border-radius: var(--rh-radius-sm); }
    .libelle { color: var(--rh-ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .piste { display: flex; align-items: center; gap: var(--rh-space-2); min-width: 0; }
    .barre { display: block; height: 16px; max-width: calc(100% - 7ch); background: var(--rh-accent); border-radius: 0 4px 4px 0; }
    .valeur { flex: none; color: var(--rh-ink); font: 500 var(--rh-text-sm) var(--rh-font-mono); font-variant-numeric: tabular-nums; }
    .bulle { left: 34%; bottom: calc(100% + 4px); }
    .vide { color: var(--rh-muted); padding: var(--rh-space-2) 0; }
  `,
})
export class Barres {
  readonly titre = input.required<string>();
  readonly donnees = input.required<Donnee[]>();
  readonly format = input<Format>(brut);
  readonly colonneLibelle = input('Catégorie');
  readonly colonneValeur = input('Valeur');
  protected readonly parts = computed(() => proportions(this.donnees()));
  protected readonly total = computed(() => this.donnees().reduce((s, d) => s + d.valeur, 0));
  protected pourcent(d: Donnee): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format((d.valeur / this.total()) * 100);
  }
}

/**
 * Columns (a value per period): columns at most 24px wide, square at the baseline, rounded 4px
 * at the top, the value on the cap when it is not zero, tooltip on hover and focus.
 */
@Component({
  selector: 'app-colonnes',
  template: `
    <ul class="colonnes" [attr.aria-label]="titre()">
      @for (d of donnees(); track d.libelle; let i = $index) {
        <li tabindex="0" [attr.aria-label]="d.libelle + ' : ' + format()(d.valeur)">
          <span class="zone">
            @if (d.valeur > 0) { <span class="valeur">{{ format()(d.valeur) }}</span> }
            @if (parts()[i] > 0) { <span class="colonne" [style.height.%]="parts()[i]"></span> }
          </span>
          <span class="libelle">{{ d.libelle }}</span>
          <span class="bulle" aria-hidden="true"><b>{{ format()(d.valeur) }}</b> · {{ d.libelle }}</span>
        </li>
      }
    </ul>
    ${TABLEAU}
  `,
  styles: STYLES_COMMUNS + `
    .colonnes { list-style: none; margin: 0; padding: 0; display: grid; grid-auto-flow: column; grid-auto-columns: minmax(0, 1fr); gap: 2px; border-bottom: 1px solid var(--rh-line-strong); }
    li[tabindex] { position: relative; display: grid; grid-template-rows: 160px auto; justify-items: center; }
    li[tabindex]:hover .zone { background: var(--rh-surface-2); }
    .zone { width: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 4px; border-radius: var(--rh-radius-sm) var(--rh-radius-sm) 0 0; }
    .colonne { display: block; width: min(24px, 70%); max-height: calc(100% - 22px); background: var(--rh-accent); border-radius: 4px 4px 0 0; }
    .valeur { color: var(--rh-ink); font: 500 var(--rh-text-xs) var(--rh-font-mono); }
    .libelle { padding-top: 6px; color: var(--rh-muted); font-size: var(--rh-text-xs); }
    .bulle { bottom: calc(100% - 20px); left: 50%; transform: translateX(-50%); }
  `,
})
export class Colonnes {
  readonly titre = input.required<string>();
  readonly donnees = input.required<Donnee[]>();
  readonly format = input<Format>(brut);
  readonly colonneLibelle = input('Période');
  readonly colonneValeur = input('Valeur');
  protected readonly parts = computed(() => proportions(this.donnees()));
}
