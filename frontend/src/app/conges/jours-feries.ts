import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, catchError, distinctUntilChanged, firstValueFrom, map, merge, of, startWith, switchMap, tap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { JourFerie } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { dateIso } from './regles';

type Etat = { statut: 'chargement' } | { statut: 'ok'; liste: JourFerie[] } | { statut: 'erreur'; message: string };

/** "2026-05-01" → local Date (never new Date(iso), read as UTC). */
function versDate(iso: string): Date {
  const [a, m, j] = iso.split('-').map(Number);
  return new Date(a, m - 1, j);
}

/** Same range as the API (2000–2100): outside it a holiday could be neither listed nor deleted. */
export const ANNEE_MIN = 2000;
export const ANNEE_MAX = 2100;

/** Year from the address; anything unreadable or out of range means `defaut`. */
export function lireAnnee(texte: string | null, defaut: number): number {
  if (!texte || !/^\d{4}$/.test(texte)) return defaut;
  const a = Number(texte);
  return a >= ANNEE_MIN && a <= ANNEE_MAX ? a : defaut;
}

/**
 * Public holidays of a year (excluded from working-day counts). Everyone can read them, to plan leave;
 * ADMIN and RH add and delete them. The year lives in the URL.
 */
@Component({
  selector: 'app-jours-feries',
  imports: [DatePipe, FormsModule, ButtonModule, DatePickerModule, InputTextModule, SelectModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Congés</p><h1 class="page-titre">Jours fériés</h1></div>
        <div class="actions">
          <p-select [options]="annees()" [ngModel]="annee()" (ngModelChange)="choisirAnnee($event)" ariaLabel="Année" />
        </div>
      </header>

      <p class="hint intro">
        Un jour férié n’est pas compté dans un congé : le calcul des jours ouvrables le retire, comme les week-ends.
        Ce calcul se fait au dépôt de la demande : les demandes déjà déposées ne sont pas recalculées quand la liste change.
      </p>
      <p class="annonce sr-only" aria-live="polite">{{ annonce() }}</p>

      @if (gestion()) {
        <form class="ajout card" (submit)="$event.preventDefault(); ajouter()" novalidate>
          <div class="champ">
            <label for="ferie-date">Date</label>
            <p-datepicker inputId="ferie-date" [ngModel]="date()" (ngModelChange)="date.set($event)" name="date" [showIcon]="true" iconDisplay="input"
                          dateFormat="dd/mm/yy" placeholder="jj/mm/aaaa" [minDate]="dateMin" [maxDate]="dateMax" [defaultDate]="premierJanvier()"
                          [invalid]="dateManquante()" />
          </div>
          <div class="champ libelle">
            <label for="ferie-libelle">Nom</label>
            <input pInputText id="ferie-libelle" name="libelle" maxlength="100" placeholder="ex. Fête du Travail" [ngModel]="libelle()" (ngModelChange)="libelle.set($event)"
                   [attr.aria-invalid]="libelleManquant() ? 'true' : null" [attr.aria-describedby]="erreurAjout() ? 'ferie-err' : null">
          </div>
          <p-button type="submit" label="Ajouter" icon="pi pi-plus" [loading]="occupe() === 'ajout'" [disabled]="!!occupe()" />
          @if (erreurAjout()) { <p class="alerte erreur" id="ferie-err" role="alert">{{ erreurAjout() }}</p> }
        </form>
      }

      @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }

      @switch (etat().statut) {
        @case ('chargement') { <p class="hint" aria-busy="true">Chargement…</p> }
        @case ('erreur') { <p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
        @case ('ok') {
          <ul class="liste" tabindex="-1" [attr.aria-label]="'Jours fériés ' + annee()">
            @for (j of tries(); track j.id) {
              <li class="ferie" [attr.data-id]="j.id">
                <span class="jour">
                  <b>{{ jourMois(j.date) }}</b>
                  <small>{{ versDate(j.date) | date: 'EEEE' }}</small>
                </span>
                <span class="nom">{{ j.libelle }}</span>
                @if (gestion()) {
                  <span class="actions-ligne">
                    @if (aConfirmer() === j.id) {
                      <span class="hint">Supprimer ce jour férié ?</span>
                      <p-button styleClass="non" label="Non" severity="secondary" size="small" (onClick)="annulerSuppression(j.id)" />
                      <p-button label="Confirmer" severity="danger" size="small" [loading]="occupe() === j.id" (onClick)="supprimer(j.id)" />
                    } @else {
                      <p-button styleClass="supprimer" label="Supprimer" severity="secondary" size="small" [text]="true" [ariaLabel]="'Supprimer ' + j.libelle" (onClick)="demanderSuppression(j.id)" />
                    }
                  </span>
                }
              </li>
            } @empty {
              <li class="vide hint">Aucun jour férié enregistré pour {{ annee() }}.</li>
            }
          </ul>
        }
      }
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: 760px; }
    .intro { max-width: 70ch; }
    .ajout { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--rh-space-3); padding: var(--rh-space-4) var(--rh-space-5); }
    .ajout .champ { flex: 0 1 180px; }
    .ajout .libelle { flex: 1 1 220px; }
    .ajout .libelle input { width: 100%; }
    .ajout .alerte { flex-basis: 100%; }
    .liste { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--rh-line); }
    .liste:focus { outline: none; }
    .ferie { display: grid; grid-template-columns: 140px minmax(0, 1fr) auto; align-items: center; gap: var(--rh-space-4); min-height: var(--rh-row-height); padding: 8px var(--rh-space-2); border-bottom: 1px solid var(--rh-line-row); }
    .jour b { display: block; font-weight: 500; }
    .jour small { display: block; color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .jour small::first-letter { text-transform: uppercase; }
    .nom { overflow-wrap: anywhere; }
    .actions-ligne { display: flex; align-items: center; gap: var(--rh-space-2); }
    .vide { padding: var(--rh-space-6) var(--rh-space-2); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    @media (max-width: 560px) {
      .ferie { grid-template-columns: minmax(0, 1fr) auto; row-gap: 2px; padding-block: 10px; }
      .jour { display: flex; align-items: baseline; gap: var(--rh-space-2); }
      .nom { grid-column: 1; grid-row: 2; color: var(--rh-ink-2); }
      .actions-ligne { grid-column: 2; grid-row: 1 / span 2; }
    }
  `,
})
export class JoursFeries {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly recharge$ = new Subject<void>();
  private readonly anneeCourante = new Date().getFullYear();
  protected readonly versDate = versDate;
  protected readonly dateMin = new Date(ANNEE_MIN, 0, 1);
  protected readonly dateMax = new Date(ANNEE_MAX, 11, 31);
  private readonly mois = new Intl.DateTimeFormat('fr-FR', { month: 'long' });

  /** "1er mai", "14 juillet": French writes the first of the month as an ordinal. */
  protected jourMois(iso: string): string {
    const d = versDate(iso);
    return `${d.getDate() === 1 ? '1er' : d.getDate()} ${this.mois.format(d)}`;
  }

  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly annee = computed(() => lireAnnee(this.params().get('annee'), this.anneeCourante));
  /** The date picker opens on the year shown. */
  protected readonly premierJanvier = computed(() => new Date(this.annee(), 0, 1));
  protected readonly annees = computed(() => [...new Set([this.anneeCourante - 1, this.anneeCourante, this.anneeCourante + 1, this.annee()])]
    .sort((a, b) => a - b).map(a => ({ label: String(a), value: a })));

  readonly date = signal<Date | null>(null);
  readonly libelle = signal('');
  protected readonly occupe = signal<'ajout' | number | null>(null);
  protected readonly aConfirmer = signal<number | null>(null);
  protected readonly erreurAjout = signal('');
  protected readonly erreur = signal('');
  protected readonly annonce = signal('');
  private readonly tente = signal(false);
  protected readonly dateManquante = computed(() => this.tente() && !this.date());
  protected readonly libelleManquant = computed(() => this.tente() && !this.libelle().trim());

  /**
   * A new year shows "Chargement…"; a reload after an add or a delete keeps the list on screen, so the
   * focused row is not destroyed. switchMap: an older answer never replaces a newer one.
   */
  protected readonly etat = toSignal(
    merge(
      this.route.queryParamMap.pipe(
        map(q => lireAnnee(q.get('annee'), this.anneeCourante)),
        distinctUntilChanged(),
        tap(() => { this.aConfirmer.set(null); this.erreur.set(''); }),
        map(annee => ({ annee, garder: false })),
      ),
      this.recharge$.pipe(map(() => ({ annee: this.annee(), garder: true }))),
    ).pipe(
      switchMap(({ annee, garder }) => {
        const charge: Observable<Etat> = this.api.joursFeries(annee).pipe(
          map((liste): Etat => ({ statut: 'ok', liste })),
          catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        );
        return garder ? charge : charge.pipe(startWith<Etat>({ statut: 'chargement' }));
      }),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly tries = computed(() => {
    const e = this.etat();
    return e.statut === 'ok' ? [...e.liste].sort((a, b) => a.date.localeCompare(b.date)) : [];
  });

  protected choisirAnnee(a: number): void {
    void this.router.navigate([], { queryParams: { annee: a === this.anneeCourante ? null : a } });
  }

  /** The "Supprimer" button disappears: the keyboard goes to "Non", the safe choice. */
  protected demanderSuppression(id: number): void {
    this.aConfirmer.set(id);
    this.focus(`li[data-id="${id}"] .non`);
  }

  protected annulerSuppression(id: number): void {
    this.aConfirmer.set(null);
    this.focus(`li[data-id="${id}"] .supprimer`);
  }

  protected async ajouter(): Promise<void> {
    const d = this.date(), libelle = this.libelle().trim();
    this.erreurAjout.set('');
    this.tente.set(true);
    if (!d || !libelle) {
      this.erreurAjout.set('Indiquez la date et le nom du jour férié.');
      this.focus(!d ? '#ferie-date' : '#ferie-libelle');
      return;
    }
    if (this.occupe()) return;
    this.occupe.set('ajout');
    try {
      await firstValueFrom(this.api.ajouterJourFerie({ date: dateIso(d), libelle }));
      this.date.set(null);
      this.libelle.set('');
      this.tente.set(false);
      this.annonce.set(`Jour férié ajouté : ${libelle}.`);
      if (d.getFullYear() !== this.annee()) this.choisirAnnee(d.getFullYear());
      else this.recharge$.next();
    } catch (e) {
      this.erreurAjout.set(messageErreur(e));
    } finally {
      this.occupe.set(null);
    }
  }

  protected async supprimer(id: number): Promise<void> {
    if (this.occupe()) return;
    this.occupe.set(id);
    this.erreur.set('');
    try {
      await firstValueFrom(this.api.supprimerJourFerie(id));
      this.annonce.set('Jour férié supprimé.');
      this.apresSuppression();
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) {
        // Someone else deleted it meanwhile: the list catches up
        this.annonce.set('Ce jour férié avait déjà été supprimé.');
        this.apresSuppression();
      } else {
        this.erreur.set(messageErreur(e));
      }
    } finally {
      this.occupe.set(null);
    }
  }

  private apresSuppression(): void {
    this.aConfirmer.set(null);
    this.recharge$.next();
    this.focus('ul.liste');
  }

  /** Focus after the next render (the element may only just appear). For a p-button, its inner <button>. */
  private focus(selecteur: string): void {
    afterNextRender(() => {
      const cible = this.hote.nativeElement.querySelector<HTMLElement>(selecteur);
      (cible?.matches('button, input, ul') ? cible : cible?.querySelector<HTMLElement>('button, input'))?.focus();
    }, { injector: this.injector });
  }
}
