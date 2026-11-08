import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { Role } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { libelleRole } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';

/** Mirrors RoleService.ROLES_SYSTEME: these roles carry the app's permissions. */
export const ROLES_SYSTEME = ['ADMIN', 'RH', 'MANAGER', 'EMPLOYE'];

type Etat = { statut: 'chargement' } | { statut: 'ok'; r: Role } | { statut: 'erreur'; message: string };

@Component({
  selector: 'app-role-detail',
  imports: [RouterLink, ButtonModule, Apparition],
  template: `
    @switch (etat().statut) {
      @case ('chargement') { <p class="crumb"><a routerLink="/roles">Rôles</a></p><p class="hint" aria-busy="true">Chargement…</p> }
      @case ('erreur') { <p class="crumb"><a routerLink="/roles">Rôles</a></p><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
      @case ('ok') {
        @let r = role()!;
        <div class="page" rhApparition>
          <header class="entete">
            <div><p class="crumb"><a routerLink="/roles">Rôles</a></p><h1 class="page-titre">{{ libelleRole(r.nom) }}</h1></div>
            @if (admin() && !systeme(r)) { <a pButton severity="secondary" [routerLink]="['/roles', r.id, 'edit']">Modifier</a> }
          </header>
          @if (systeme(r)) { <p class="alerte info">Rôle du système : il donne les droits d'accès de l'application et ne peut être ni modifié ni supprimé.</p> }
          <section class="card" aria-labelledby="fiche">
            <h2 id="fiche" class="card-h">Fiche</h2>
            <dl class="fiche">
              <dt>Nom</dt><dd class="mono">{{ r.nom }}</dd>
              <dt>Description</dt><dd>@if (r.description) { {{ r.description }} } @else { <span class="hint">Aucune</span> }</dd>
            </dl>
          </section>
          @if (admin() && !systeme(r)) {
            <section class="card" aria-labelledby="suppression">
              <h2 id="suppression" class="card-h">Supprimer le rôle</h2>
              <div class="card-b">
                <p>Possible uniquement si aucun employé n'a ce rôle.</p>
                @if (erreurSuppression()) { <p class="alerte erreur" role="alert">{{ erreurSuppression() }}</p> }
              </div>
              <div class="card-f">
                @if (confirmer()) {
                  <span>Supprimer « {{ libelleRole(r.nom) }} » ?</span>
                  <div class="actions">
                    <p-button label="Annuler" severity="secondary" (onClick)="confirmer.set(false)" />
                    <p-button label="Supprimer" severity="danger" [loading]="occupe()" (onClick)="supprimer(r.id)" />
                  </div>
                } @else {
                  <div class="actions"><p-button label="Supprimer" severity="danger" [outlined]="true" (onClick)="confirmer.set(true)" /></div>
                }
              </div>
            </section>
          }
        </div>
      }
    }
  `,
  styles: `.page { display: grid; gap: var(--rh-space-4); --page-max: 760px; }`,
})
export class RoleDetailPage {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly libelleRole = libelleRole;
  protected readonly admin = computed(() => this.auth.profil()?.role === 'ADMIN');
  protected systeme(r: Role): boolean { return ROLES_SYSTEME.includes(r.nom); }
  protected readonly etat = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      switchMap(p => this.api.role(Number(p.get('id'))).pipe(
        map((r): Etat => ({ statut: 'ok', r })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly role = computed(() => { const s = this.etat(); return s.statut === 'ok' ? s.r : null; });
  protected readonly confirmer = signal(false);
  protected readonly occupe = signal(false);
  protected readonly erreurSuppression = signal('');

  protected async supprimer(id: number): Promise<void> {
    this.occupe.set(true);
    this.erreurSuppression.set('');
    try {
      await firstValueFrom(this.api.supprimerRole(id));
      await this.router.navigate(['/roles']);
    } catch (e) {
      this.erreurSuppression.set(messageErreur(e));
      this.confirmer.set(false);
    } finally {
      this.occupe.set(false);
    }
  }
}
