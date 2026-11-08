import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { Departement } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';
import { Apparition, Compteur } from '../core/motion/motion';

type Etat = { statut: 'chargement' } | { statut: 'ok'; liste: Departement[] } | { statut: 'erreur'; message: string };

@Component({
  selector: 'app-departements-liste',
  imports: [RouterLink, ButtonModule, Apparition, Compteur],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Entreprise</p><h1 class="page-titre">Départements</h1></div>
        @if (gestion()) { <a pButton routerLink="/departments/new" icon="pi pi-plus">Ajouter un département</a> }
      </header>
      <section class="card">
        @switch (etat().statut) {
          @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
          @case ('erreur') { <div class="vide"><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p></div> }
          @case ('ok') {
            @for (d of $any(etat()).liste; track d.id) {
              <a class="ligne dept" [routerLink]="['/departments', d.id]">
                <span class="nom"><b>{{ d.nom }}</b>@if (d.description) { <small>{{ d.description }}</small> }</span>
                <span class="effectif"><span class="lecture" [rhCompteur]="d.nombreEmployes"></span><small>{{ d.nombreEmployes > 1 ? 'employés' : 'employé' }}</small></span>
              </a>
            } @empty {
              <div class="vide"><p><strong>Aucun département pour l'instant.</strong></p></div>
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); max-width: 900px; }
    .dept { color: var(--rh-ink); text-decoration: none; transition: background var(--rh-fast) var(--rh-ease); }
    .dept:hover { background: var(--rh-surface-2); }
    .dept:hover b { text-decoration: underline; text-underline-offset: 3px; }
    .nom { min-width: 0; }
    .nom b { font-weight: 500; }
    .effectif { display: flex; align-items: baseline; gap: 6px; white-space: nowrap; }
    .effectif small { display: inline; color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .vide { padding: var(--rh-space-8) var(--rh-space-4); }
  `,
})
export class DepartementsListe {
  private readonly auth = inject(AuthService);
  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  protected readonly etat = toSignal(
    inject(RhApi).departements().pipe(
      map((liste): Etat => ({ statut: 'ok', liste })),
      catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
      startWith<Etat>({ statut: 'chargement' }),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
}
