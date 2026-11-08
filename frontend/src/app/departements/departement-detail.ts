import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { DepartementDetail } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { initiales } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';

type Etat = { statut: 'chargement' } | { statut: 'ok'; d: DepartementDetail } | { statut: 'erreur'; message: string };

@Component({
  selector: 'app-departement-detail',
  imports: [RouterLink, ButtonModule, Apparition],
  template: `
    @switch (etat().statut) {
      @case ('chargement') { <p class="crumb"><a routerLink="/departments">Départements</a></p><p class="hint" aria-busy="true">Chargement…</p> }
      @case ('erreur') { <p class="crumb"><a routerLink="/departments">Départements</a></p><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
      @case ('ok') {
        @let d = departement()!;
        <div class="page" rhApparition>
          <header class="entete">
            <div>
              <p class="crumb"><a routerLink="/departments">Départements</a></p>
              <h1 class="page-titre">{{ d.nom }}</h1>
              @if (d.description) { <p class="intro">{{ d.description }}</p> }
            </div>
            @if (gestion()) { <a pButton severity="secondary" [routerLink]="['/departments', d.id, 'edit']">Modifier</a> }
          </header>

          <section class="card" aria-labelledby="membres">
            <div class="card-h"><h2 id="membres">Équipe</h2><span class="hint">{{ d.employes.length }} {{ d.employes.length > 1 ? 'personnes' : 'personne' }}</span></div>
            @for (e of d.employes; track e.id) {
              <div class="ligne">
                @if (lienEmployes()) {
                  <a class="personne" [routerLink]="['/employees', e.id]"><span class="avatar" aria-hidden="true">{{ initiales(e.prenom, e.nom) }}</span><b>{{ e.prenom }} {{ e.nom }}</b></a>
                } @else {
                  <span class="personne"><span class="avatar" aria-hidden="true">{{ initiales(e.prenom, e.nom) }}</span><b>{{ e.prenom }} {{ e.nom }}</b></span>
                }
                <span class="hint">{{ e.email }}</span>
              </div>
            } @empty {
              <p class="vide hint">Aucun employé dans ce département.</p>
            }
          </section>

          @if (gestion()) {
            <section class="card" aria-labelledby="suppression">
              <h2 id="suppression" class="card-h">Supprimer le département</h2>
              <div class="card-b">
                @if (d.employes.length > 0) {
                  <p>Impossible pour l'instant : {{ d.employes.length }} {{ d.employes.length > 1 ? 'employés y sont rattachés' : 'employé y est rattaché' }}. Changez-les de département, puis revenez ici.</p>
                } @else {
                  <p>Ce département ne contient plus aucun employé : il peut être supprimé.</p>
                }
                @if (erreurSuppression()) { <p class="alerte erreur" role="alert">{{ erreurSuppression() }}</p> }
              </div>
              @if (d.employes.length === 0) {
                <div class="card-f">
                  @if (confirmer()) {
                    <span>Supprimer « {{ d.nom }} » ?</span>
                    <div class="actions">
                      <p-button label="Annuler" severity="secondary" (onClick)="confirmer.set(false)" />
                      <p-button label="Supprimer" severity="danger" [loading]="occupe()" (onClick)="supprimer(d.id)" />
                    </div>
                  } @else {
                    <div class="actions"><p-button label="Supprimer" severity="danger" [outlined]="true" (onClick)="confirmer.set(true)" /></div>
                  }
                </div>
              }
            </section>
          }
        </div>
      }
    }
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: 900px; }
    .intro { flex-basis: 100%; color: var(--rh-ink-2); max-width: 65ch; }
    .card-h h2 { font-size: inherit; font-weight: inherit; }
    .personne { display: flex; align-items: center; gap: var(--rh-space-3); color: var(--rh-ink); text-decoration: none; }
    .personne b { font-weight: 500; }
    a.personne:hover b { text-decoration: underline; text-underline-offset: 3px; }
    .avatar { flex: none; width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; background: var(--rh-surface-3); color: var(--rh-ink-2); font-size: var(--rh-text-sm); font-weight: 600; }
    .vide { padding: var(--rh-space-4); }
    @media (max-width: 560px) { .ligne { flex-wrap: wrap; } }
  `,
})
export class DepartementDetailPage {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  /** Employees can open only their own record, so the names are links for the other roles. */
  protected readonly lienEmployes = computed(() => this.auth.profil()?.role !== 'EMPLOYE');
  protected readonly initiales = initiales;

  protected readonly etat = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      switchMap(p => this.api.departement(Number(p.get('id'))).pipe(
        map((d): Etat => ({ statut: 'ok', d })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly departement = computed(() => { const s = this.etat(); return s.statut === 'ok' ? s.d : null; });

  protected readonly confirmer = signal(false);
  protected readonly occupe = signal(false);
  protected readonly erreurSuppression = signal('');

  protected async supprimer(id: number): Promise<void> {
    this.occupe.set(true);
    this.erreurSuppression.set('');
    try {
      await firstValueFrom(this.api.supprimerDepartement(id));
      await this.router.navigate(['/departments']);
    } catch (e) {
      this.erreurSuppression.set(messageErreur(e));
      this.confirmer.set(false);
    } finally {
      this.occupe.set(false);
    }
  }
}
