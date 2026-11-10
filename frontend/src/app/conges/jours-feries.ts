import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
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

      <p class="hint intro">Ces jours ne sont jamais comptés dans un congé : le calcul des jours ouvrables les retire, comme les week-ends.</p>

      @if (gestion()) {
        <form class="ajout card" (submit)="$event.preventDefault(); ajouter()" novalidate>
          <div class="champ">
            <label for="ferie-date">Date</label>
            <p-datepicker inputId="ferie-date" [ngModel]="date()" (ngModelChange)="date.set($event)" name="date" [showIcon]="true" iconDisplay="input" dateFormat="dd/mm/yy" placeholder="jj/mm/aaaa" />
          </div>
          <div class="champ libelle">
            <label for="ferie-libelle">Nom</label>
            <input pInputText id="ferie-libelle" name="libelle" maxlength="100" placeholder="ex. Fête du Travail" [ngModel]="libelle()" (ngModelChange)="libelle.set($event)">
          </div>
          <p-button type="submit" label="Ajouter" icon="pi pi-plus" [loading]="occupe() === 'ajout'" [disabled]="!!occupe()" />
          @if (erreurAjout()) { <p class="alerte erreur" role="alert">{{ erreurAjout() }}</p> }
        </form>
      }

      @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }

      @switch (etat().statut) {
        @case ('chargement') { <p class="hint" aria-busy="true">Chargement…</p> }
        @case ('erreur') { <p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
        @case ('ok') {
          <ul class="liste">
            @for (j of tries(); track j.id) {
              <li class="ferie">
                <span class="jour">
                  <b>{{ jourMois(j.date) }}</b>
                  <small>{{ versDate(j.date) | date: 'EEEE' }}</small>
                </span>
                <span class="nom">{{ j.libelle }}</span>
                @if (gestion()) {
                  <span class="actions-ligne">
                    @if (aConfirmer() === j.id) {
                      <span class="hint">Supprimer ce jour férié ?</span>
                      <p-button label="Non" severity="secondary" size="small" (onClick)="aConfirmer.set(null)" />
                      <p-button label="Confirmer" severity="danger" size="small" [loading]="occupe() === j.id" (onClick)="supprimer(j.id)" />
                    } @else {
                      <p-button label="Supprimer" severity="secondary" size="small" [text]="true" (onClick)="aConfirmer.set(j.id)" />
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
    .ferie { display: grid; grid-template-columns: 140px minmax(0, 1fr) auto; align-items: center; gap: var(--rh-space-4); min-height: var(--rh-row-height); padding: 8px var(--rh-space-2); border-bottom: 1px solid var(--rh-line-row); }
    .jour b { display: block; font-weight: 500; }
    .jour small { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .jour small::first-letter { text-transform: uppercase; }
    .nom { overflow-wrap: anywhere; }
    .actions-ligne { display: flex; align-items: center; gap: var(--rh-space-2); }
    .vide { padding: var(--rh-space-6) var(--rh-space-2); }
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
  private readonly recharge$ = new BehaviorSubject<void>(undefined);
  private readonly anneeCourante = new Date().getFullYear();
  protected readonly versDate = versDate;
  private readonly mois = new Intl.DateTimeFormat('fr-FR', { month: 'long' });
  /** "1er mai", "14 juillet": French writes the first of the month as an ordinal. */
  protected jourMois(iso: string): string {
    const d = versDate(iso);
    return `${d.getDate() === 1 ? '1er' : d.getDate()} ${this.mois.format(d)}`;
  }

  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly annee = computed(() => Number(this.params().get('annee')) || this.anneeCourante);
  protected readonly annees = computed(() => [...new Set([this.anneeCourante - 1, this.anneeCourante, this.anneeCourante + 1, this.annee()])]
    .sort((a, b) => a - b).map(a => ({ label: String(a), value: a })));

  protected readonly etat = toSignal(
    combineLatest([this.route.queryParamMap, this.recharge$]).pipe(
      switchMap(([q]) => this.api.joursFeries(Number(q.get('annee')) || this.anneeCourante).pipe(
        map((liste): Etat => ({ statut: 'ok', liste })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly tries = computed(() => {
    const e = this.etat();
    return e.statut === 'ok' ? [...e.liste].sort((a, b) => a.date.localeCompare(b.date)) : [];
  });

  readonly date = signal<Date | null>(null);
  readonly libelle = signal('');
  protected readonly occupe = signal<'ajout' | number | null>(null);
  protected readonly aConfirmer = signal<number | null>(null);
  protected readonly erreurAjout = signal('');
  protected readonly erreur = signal('');

  protected choisirAnnee(a: number): void {
    void this.router.navigate([], { queryParams: { annee: a === this.anneeCourante ? null : a } });
  }

  protected async ajouter(): Promise<void> {
    const d = this.date(), libelle = this.libelle().trim();
    this.erreurAjout.set('');
    if (!d || !libelle) {
      this.erreurAjout.set('Indiquez la date et le nom du jour férié.');
      return;
    }
    if (this.occupe()) return;
    this.occupe.set('ajout');
    try {
      await firstValueFrom(this.api.ajouterJourFerie({ date: dateIso(d), libelle }));
      this.date.set(null);
      this.libelle.set('');
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
      this.aConfirmer.set(null);
      this.recharge$.next();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(null);
    }
  }
}
