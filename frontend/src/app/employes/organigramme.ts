import { Component, ElementRef, Injector, afterNextRender, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { NoeudOrganigramme } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { initiales, libelleRole } from '../core/api/libelles';
import { Apparition } from '../core/motion/motion';
import { Branche, chercher, cheminVers, construireArbre } from './organigramme-regles';

type Etat = { statut: 'chargement' } | { statut: 'ok'; noeuds: NoeudOrganigramme[] } | { statut: 'erreur'; message: string };

const MAX_RESULTATS = 8;

/**
 * Company org chart (ADMIN, RH): a top-down tree, each manager's team opened or closed on demand,
 * a search that opens the way to a person. On a phone the same tree reads as an indented list.
 */
@Component({
  selector: 'app-organigramme',
  imports: [NgTemplateOutlet, RouterLink, ButtonModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Entreprise</p><h1 class="page-titre">Organigramme</h1></div>
      </header>

      @switch (etat().statut) {
        @case ('chargement') { <p class="hint" aria-busy="true">Chargement…</p> }
        @case ('erreur') { <p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
        @case ('ok') {
          <div class="outils">
            <div class="recherche">
              <label class="sr-only" for="organigramme-recherche">Trouver une personne</label>
              <i class="pi pi-search" aria-hidden="true"></i>
              <input id="organigramme-recherche" class="p-inputtext" type="search" placeholder="Trouver une personne" autocomplete="off"
                     [value]="recherche()" (input)="recherche.set($any($event.target).value)" (keydown.escape)="recherche.set('')"
                     aria-describedby="organigramme-resultats">
              @if (recherche().trim()) {
                <div class="resultats" id="organigramme-resultats">
                  @for (p of resultats(); track p.id) {
                    <button type="button" (click)="montrer(p.id)">
                      <b>{{ p.prenom }} {{ p.nom }}</b> <span>{{ libelleRole(p.roleNom) }} · {{ p.departementNom }}</span>
                    </button>
                  } @empty {
                    <p class="hint" role="status">Aucune personne ne correspond.</p>
                  }
                </div>
              }
            </div>
            <span class="resume hint">{{ resume() }}</span>
            <div class="actions">
              <p-button label="Tout déplier" icon="pi pi-plus" severity="secondary" size="small" (onClick)="toutOuvrir()" />
              <p-button label="Tout replier" icon="pi pi-minus" severity="secondary" size="small" (onClick)="toutFermer()" />
            </div>
          </div>

          <div class="cadre" #cadre>
            <ul class="niveau racines">
              @for (b of arbre(); track b.personne.id) {
                <ng-container *ngTemplateOutlet="noeud; context: { $implicit: b }" />
              }
            </ul>
          </div>
        }
      }
    </div>

    <ng-template #noeud let-b>
      <li class="noeud">
        <div class="carte" [class.choisie]="choisie() === b.personne.id" [attr.data-id]="b.personne.id">
          <span class="avatar" aria-hidden="true">{{ initiales(b.personne.prenom, b.personne.nom) }}</span>
          <span class="texte">
            <a class="nom" [routerLink]="['/employees', b.personne.id]" [title]="b.personne.prenom + ' ' + b.personne.nom">{{ b.personne.prenom }} {{ b.personne.nom }}</a>
            <span class="poste" [title]="libelleRole(b.personne.roleNom)">{{ libelleRole(b.personne.roleNom) }}</span>
            <span class="dept" [title]="b.personne.departementNom">{{ b.personne.departementNom }}</span>
          </span>
          @if (b.equipe.length) {
            <button type="button" class="equipe" [attr.aria-expanded]="ouvert(b.personne.id)" [attr.aria-controls]="'equipe-' + b.personne.id"
                    [attr.aria-label]="'Équipe de ' + b.personne.prenom + ' ' + b.personne.nom + ' : ' + effectif(b)"
                    (click)="basculer(b.personne.id)">
              <i class="pi" [class.pi-chevron-down]="!ouvert(b.personne.id)" [class.pi-chevron-up]="ouvert(b.personne.id)" aria-hidden="true"></i>
              {{ b.total }}
            </button>
          }
        </div>
        @if (b.equipe.length && ouvert(b.personne.id)) {
          <ul class="niveau" [id]="'equipe-' + b.personne.id">
            @for (c of b.equipe; track c.personne.id) {
              <ng-container *ngTemplateOutlet="noeud; context: { $implicit: c }" />
            }
          </ul>
        }
      </li>
    </ng-template>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: none; }
    .outils { display: flex; flex-wrap: wrap; align-items: center; gap: var(--rh-space-3); }
    .recherche { position: relative; flex: 0 1 320px; }
    .recherche > i { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--rh-muted); pointer-events: none; }
    .recherche input { width: 100%; padding-left: 36px; }
    .resultats { position: absolute; z-index: 20; top: calc(100% + 4px); left: 0; right: 0; display: grid; padding: 4px; border: 1px solid var(--rh-line); border-radius: var(--rh-radius-sm); background: var(--rh-surface); box-shadow: var(--rh-shadow-md, 0 8px 24px rgb(0 0 0 / .12)); }
    .resultats button { display: grid; justify-items: start; gap: 1px; padding: 8px 10px; border: 0; border-radius: 6px; background: none; color: var(--rh-ink); font: inherit; text-align: left; cursor: pointer; }
    .resultats button:hover, .resultats button:focus-visible { background: var(--rh-surface-2); outline: none; }
    .resultats button b { font-weight: 500; }
    .resultats button span { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .resultats .hint { margin: 0; padding: 8px 10px; }
    .resume { flex: 1; }
    .actions { display: flex; gap: var(--rh-space-1); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

    /* Top-down tree: each team hangs from a vertical line, siblings joined by a horizontal one */
    .cadre { overflow-x: auto; padding: var(--rh-space-2) 0 var(--rh-space-6); }
    .niveau { display: flex; justify-content: center; margin: 0; padding: 0; list-style: none; }
    .racines { gap: var(--rh-space-6); width: max-content; min-width: 100%; }
    .niveau .niveau { position: relative; padding-top: 22px; }
    .niveau .niveau::before { content: ''; position: absolute; top: -12px; left: 50%; height: 34px; border-left: 1px solid var(--rh-line-strong); }
    .noeud { position: relative; display: flex; flex-direction: column; align-items: center; padding: 0 6px; }
    .niveau .niveau > .noeud { padding-top: 22px; }
    .niveau .niveau > .noeud::before, .niveau .niveau > .noeud::after { content: ''; position: absolute; top: 0; width: 50%; height: 22px; border-top: 1px solid var(--rh-line-strong); }
    .niveau .niveau > .noeud::before { right: 50%; }
    .niveau .niveau > .noeud::after { left: 50%; border-left: 1px solid var(--rh-line-strong); }
    .niveau .niveau > .noeud:first-child::before, .niveau .niveau > .noeud:last-child::after { border-top: 0; }
    .niveau .niveau > .noeud:last-child::before { border-right: 1px solid var(--rh-line-strong); border-top-right-radius: 6px; width: calc(50% + 1px); right: calc(50% - 1px); }
    .niveau .niveau > .noeud:first-child::after { border-top-left-radius: 6px; }
    .niveau .niveau > .noeud:only-child { padding-top: 0; }
    .niveau .niveau > .noeud:only-child::before, .niveau .niveau > .noeud:only-child::after { display: none; }

    .carte { position: relative; display: flex; align-items: center; gap: 10px; width: 200px; padding: 10px 12px; border: 1px solid var(--rh-line); border-radius: var(--rh-radius-md, 10px); background: var(--rh-surface); text-align: left; transition: border-color .15s, box-shadow .15s; }
    .carte:hover { border-color: var(--rh-line-strong); }
    .carte.choisie { border-color: var(--rh-accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--rh-accent) 22%, transparent); }
    .avatar { flex: none; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; background: var(--rh-surface-2); color: var(--rh-ink-2); font-size: 12px; font-weight: 600; }
    .texte { display: grid; min-width: 0; flex: 1; }
    .nom { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--rh-ink); font-weight: 500; text-decoration: none; }
    .nom:hover { text-decoration: underline; }
    .nom::after { content: ''; position: absolute; inset: 0; border-radius: inherit; } /* the whole card opens the record */
    .poste, .dept { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--rh-text-sm); }
    .poste { color: var(--rh-ink-2); }
    .dept { color: var(--rh-muted); }
    .carte:has(.equipe) { margin-bottom: 12px; padding-bottom: 18px; } /* room for the pill on the bottom edge */
    .equipe { position: absolute; z-index: 1; left: 50%; bottom: -13px; transform: translateX(-50%); display: inline-flex; align-items: center; gap: 4px; min-height: 28px; padding: 0 8px; border: 1px solid var(--rh-line); border-radius: 999px; background: var(--rh-surface-2); color: var(--rh-ink-2); font: inherit; font-size: 12px; font-variant-numeric: tabular-nums; cursor: pointer; }
    .equipe[aria-expanded='true'] { border-color: var(--rh-accent); color: var(--rh-ink); }
    .equipe:focus-visible, .nom:focus-visible { outline: 2px solid var(--rh-accent); outline-offset: 2px; }
    .equipe .pi { font-size: 10px; }

    /* Phone: the same tree as an indented list */
    @media (max-width: 720px) {
      .recherche, .resume { flex-basis: 100%; }
      .cadre { overflow: visible; }
      .niveau, .racines { display: block; width: auto; min-width: 0; }
      .noeud { display: block; padding: 0; }
      .noeud + .noeud { margin-top: 8px; }
      .niveau .niveau { margin-left: 16px; padding: 8px 0 0 14px; border-left: 1px solid var(--rh-line-strong); }
      .niveau .niveau::before, .niveau .niveau > .noeud::before, .niveau .niveau > .noeud::after { display: none; }
      .niveau .niveau > .noeud { padding-top: 0; }
      .carte { width: auto; }
      .carte:has(.equipe) { margin-bottom: 0; padding-bottom: 10px; padding-right: 64px; }
      .equipe { left: auto; right: 10px; bottom: auto; top: 50%; transform: translateY(-50%); }
    }
  `,
})
export class Organigramme {
  private readonly api = inject(RhApi);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly cadre = viewChild<ElementRef<HTMLElement>>('cadre');
  protected readonly initiales = initiales;
  protected readonly libelleRole = libelleRole;

  protected readonly etat = toSignal(
    this.api.organigramme().pipe(
      map((noeuds): Etat => ({ statut: 'ok', noeuds })),
      catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
      startWith<Etat>({ statut: 'chargement' }),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  private readonly noeuds = computed(() => { const e = this.etat(); return e.statut === 'ok' ? e.noeuds : []; });
  protected readonly arbre = computed(() => construireArbre(this.noeuds()));

  /** Open teams; null until the first load: the top of each branch then shows its direct team. */
  private readonly ouverts = signal<Set<number> | null>(null);
  private readonly ouvertsEffectifs = computed(() => this.ouverts() ?? new Set(this.arbre().map(b => b.personne.id)));
  protected readonly choisie = signal<number | null>(null);
  protected readonly recherche = signal('');
  protected readonly resultats = computed(() => chercher(this.noeuds(), this.recherche()).slice(0, MAX_RESULTATS));

  protected readonly resume = computed(() => {
    const n = this.noeuds().length;
    const managers = new Set(this.noeuds().map(p => p.managerId).filter(m => m !== null)).size;
    return `${n} ${n > 1 ? 'personnes' : 'personne'} · ${managers} ${managers > 1 ? 'équipes' : 'équipe'}`;
  });

  constructor() {
    // A tree wider than the screen opens centred on the top of the company, not on its left edge
    effect(() => {
      if (!this.cadre()) return;
      untracked(() => this.centrer());
    });
  }

  private centrer(): void {
    afterNextRender(() => {
      const c = this.cadre()?.nativeElement;
      if (c && c.scrollWidth > c.clientWidth) c.scrollLeft = (c.scrollWidth - c.clientWidth) / 2;
    }, { injector: this.injector });
  }

  protected ouvert(id: number): boolean {
    return this.ouvertsEffectifs().has(id);
  }

  protected effectif(b: Branche): string {
    return `${b.total} ${b.total > 1 ? 'personnes' : 'personne'}`;
  }

  protected basculer(id: number): void {
    const s = new Set(this.ouvertsEffectifs());
    if (s.has(id)) s.delete(id); else s.add(id);
    this.ouverts.set(s);
  }

  protected toutOuvrir(): void {
    this.ouverts.set(new Set(this.noeuds().filter(p => this.noeuds().some(q => q.managerId === p.id)).map(p => p.id)));
    this.centrer();
  }

  protected toutFermer(): void {
    this.ouverts.set(new Set());
  }

  /** Opens every team on the way to the person, marks their card and brings it into view with the keyboard on it. */
  protected montrer(id: number): void {
    const s = new Set(this.ouvertsEffectifs());
    cheminVers(this.noeuds(), id).forEach(m => s.add(m));
    this.ouverts.set(s);
    this.choisie.set(id);
    this.recherche.set('');
    afterNextRender(() => {
      const carte = this.hote.nativeElement.querySelector<HTMLElement>(`.carte[data-id="${id}"]`);
      carte?.scrollIntoView?.({ block: 'center', inline: 'center', behavior: 'smooth' });
      carte?.querySelector<HTMLElement>('a.nom')?.focus({ preventScroll: true });
    }, { injector: this.injector });
  }
}
