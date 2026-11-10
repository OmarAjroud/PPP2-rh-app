import { Component, ElementRef, Injector, afterNextRender, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, firstValueFrom, map, of, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { MouvementSolde, TypeConge, TypeMouvement } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';

const LIBELLES: Record<TypeMouvement, string> = {
  ALLOCATION: 'Allocation',
  DEDUCTION: 'Congé validé',
  REMBOURSEMENT: 'Jours rendus',
  AJUSTEMENT: 'Ajustement',
};

const nombre = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });

/** "2,5" / "−4" with a true minus sign (same sign everywhere in the history). */
function valeur(v: number): string {
  return v < 0 ? `−${nombre.format(-v)}` : nombre.format(v);
}

/** "+2,5" / "−4" / "0". */
export function signe(v: number): string {
  return v > 0 ? `+${nombre.format(v)}` : valeur(v);
}

/** "1,5", "-2", "−2", "– 2", "+3" or ",5" typed by a person → number; null if not a valid amount (1 decimal, 3 digits, not zero). */
export function lireJours(texte: string): number | null {
  let t = texte.replace(/\s+/g, '').replace(',', '.').replace(/^[−–‑]/, '-').replace(/^\+/, '');
  t = t.replace(/^(-?)\./, '$10.');
  if (!/^-?\d{1,3}(\.\d)?$/.test(t)) return null;
  const v = Number(t);
  return v === 0 ? null : v;
}

type Etat = 'chargement' | 'ok' | 'erreur' | 'masque';

/**
 * Balance history for a year (every allocation, approved leave, refund and HR adjustment, with the
 * balance after it), mine or an employee's. Hidden when the reader may not see it (404/403).
 * With `ajustable` (ADMIN, RH on someone else's record), a form adds or removes days with a reason;
 * `modifie` tells the page to reload the balances.
 */
@Component({
  selector: 'app-historique-soldes',
  imports: [DatePipe, RouterLink, ButtonModule],
  template: `
    @if (etat() !== 'masque') {
      <section class="card" aria-labelledby="historique-titre">
        <div class="card-h">
          <h2 id="historique-titre">Historique du solde</h2>
          <div class="outils">
            <label class="sr-only" for="historique-annee">Année</label>
            <select id="historique-annee" (change)="annee.set(+$any($event.target).value)">
              @for (a of annees; track a) { <option [value]="a" [selected]="a === annee()">{{ a }}</option> }
            </select>
            @if (ajustable() && !formOuvert()) {
              <span #ouvreur><p-button label="Ajuster le solde" severity="secondary" size="small" (onClick)="ouvrir()" /></span>
            }
          </div>
        </div>
        <p class="annonce sr-only" aria-live="polite">{{ annonce() }}</p>

        @if (formOuvert()) {
          <form class="ajustement card-b" (submit)="$event.preventDefault(); ajuster()" novalidate>
            @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
            @if (!typesSolde().length) { <p class="alerte erreur" role="alert">Les types de congé n’ont pas pu être chargés : réessayez plus tard.</p> }
            <div class="champs">
              <div class="champ">
                <label for="ajust-type">Type de congé</label>
                <select id="ajust-type" (change)="typeId.set(+$any($event.target).value)">
                  @for (t of typesSolde(); track t.id) { <option [value]="t.id" [selected]="t.id === typeId()">{{ t.libelle }}</option> }
                </select>
              </div>
              <div class="champ">
                <label for="ajust-jours">Jours</label>
                <input #jours id="ajust-jours" class="p-inputtext" inputmode="decimal" placeholder="ex. 2 ou -1,5"
                       [value]="joursTexte()" (input)="joursTexte.set($any($event.target).value)"
                       [attr.aria-invalid]="joursInvalide() ? 'true' : null" [attr.aria-describedby]="joursInvalide() ? 'ajust-jours-err' : 'ajust-jours-aide'">
                @if (joursInvalide()) { <span class="err" id="ajust-jours-err">Indiquez un nombre de jours, positif pour ajouter, négatif pour retirer (ex. 2 ou -1,5).</span> }
                @else { <span class="aide" id="ajust-jours-aide">Positif pour ajouter, négatif pour retirer. Année {{ annee() }}.</span> }
              </div>
              <div class="champ large">
                <label for="ajust-motif">Motif</label>
                <input id="ajust-motif" class="p-inputtext" maxlength="255" placeholder="ex. Report de l’année précédente"
                       [value]="motif()" (input)="motif.set($any($event.target).value)"
                       [attr.aria-invalid]="motifInvalide() ? 'true' : null" [attr.aria-describedby]="motifInvalide() ? 'ajust-motif-err' : null">
                @if (motifInvalide()) { <span class="err" id="ajust-motif-err">Indiquez le motif : il apparaît dans l’historique.</span> }
              </div>
            </div>
            <div class="actions">
              <p-button label="Annuler" severity="secondary" (onClick)="fermer()" />
              <p-button type="submit" label="Enregistrer l’ajustement" [loading]="occupe()" [disabled]="!typesSolde().length" />
            </div>
          </form>
        }

        @switch (etat()) {
          @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
          @case ('erreur') { <p class="vide alerte erreur" role="alert">L’historique n’a pas pu être chargé.</p> }
          @default {
            <ul class="liste">
              @for (m of tries(); track m.id) {
                <li class="mouvement">
                  <span class="quoi">
                    <b>{{ libelle(m.typeMouvement) }}</b>
                    <small>
                      {{ m.dateMouvement | date: 'd MMM y' }} · {{ libelleType(m.typeCongeCode) }}
                      @if (m.demandeId) { · <a [routerLink]="['/leave-requests', m.demandeId]">voir la demande</a> }
                      @if (m.commentaire) { · {{ m.commentaire }} }
                      @if (m.creeParNomComplet) { · par {{ m.creeParNomComplet }} }
                    </small>
                  </span>
                  <span class="chiffres">
                    <span class="lecture" [class.plus]="m.jours > 0">{{ signe(m.jours) }}</span>
                    <small class="apres">solde {{ valeur(m.soldeApres) }}</small>
                  </span>
                </li>
              } @empty {
                <li class="vide hint">Aucun mouvement en {{ annee() }}.</li>
              }
            </ul>
          }
        }
      </section>
    }
  `,
  styles: `
    .card-h h2 { font-size: inherit; font-weight: inherit; margin: 0; }
    .outils { display: flex; align-items: center; gap: var(--rh-space-2); }
    select { font-size: var(--rh-text-sm) !important; font-weight: 400 !important; height: 32px; padding: 0 8px; border: 1px solid var(--rh-line-strong); border-radius: var(--rh-radius-sm); background: var(--rh-surface-2); color: var(--rh-ink); font-family: inherit; }
    .champ select { height: var(--rh-control-height); width: 100%; font-size: var(--rh-text-md) !important; }
    .ajustement { border-bottom: 1px solid var(--rh-line); }
    .champs { display: grid; grid-template-columns: minmax(0, 1fr) 140px; gap: var(--rh-space-3); }
    .champ.large { grid-column: 1 / -1; }
    .champ input { width: 100%; }
    .actions { display: flex; justify-content: flex-end; gap: var(--rh-space-2); }
    .liste { list-style: none; margin: 0; padding: 0; }
    .mouvement { display: flex; justify-content: space-between; align-items: center; gap: var(--rh-space-4); padding: 10px var(--rh-space-5); border-bottom: 1px solid var(--rh-line-row); }
    .mouvement:last-child { border-bottom: 0; }
    .quoi { min-width: 0; }
    .quoi b { display: block; font-weight: 500; }
    .quoi small { display: block; color: var(--rh-muted); font-size: var(--rh-text-sm); overflow-wrap: anywhere; }
    .chiffres { display: grid; justify-items: end; flex: none; }
    /* Additions in green; deductions keep the ink colour (a deduction is normal, not an error) */
    .lecture.plus { color: var(--rh-ok); }
    .apres { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .vide { padding: var(--rh-space-4) var(--rh-space-5); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    @media (max-width: 480px) { .champs { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class HistoriqueSoldes {
  private readonly api = inject(RhApi);
  private readonly injector = inject(Injector);
  private readonly ouvreur = viewChild<ElementRef<HTMLElement>>('ouvreur');
  private readonly champJours = viewChild<ElementRef<HTMLInputElement>>('jours');

  /** null: the signed-in person's own history. */
  readonly employeId = input<number | null>(null);
  /** Shows the adjustment form (ADMIN, RH on someone else's record; the API refuses one's own). */
  readonly ajustable = input(false);
  /** A balance changed: the page reloads its balances. */
  readonly modifie = output<void>();

  private readonly anneeCourante = new Date().getFullYear();
  protected readonly annees = [this.anneeCourante, this.anneeCourante - 1];
  protected readonly annee = signal(this.anneeCourante);

  protected readonly types = toSignal(this.api.typesConge().pipe(catchError(() => of<TypeConge[]>([]))), { initialValue: [] as TypeConge[] });
  /** Only types deducted from a balance can be adjusted (the API refuses the others). */
  protected readonly typesSolde = computed(() => this.types().filter(t => t.deduitDuSolde));
  private readonly mouvements = signal<MouvementSolde[]>([]);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly tries = computed(() => [...this.mouvements()].sort((a, b) => b.dateMouvement.localeCompare(a.dateMouvement) || b.id - a.id));

  protected readonly formOuvert = signal(false);
  protected readonly typeId = signal<number | null>(null);
  protected readonly joursTexte = signal('');
  protected readonly motif = signal('');
  protected readonly tente = signal(false);
  protected readonly occupe = signal(false);
  protected readonly erreur = signal('');
  protected readonly annonce = signal('');
  protected readonly joursInvalide = computed(() => this.tente() && lireJours(this.joursTexte()) === null);
  protected readonly motifInvalide = computed(() => this.tente() && !this.motif().trim());

  protected readonly signe = signe;
  protected readonly valeur = valeur;

  /** Each (employee, year) asks once; a newer question cancels the previous one (switchMap). */
  private readonly demandes$ = new Subject<{ id: number | null; annee: number }>();

  constructor() {
    this.demandes$.pipe(
      switchMap(({ id, annee }) => {
        this.etat.set('chargement');
        return this.api.mouvementsSolde(id, annee).pipe(
          map(m => ({ m, etat: 'ok' as Etat })),
          catchError(e => of({ m: [] as MouvementSolde[], etat: (e instanceof HttpErrorResponse && (e.status === 404 || e.status === 403) ? 'masque' : 'erreur') as Etat })),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(({ m, etat }) => { this.mouvements.set(m); this.etat.set(etat); });

    effect(() => {
      const id = this.employeId(), annee = this.annee();
      untracked(() => this.demandes$.next({ id, annee }));
    });
  }

  protected libelle(t: TypeMouvement): string {
    return LIBELLES[t] ?? t;
  }

  protected libelleType(code: string): string {
    return this.types().find(t => t.code === code)?.libelle ?? code;
  }

  protected ouvrir(): void {
    this.typeId.set(this.typesSolde()[0]?.id ?? null);
    this.joursTexte.set('');
    this.motif.set('');
    this.tente.set(false);
    this.erreur.set('');
    this.annonce.set('');
    this.formOuvert.set(true);
    afterNextRender(() => this.champJours()?.nativeElement.focus(), { injector: this.injector });
  }

  protected fermer(): void {
    this.formOuvert.set(false);
    this.focusOuvreur();
  }

  protected async ajuster(): Promise<void> {
    const id = this.employeId();
    this.tente.set(true);
    const jours = lireJours(this.joursTexte());
    const motif = this.motif().trim();
    const typeCongeId = this.typeId();
    if (id === null || jours === null || !motif || typeCongeId === null || this.occupe()) return;
    this.occupe.set(true);
    this.erreur.set('');
    try {
      await firstValueFrom(this.api.ajusterSolde(id, { typeCongeId, annee: this.annee(), jours, commentaire: motif }));
      this.formOuvert.set(false);
      this.annonce.set(`Ajustement enregistré : ${signe(jours)} jour${Math.abs(jours) > 1 ? 's' : ''}.`);
      this.demandes$.next({ id, annee: this.annee() });
      this.modifie.emit();
      this.focusOuvreur();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  /** The button that opened the form comes back: focus returns to it. */
  private focusOuvreur(): void {
    afterNextRender(() => this.ouvreur()?.nativeElement.querySelector('button')?.focus(), { injector: this.injector });
  }
}
