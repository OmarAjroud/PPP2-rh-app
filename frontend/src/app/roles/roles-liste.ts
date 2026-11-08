import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { Role } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { ROLES_SYSTEME } from './role-detail';

type Etat = { statut: 'chargement' } | { statut: 'ok'; liste: Role[] } | { statut: 'erreur'; message: string };

@Component({
  selector: 'app-roles-liste',
  imports: [RouterLink, ButtonModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Entreprise</p><h1 class="page-titre">Rôles</h1></div>
        @if (admin()) { <a pButton routerLink="/roles/new" icon="pi pi-plus">Ajouter un rôle</a> }
      </header>
      <section class="card">
        @switch (etat().statut) {
          @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
          @case ('erreur') { <div class="vide"><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p></div> }
          @case ('ok') {
            @for (r of $any(etat()).liste; track r.id) {
              <a class="ligne role" [routerLink]="['/roles', r.id]">
                <span><b class="mono">{{ r.nom }}</b>@if (r.description) { <small>{{ r.description }}</small> }</span>
                @if (systeme(r.nom)) { <span class="tag">Système</span> }
              </a>
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-6); max-width: 900px; }
    .role { color: var(--rh-ink); text-decoration: none; transition: background var(--rh-fast) var(--rh-ease); }
    .role:hover { background: var(--rh-surface-3); }
    .role:hover b { color: var(--rh-accent); }
    .role:first-child { border-radius: var(--rh-radius-lg) var(--rh-radius-lg) 0 0; }
    .role:last-child { border-radius: 0 0 var(--rh-radius-lg) var(--rh-radius-lg); }
    .role b { font-weight: 500; }
    .vide { padding: var(--rh-space-8) var(--rh-space-6); }
  `,
})
export class RolesListe {
  private readonly auth = inject(AuthService);
  protected readonly admin = computed(() => this.auth.profil()?.role === 'ADMIN');
  protected systeme(nom: string): boolean { return ROLES_SYSTEME.includes(nom); }
  protected readonly etat = toSignal(
    inject(RhApi).roles().pipe(
      map((liste): Etat => ({ statut: 'ok', liste })),
      catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
      startWith<Etat>({ statut: 'chargement' }),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
}
