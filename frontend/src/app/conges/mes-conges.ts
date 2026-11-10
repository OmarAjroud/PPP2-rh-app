import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { DemandeConge, PageReponse, StatutDemande } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { jours, periode, statutDemande } from '../core/api/libelles';
import { Apparition } from '../core/motion/motion';
import { SoldesPanel } from './soldes-panel';
import { HistoriqueSoldes } from './historique-soldes';
import { dateIso, peutAnnuler } from './regles';

type Etat = { statut: 'chargement' } | { statut: 'ok'; page: PageReponse<DemandeConge> } | { statut: 'erreur'; message: string };

export const FILTRES_STATUT: { label: string; value: StatutDemande | null }[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'En attente', value: 'EN_ATTENTE' },
  { label: 'Approuvées', value: 'APPROUVEE' },
  { label: 'Refusées', value: 'REFUSEE' },
  { label: 'Annulées', value: 'ANNULEE' },
];

/** My leave: balances of the year and my requests (filter and page live in the URL). */
@Component({
  selector: 'app-mes-conges',
  imports: [RouterLink, FormsModule, ButtonModule, PaginatorModule, SelectModule, Apparition, SoldesPanel, HistoriqueSoldes],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Congés</p><h1 class="page-titre">Mes congés</h1></div>
        <a pButton routerLink="/leave-requests/new" icon="pi pi-plus">Nouvelle demande</a>
      </header>

      <div class="grille">
        <section class="liste" aria-label="Mes demandes">
          <div class="filtres">
            <p-select [options]="filtres" [ngModel]="statut()" (ngModelChange)="filtrer($event)" ariaLabel="Filtrer par statut" placeholder="Tous les statuts" />
            @if (etat().statut === 'ok') {
              @let total = $any(etat()).page.totalElements;
              <span class="total hint" aria-live="polite">{{ total }} {{ total > 1 ? 'demandes' : 'demande' }}</span>
            }
          </div>
          @if (erreurAction()) { <p class="alerte erreur" role="alert">{{ erreurAction() }}</p> }

          @switch (etat().statut) {
            @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
            @case ('erreur') { <div class="vide"><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p></div> }
            @case ('ok') {
              @let p = $any(etat()).page;
              @for (d of p.contenu; track d.id) {
                @let st = statutDemande(d);
                <div class="ligne demande">
                  <a class="quoi" [routerLink]="['/leave-requests', d.id]">
                    <b>{{ periode(d.dateDebut, d.dateFin) }}</b>
                    <small>{{ d.typeCongeLibelle }} · {{ jours(d.nombreJours) }}</small>
                  </a>
                  <span class="status {{ st.classe }}">{{ st.libelle }}</span>
                  <div class="actions">
                    @if (peutAnnuler(d, aujourdhui)) {
                      @if (aConfirmer() === d.id) {
                        <span class="hint">Annuler cette demande ?</span>
                        <p-button label="Non" severity="secondary" (onClick)="aConfirmer.set(null)" />
                        <p-button label="Confirmer" severity="danger" styleClass="confirmer" [loading]="occupe()" (onClick)="annuler(d.id)" />
                      } @else {
                        <p-button label="Annuler" severity="secondary" (onClick)="demanderConfirmation(d.id)" />
                      }
                    }
                  </div>
                </div>
              } @empty {
                <div class="vide">
                  @if (statut()) {
                    <p><strong>Aucune demande avec ce statut.</strong></p>
                  } @else {
                    <p><strong>Aucune demande de congé pour l'instant.</strong></p>
                    <p class="hint">Faites votre première demande avec « Nouvelle demande ».</p>
                  }
                </div>
              }
              @if (p.totalElements > taille) {
                <p-paginator [first]="page() * taille" [rows]="taille" [totalRecords]="p.totalElements" (onPageChange)="paginer($event)" />
              }
            }
          }
        </section>

        <aside class="cote"><app-soldes [soldes]="soldes() ?? []" /><app-historique-soldes /></aside>
      </div>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: var(--rh-content-max); }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: var(--rh-space-6); align-items: start; }
    .liste { min-width: 0; }
    .cote { display: grid; gap: var(--rh-space-4); align-content: start; }
    .filtres { display: flex; flex-wrap: wrap; align-items: center; gap: var(--rh-space-2); padding-bottom: var(--rh-space-3); border-bottom: 1px solid var(--rh-line); }
    .filtres p-select { flex: 0 1 220px; }
    .total { margin-left: auto; }
    .demande { padding-inline: var(--rh-space-2); }
    .quoi { flex: 1; min-width: 0; color: var(--rh-ink); text-decoration: none; }
    .quoi b { display: block; font-weight: 500; }
    .quoi:hover b { text-decoration: underline; text-underline-offset: 3px; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--rh-space-2); min-width: 96px; }
    .vide { display: grid; gap: var(--rh-space-1); padding: var(--rh-space-8) var(--rh-space-2); }
    .alerte { margin-top: var(--rh-space-3); }
    @media (max-width: 1100px) {
      .grille { grid-template-columns: minmax(0, 1fr); }
      aside { order: -1; }
    }
    @media (max-width: 600px) {
      .demande { flex-wrap: wrap; }
      .actions { flex-basis: 100%; justify-content: flex-start; }
    }
  `,
})
export class MesConges {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly recharge$ = new BehaviorSubject<void>(undefined);

  protected readonly filtres = FILTRES_STATUT;
  protected readonly taille = 10;
  protected readonly aujourdhui = dateIso(new Date());
  protected readonly statutDemande = statutDemande;
  protected readonly periode = periode;
  protected readonly jours = jours;
  protected readonly peutAnnuler = peutAnnuler;

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly statut = computed(() => (this.params().get('statut') as StatutDemande | null) || null);
  protected readonly page = computed(() => Math.max(0, Number(this.params().get('page') ?? 0) || 0));

  protected readonly etat = toSignal(
    combineLatest([this.route.queryParamMap, this.recharge$]).pipe(
      switchMap(([q]) => this.api.mesDemandesPage({
        statut: (q.get('statut') as StatutDemande | null) || null,
        page: Math.max(0, Number(q.get('page') ?? 0) || 0),
        size: this.taille,
      }).pipe(
        map((page): Etat => ({ statut: 'ok', page })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly soldes = toSignal(this.recharge$.pipe(switchMap(() => this.api.mesSoldes().pipe(catchError(() => of([]))))));

  protected readonly aConfirmer = signal<number | null>(null);
  protected readonly occupe = signal(false);
  protected readonly erreurAction = signal('');

  protected filtrer(statut: StatutDemande | null): void {
    void this.router.navigate([], { queryParams: { statut: statut || null, page: null }, queryParamsHandling: 'merge' });
  }

  protected paginer(e: PaginatorState): void {
    void this.router.navigate([], { queryParams: { page: e.page || null }, queryParamsHandling: 'merge' });
  }

  /** The pressed button disappears: focus goes to « Confirmer » so keyboard users keep their place. */
  protected demanderConfirmation(id: number): void {
    this.aConfirmer.set(id);
    afterNextRender(() => this.hote.nativeElement.querySelector<HTMLButtonElement>('button.confirmer')?.focus(), { injector: this.injector });
  }

  protected async annuler(id: number): Promise<void> {
    if (this.occupe()) return;
    this.occupe.set(true);
    this.erreurAction.set('');
    try {
      await firstValueFrom(this.api.annulerDemande(id));
      this.aConfirmer.set(null);
      this.recharge$.next();
    } catch (e) {
      this.erreurAction.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
