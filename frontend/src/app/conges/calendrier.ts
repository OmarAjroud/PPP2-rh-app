import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, distinctUntilChanged, expand, forkJoin, map, of, reduce, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { Departement, DemandeConge, PageReponse, StatutDemande } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { Barre, LigneCalendrier, Mois, construireMois } from './calendrier-regles';

type Etat = { statut: 'chargement' } | { statut: 'ok'; mois: Mois } | { statut: 'erreur'; message: string };

const deux = (n: number) => String(n).padStart(2, '0');
const moisLong = new Intl.DateTimeFormat('fr-FR', { month: 'long' });
const moisCourt = new Intl.DateTimeFormat('fr-FR', { month: 'short' });
/** Two letters: "M" alone would not tell mardi from mercredi. */
const JOURS = ['di', 'lu', 'ma', 'me', 'je', 've', 'sa'];
const versDate = (iso: string) => new Date(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10));

/** "2026-12" from the address; anything else means the current month. */
export function lireMois(texte: string | null, aujourdhui = new Date()): { annee: number; mois: number } {
  const m = texte?.match(/^(\d{4})-(0[1-9]|1[0-2])$/);
  if (m && +m[1] >= 2000 && +m[1] <= 2100) return { annee: +m[1], mois: +m[2] };
  return { annee: aujourdhui.getFullYear(), mois: aujourdhui.getMonth() + 1 };
}

/** "du 14 au 18 décembre", "du 25 novembre au 3 décembre", "le 15 décembre" (years added when they differ). */
export function periodeLongue(debut: string, fin: string): string {
  const d = versDate(debut), f = versDate(fin);
  const jour = (x: Date) => (x.getDate() === 1 ? '1er' : String(x.getDate()));
  if (debut === fin) return `le ${jour(d)} ${moisLong.format(d)}`;
  if (d.getFullYear() !== f.getFullYear()) return `du ${jour(d)} ${moisLong.format(d)} ${d.getFullYear()} au ${jour(f)} ${moisLong.format(f)} ${f.getFullYear()}`;
  if (d.getMonth() === f.getMonth()) return `du ${jour(d)} au ${jour(f)} ${moisLong.format(f)}`;
  return `du ${jour(d)} ${moisLong.format(d)} au ${jour(f)} ${moisLong.format(f)}`;
}

/** "14 – 18 déc.", "25 nov. – 3 déc.", "15 déc.". */
export function periodeCourte(debut: string, fin: string): string {
  const d = versDate(debut), f = versDate(fin);
  if (debut === fin) return `${d.getDate()} ${moisCourt.format(d)}`;
  if (d.getMonth() === f.getMonth() && d.getFullYear() === f.getFullYear()) return `${d.getDate()} – ${f.getDate()} ${moisCourt.format(f)}`;
  return `${d.getDate()} ${moisCourt.format(d)} – ${f.getDate()} ${moisCourt.format(f)}`;
}

const STATUTS: Partial<Record<StatutDemande, string>> = { APPROUVEE: 'approuvé', EN_ATTENTE: 'en attente' };

/** Every page of a paginated list (100 per page, the API maximum), as one array. */
function toutesLesPages(page: (n: number) => Observable<PageReponse<DemandeConge>>): Observable<DemandeConge[]> {
  return page(0).pipe(
    expand(p => (p.dernierePage ? EMPTY : page(p.numeroPage + 1))),
    reduce((tout, p) => tout.concat(p.contenu), [] as DemandeConge[]),
  );
}

/**
 * Who is off, month by month: one row per person, approved leave as solid bars, pending as outlined bars.
 * Manager: their direct team. ADMIN and RH: everyone, with a department filter. A list view says the same
 * thing in text (and is the default on a phone). Month and department live in the URL.
 */
@Component({
  selector: 'app-calendrier',
  imports: [FormsModule, RouterLink, ButtonModule, SelectModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Congés</p><h1 class="page-titre">Calendrier des absences</h1></div>
        <div class="actions">
          @if (gestion()) {
            <p-select [options]="optionsDepartements()" [ngModel]="departementId()" (ngModelChange)="choisirDepartement($event)"
                      ariaLabel="Filtrer par département" placeholder="Tous les départements" [showClear]="true" />
          }
        </div>
      </header>

      <div class="barre-outils">
        <div class="navigation">
          <p-button icon="pi pi-chevron-left" severity="secondary" [text]="true" ariaLabel="Mois précédent" (onClick)="decaler(-1)" />
          <h2 class="titre-mois" aria-live="polite">{{ titreMois() }}</h2>
          <p-button icon="pi pi-chevron-right" severity="secondary" [text]="true" ariaLabel="Mois suivant" (onClick)="decaler(1)" />
          <p-button label="Aujourd’hui" severity="secondary" size="small" [disabled]="estMoisCourant()" (onClick)="allerAujourdhui()" />
        </div>
        <div class="vues" role="group" aria-label="Affichage">
          <button type="button" class="vue-grille" [attr.aria-pressed]="vue() === 'grille'" (click)="vue.set('grille')"><i class="pi pi-th-large" aria-hidden="true"></i> Grille</button>
          <button type="button" class="vue-liste" [attr.aria-pressed]="vue() === 'liste'" (click)="vue.set('liste')"><i class="pi pi-list" aria-hidden="true"></i> Liste</button>
        </div>
      </div>

      @switch (etat().statut) {
        @case ('chargement') { <p class="hint" aria-busy="true">Chargement…</p> }
        @case ('erreur') { <p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
        @case ('ok') {
          @let m = $any(etat()).mois;
          @if (!m.lignes.length) {
            <p class="vide card">Personne n’est absent en {{ titreMois() }}.</p>
          } @else if (vue() === 'grille') {
            <div class="grille-cadre card">
              <div class="grille" [style.--jours]="m.jours.length">
                <div class="rangee tete" aria-hidden="true">
                  <span class="cellule-nom"></span>
                  @for (j of m.jours; track j.jour) {
                    <span class="jour" [class.repos]="j.weekend || j.ferie" [class.ferie]="j.ferie" [class.aujourdhui]="j.iso === aujourdhuiIso">
                      <small>{{ lettre(j.iso) }}</small>{{ j.jour }}
                    </span>
                  }
                </div>
                @for (l of m.lignes; track l.employeId) {
                  <div class="rangee">
                    <span class="cellule-nom"><span class="personne">{{ l.nom }}</span>@if (gestion()) {<small class="dept">{{ l.departement }}</small>}</span>
                    @for (j of m.jours; track j.jour) {
                      <span class="fond" aria-hidden="true" [style.grid-column]="j.jour + 1" [class.repos]="j.weekend || j.ferie" [class.aujourdhui]="j.iso === aujourdhuiIso"></span>
                    }
                    @for (b of l.barres; track b.id) {
                      <a class="barre" [routerLink]="['/leave-requests', b.id]" [class.en-attente]="b.statut === 'EN_ATTENTE'"
                         [class.coupe-avant]="b.coupeAvant" [class.coupe-apres]="b.coupeApres"
                         [style.grid-column]="(b.debut + 1) + ' / ' + (b.fin + 2)"
                         [attr.aria-label]="libelleBarre(l, b)" [title]="libelleBarre(l, b)">@if (b.fin - b.debut >= 2) {<span>{{ b.type }}</span>}</a>
                    }
                  </div>
                }
                <div class="rangee pied" aria-hidden="true">
                  <span class="cellule-nom">Absents</span>
                  @for (j of m.jours; track j.jour) { <span class="compte" [class.repos]="j.weekend || j.ferie">{{ j.absents || '' }}</span> }
                </div>
              </div>
            </div>
            <p class="legende hint">
              <span><i class="pastille approuve" aria-hidden="true"></i> Approuvé</span>
              <span><i class="pastille attente" aria-hidden="true"></i> En attente</span>
              <span><i class="pastille repos" aria-hidden="true"></i> Week-end ou jour férié</span>
            </p>
          } @else {
            <ul class="liste-absences card">
              @for (a of absences(); track a.barre.id) {
                <li>
                  <a [routerLink]="['/leave-requests', a.barre.id]">
                    <span class="qui"><b>{{ a.ligne.nom }}</b>@if (gestion()) {<small>{{ a.ligne.departement }}</small>}</span>
                    <span class="quand">{{ courte(a.barre.dateDebut, a.barre.dateFin) }}</span>
                    <span class="quoi">{{ a.barre.type }}</span>
                    <span class="status" [class.s-ok]="a.barre.statut === 'APPROUVEE'" [class.s-wait]="a.barre.statut === 'EN_ATTENTE'">{{ a.barre.statut === 'APPROUVEE' ? 'Approuvé' : 'En attente' }}</span>
                  </a>
                </li>
              }
            </ul>
          }
        }
      }
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); }
    .barre-outils { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: var(--rh-space-3); }
    .navigation { display: flex; align-items: center; gap: var(--rh-space-1); }
    .titre-mois { margin: 0; min-width: 11ch; text-align: center; font-size: var(--rh-text-lg); font-weight: 600; }
    .titre-mois::first-letter { text-transform: uppercase; }
    .vues { display: inline-flex; padding: 2px; border: 1px solid var(--rh-line); border-radius: var(--rh-radius-sm); background: var(--rh-surface-2); }
    .vues button { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; padding: 0 12px; border: 0; border-radius: calc(var(--rh-radius-sm) - 2px); background: none; color: var(--rh-muted); font: inherit; font-size: var(--rh-text-sm); cursor: pointer; }
    .vues button[aria-pressed='true'] { background: var(--rh-surface); color: var(--rh-ink); box-shadow: 0 0 0 1px var(--rh-line); }
    .vide { padding: var(--rh-space-6) var(--rh-space-5); color: var(--rh-muted); text-align: center; }

    .grille-cadre { overflow-x: auto; padding: 0; }
    .grille { --nom: 170px; min-width: calc(var(--nom) + var(--jours) * 24px); }
    .rangee { display: grid; grid-template-columns: var(--nom) repeat(var(--jours), minmax(24px, 1fr)); align-items: center; min-height: 44px; border-bottom: 1px solid var(--rh-line-row); }
    .rangee > * { grid-row: 1; }
    .cellule-nom { grid-column: 1; position: sticky; left: 0; z-index: 2; display: grid; align-self: stretch; align-content: center; padding: 6px var(--rh-space-4); background: var(--rh-surface); border-right: 1px solid var(--rh-line); min-width: 0; }
    .personne { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
    .dept { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--rh-muted); font-size: var(--rh-text-xs, 12px); }
    .tete { min-height: 48px; }
    .tete .jour { display: grid; justify-items: center; align-self: stretch; align-content: center; font-size: var(--rh-text-sm); font-variant-numeric: tabular-nums; }
    .tete .jour small { color: var(--rh-muted); font-size: 11px; }
    .tete .jour.ferie { color: var(--rh-muted); }
    .tete .jour.aujourdhui { color: var(--rh-accent); font-weight: 600; }
    .fond { align-self: stretch; }
    /* Hatched: a flat surface-2 tint barely differs from the surface, in dark mode especially */
    .repos { background: repeating-linear-gradient(135deg, var(--rh-line-row) 0 1px, transparent 1px 6px), var(--rh-surface-2); }
    .fond.aujourdhui { box-shadow: inset 1px 0 0 var(--rh-accent), inset -1px 0 0 var(--rh-accent); }
    .barre { z-index: 1; min-width: 0; height: 26px; margin: 0 2px; padding: 0 8px; display: flex; align-items: center; overflow: hidden; white-space: nowrap; border-radius: 6px; background: var(--rh-accent); color: var(--rh-accent-ink); font-size: 12px; font-weight: 500; text-decoration: none; }
    .barre span { overflow: hidden; text-overflow: ellipsis; }
    .barre.en-attente { background: var(--rh-surface); color: var(--rh-ink); box-shadow: inset 0 0 0 1.5px var(--rh-accent); background-image: repeating-linear-gradient(135deg, transparent 0 6px, color-mix(in srgb, var(--rh-accent) 14%, transparent) 6px 12px); }
    .barre.coupe-avant { border-top-left-radius: 0; border-bottom-left-radius: 0; margin-left: 0; }
    .barre.coupe-apres { border-top-right-radius: 0; border-bottom-right-radius: 0; margin-right: 0; }
    .barre:hover { filter: brightness(1.08); }
    .barre:focus-visible { outline: 2px solid var(--rh-ink); outline-offset: 2px; }
    .pied { min-height: 36px; border-bottom: 0; }
    .pied .cellule-nom { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .compte { align-self: stretch; display: grid; place-items: center; color: var(--rh-muted); font-size: var(--rh-text-sm); font-variant-numeric: tabular-nums; }

    .legende { display: flex; flex-wrap: wrap; gap: var(--rh-space-4); margin: 0; }
    .legende span { display: inline-flex; align-items: center; gap: 6px; }
    .pastille { display: inline-block; width: 16px; height: 10px; border-radius: 3px; }
    .pastille.approuve { background: var(--rh-accent); }
    .pastille.attente { box-shadow: inset 0 0 0 1.5px var(--rh-accent); }
    .pastille.repos { background: repeating-linear-gradient(135deg, var(--rh-line-row) 0 1px, transparent 1px 4px), var(--rh-surface-2); box-shadow: inset 0 0 0 1px var(--rh-line); }

    .liste-absences { list-style: none; margin: 0; padding: 0; }
    .liste-absences li + li { border-top: 1px solid var(--rh-line-row); }
    .liste-absences a { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1fr) auto; align-items: center; gap: var(--rh-space-4); padding: 10px var(--rh-space-5); color: inherit; text-decoration: none; }
    .liste-absences a:hover { background: var(--rh-surface-2); }
    .qui { display: grid; min-width: 0; }
    .qui b { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .qui small { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .quand { font-variant-numeric: tabular-nums; }
    .quoi { color: var(--rh-ink-2); }
    @media (max-width: 640px) {
      .grille { --nom: 130px; }
      .liste-absences a { grid-template-columns: minmax(0, 1fr) auto; row-gap: 2px; padding-inline: var(--rh-space-4); }
      .quand { grid-column: 1; grid-row: 2; color: var(--rh-muted); }
      .quoi { grid-column: 1; grid-row: 3; font-size: var(--rh-text-sm); }
      .status { grid-column: 2; grid-row: 1 / span 3; }
    }
  `,
})
export class Calendrier {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly periode = computed(() => lireMois(this.params().get('mois')));
  protected readonly departementId = computed(() => Number(this.params().get('departement')) || null);
  protected readonly titreMois = computed(() => { const p = this.periode(); return `${moisLong.format(new Date(p.annee, p.mois - 1, 1))} ${p.annee}`; });
  protected readonly estMoisCourant = computed(() => { const p = this.periode(), n = new Date(); return p.annee === n.getFullYear() && p.mois === n.getMonth() + 1; });
  protected readonly aujourdhuiIso = (() => { const n = new Date(); return `${n.getFullYear()}-${deux(n.getMonth() + 1)}-${deux(n.getDate())}`; })();

  /** Grid on a large screen, list on a phone (the grid scrolls sideways there). */
  protected readonly vue = signal<'grille' | 'liste'>(typeof matchMedia === 'function' && matchMedia('(max-width: 640px)').matches ? 'liste' : 'grille');

  private readonly departements = toSignal(
    this.gestion() ? this.api.departements().pipe(catchError(() => of<Departement[]>([]))) : of<Departement[]>([]),
    { initialValue: [] as Departement[] },
  );
  protected readonly optionsDepartements = computed(() => this.departements().map(d => ({ label: d.nom, value: d.id })));

  protected readonly etat = toSignal(
    this.route.queryParamMap.pipe(
      map(q => ({ ...lireMois(q.get('mois')), departementId: Number(q.get('departement')) || null })),
      distinctUntilChanged((a, b) => a.annee === b.annee && a.mois === b.mois && a.departementId === b.departementId),
      switchMap(({ annee, mois, departementId }) => forkJoin([
        this.demandes(annee, mois, departementId),
        this.api.joursFeries(annee).pipe(catchError(() => of([]))),
      ]).pipe(
        map(([demandes, feries]): Etat => ({ statut: 'ok', mois: construireMois(demandes, annee, mois, feries.map(f => f.date)) })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );

  /** List view: every leave of the month, by start date. */
  protected readonly absences = computed(() => {
    const e = this.etat();
    if (e.statut !== 'ok') return [];
    return e.mois.lignes.flatMap(ligne => ligne.barres.map(barre => ({ ligne, barre })))
      .sort((a, b) => a.barre.dateDebut.localeCompare(b.barre.dateDebut) || a.ligne.nom.localeCompare(b.ligne.nom, 'fr'));
  });

  /**
   * ADMIN, RH: every request overlapping the month (optionally one department).
   * Manager: their team's requests starting that year (the API refuses a request spanning two years, so none is missed).
   */
  private demandes(annee: number, mois: number, departementId: number | null): Observable<DemandeConge[]> {
    if (this.gestion()) {
      const du = `${annee}-${deux(mois)}-01`;
      const au = `${annee}-${deux(mois)}-${deux(new Date(annee, mois, 0).getDate())}`;
      return toutesLesPages(page => this.api.demandesPeriode({ du, au, departementId, page }));
    }
    return toutesLesPages(page => this.api.demandesEquipeAnnee(annee, page));
  }

  protected lettre(iso: string): string {
    return JOURS[versDate(iso).getDay()];
  }

  protected libelleBarre(l: LigneCalendrier, b: Barre): string {
    return `${l.nom}, ${b.type}, ${periodeLongue(b.dateDebut, b.dateFin)}, ${STATUTS[b.statut] ?? b.statut}`;
  }

  protected courte(debut: string, fin: string): string {
    return periodeCourte(debut, fin);
  }

  protected decaler(n: number): void {
    const p = this.periode();
    const d = new Date(p.annee, p.mois - 1 + n, 1);
    this.allerA(`${d.getFullYear()}-${deux(d.getMonth() + 1)}`);
  }

  protected allerAujourdhui(): void {
    this.allerA(null);
  }

  protected choisirDepartement(id: number | null): void {
    void this.router.navigate([], { queryParams: { departement: id ?? null }, queryParamsHandling: 'merge' });
  }

  private allerA(mois: string | null): void {
    void this.router.navigate([], { queryParams: { mois }, queryParamsHandling: 'merge' });
  }
}
