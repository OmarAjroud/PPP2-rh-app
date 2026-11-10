import { Component, computed, inject, signal } from '@angular/core';
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
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { FILTRES_STATUT } from './mes-conges';
import { ExportListe } from '../shared/export-liste';

type Vue = 'a-decider' | 'equipe' | 'toutes';
type Etat = { statut: 'chargement' } | { statut: 'ok'; page: PageReponse<DemandeConge> } | { statut: 'erreur'; message: string };

/**
 * Requests to decide (MANAGER, RH, ADMIN), the team's requests, and for RH/ADMIN every request.
 * Approving is one click from the list; refusing needs a reason, so it opens the request.
 */
@Component({
  selector: 'app-a-decider',
  imports: [ExportListe, RouterLink, FormsModule, ButtonModule, PaginatorModule, SelectModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Congés</p><h1 class="page-titre">À décider</h1></div>
        @if (vue() === 'toutes') { <app-export-liste liste="demandes" [filtres]="{ statut: statut() }" /> }
      </header>

      <section class="liste">
        <div class="barre">
          <nav class="onglets" aria-label="Vues">
            @for (o of onglets(); track o.vue) {
              <a [attr.aria-current]="vue() === o.vue ? 'page' : null" [routerLink]="[]" [queryParams]="{ vue: o.vue === 'a-decider' ? null : o.vue }" (click)="erreurAction.set('')">{{ o.libelle }}</a>
            }
          </nav>
          @if (vue() !== 'a-decider') {
            <p-select [options]="filtres" [ngModel]="statut()" (ngModelChange)="filtrer($event)" ariaLabel="Filtrer par statut" placeholder="Tous les statuts" />
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
                  <b>{{ d.employeNomComplet }}</b>
                  <small>{{ periode(d.dateDebut, d.dateFin) }} · {{ d.typeCongeLibelle }} · {{ jours(d.nombreJours) }}</small>
                </a>
                <span class="status {{ st.classe }}">{{ st.libelle }}</span>
                <div class="actions">
                  @if (vue() === 'a-decider') {
                    <a pButton severity="secondary" [routerLink]="['/leave-requests', d.id]">Refuser…</a>
                    <p-button label="Approuver" [loading]="occupe() === d.id" [disabled]="occupe() !== null" (onClick)="approuver(d.id)" />
                  }
                </div>
              </div>
            } @empty {
              <div class="vide">
                @if (vue() === 'a-decider') {
                  <p><strong>Rien à décider pour l'instant.</strong></p>
                  <p class="hint">Les nouvelles demandes à traiter apparaîtront ici.</p>
                } @else {
                  <p><strong>Aucune demande{{ statut() ? ' avec ce statut' : '' }}.</strong></p>
                }
              </div>
            }
            @if (p.totalElements > taille) {
              <p-paginator [first]="page() * taille" [rows]="taille" [totalRecords]="p.totalElements" (onPageChange)="paginer($event)" />
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: var(--rh-content-max); }
    .liste { min-width: 0; }
    .barre { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--rh-space-3); border-bottom: 1px solid var(--rh-line); padding-bottom: var(--rh-space-3); }
    .onglets { display: flex; gap: var(--rh-space-1); }
    .onglets a { padding: 6px 12px; border-radius: var(--rh-radius-sm); color: var(--rh-muted); font-weight: 500; text-decoration: none; }
    .onglets a:hover { color: var(--rh-ink); background: var(--rh-surface-2); }
    .onglets a[aria-current="page"] { color: var(--rh-ink); background: var(--rh-surface-3); }
    .barre p-select { flex: 0 1 220px; }
    .demande { padding-inline: var(--rh-space-2); }
    .quoi { flex: 1; min-width: 0; color: var(--rh-ink); text-decoration: none; }
    .quoi b { display: block; font-weight: 500; }
    .quoi:hover b { text-decoration: underline; text-underline-offset: 3px; }
    .actions { display: flex; gap: var(--rh-space-2); justify-content: flex-end; }
    .vide { display: grid; gap: var(--rh-space-1); padding: var(--rh-space-8) var(--rh-space-2); }
    .alerte { margin-top: var(--rh-space-3); }
    @media (max-width: 700px) {
      .demande { flex-wrap: wrap; }
      .actions { flex-basis: 100%; justify-content: flex-start; }
    }
  `,
})
export class ADecider {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly recharge$ = new BehaviorSubject<void>(undefined);

  protected readonly filtres = FILTRES_STATUT;
  protected readonly taille = 10;
  protected readonly statutDemande = statutDemande;
  protected readonly periode = periode;
  protected readonly jours = jours;

  protected readonly onglets = computed(() => {
    const r = this.auth.profil()?.role;
    const base: { vue: Vue; libelle: string }[] = [{ vue: 'a-decider', libelle: 'À décider' }, { vue: 'equipe', libelle: 'Équipe' }];
    return r === 'RH' || r === 'ADMIN' ? [...base, { vue: 'toutes' as Vue, libelle: 'Toutes' }] : base;
  });

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly vue = computed<Vue>(() => {
    const v = this.params().get('vue');
    return this.onglets().some(o => o.vue === v) ? (v as Vue) : 'a-decider';
  });
  protected readonly statut = computed(() => (this.params().get('statut') as StatutDemande | null) || null);
  protected readonly page = computed(() => Math.max(0, Number(this.params().get('page') ?? 0) || 0));

  protected readonly etat = toSignal(
    combineLatest([this.route.queryParamMap, this.recharge$]).pipe(
      switchMap(() => {
        const r = { statut: this.statut(), page: this.page(), size: this.taille };
        const appel = this.vue() === 'a-decider' ? this.api.aDecider(r.page, r.size)
          : this.vue() === 'equipe' ? this.api.demandesEquipe(r) : this.api.toutesDemandes(r);
        return appel.pipe(
          map((page): Etat => ({ statut: 'ok', page })),
          catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
          startWith<Etat>({ statut: 'chargement' }),
        );
      }),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );

  protected readonly occupe = signal<number | null>(null);
  protected readonly erreurAction = signal('');

  protected filtrer(statut: StatutDemande | null): void {
    void this.router.navigate([], { queryParams: { statut: statut || null, page: null }, queryParamsHandling: 'merge' });
  }

  protected paginer(e: PaginatorState): void {
    void this.router.navigate([], { queryParams: { page: e.page || null }, queryParamsHandling: 'merge' });
  }

  protected async approuver(id: number): Promise<void> {
    if (this.occupe() !== null) return;
    this.occupe.set(id);
    this.erreurAction.set('');
    try {
      await firstValueFrom(this.api.validerDemande(id));
      // The last request of a later page was decided: go back one page instead of showing an empty one
      const e = this.etat();
      if (e.statut === 'ok' && e.page.contenu.length === 1 && this.page() > 0) this.paginer({ page: this.page() - 1 });
      else this.recharge$.next();
    } catch (e) {
      this.erreurAction.set(messageErreur(e));
    } finally {
      this.occupe.set(null);
    }
  }
}
