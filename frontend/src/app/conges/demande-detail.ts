import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, Observable, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { RhApi } from '../core/api/rh-api';
import { DemandeConge } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { jours, periode, statutDemande } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { dateIso, peutAnnuler, peutInterrompre } from './regles';

type Chargement = { d?: DemandeConge; erreur?: string };
type Action = 'annuler' | 'approuver' | 'refuser' | 'interrompre';

/**
 * One leave request: what was asked, where the decision stands, and the actions the reader may take.
 * Buttons only appear where they make sense; the API still decides and its message is shown.
 */
@Component({
  selector: 'app-demande-detail',
  imports: [RouterLink, DatePipe, ButtonModule, TextareaModule, Apparition],
  template: `
    @if (erreurChargement()) {
      <p class="crumb"><a routerLink="/leave-requests">Mes congés</a></p>
      <p class="alerte erreur" role="alert">{{ erreurChargement() }}</p>
    } @else if (!demande()) {
      <p class="hint" aria-busy="true">Chargement…</p>
    } @else {
      @let d = demande()!;
      @let st = statutDemande(d);
      <div class="page" rhApparition>
        <header class="entete">
          <div>
            <p class="crumb">@if (moi()) { <a routerLink="/leave-requests">Mes congés</a> } @else { <a routerLink="/approvals">À décider</a> }</p>
            <h1 class="page-titre">{{ periode(d.dateDebut, d.dateFin) }}</h1>
          </div>
          <span class="status {{ st.classe }}">{{ st.libelle }}</span>
        </header>

        <div class="grille">
          <section class="card" aria-labelledby="demande-titre">
            <h2 id="demande-titre" class="card-h">Demande</h2>
            <dl class="fiche">
              @if (!moi()) { <dt>Personne</dt><dd>{{ d.employeNomComplet }}<span class="hint"> · {{ d.employeDepartementNom }}</span></dd> }
              <dt>Type</dt><dd>{{ d.typeCongeLibelle }}</dd>
              <dt>Du</dt><dd>{{ d.dateDebut | date: 'EEEE d MMMM y' }}</dd>
              <dt>Au</dt><dd>{{ d.dateFin | date: 'EEEE d MMMM y' }}</dd>
              <dt>Jours ouvrables</dt><dd><span class="lecture">{{ d.nombreJours }}</span> {{ d.nombreJours > 1 ? 'jours' : 'jour' }}</dd>
              <dt>Commentaire</dt><dd>@if (d.commentaire) { {{ d.commentaire }} } @else { <span class="hint">Aucun</span> }</dd>
              <dt>Envoyée le</dt><dd>{{ d.dateCreation | date: 'd MMMM y, HH:mm' }}</dd>
            </dl>
          </section>

          <div class="colonne">
            <section class="card" aria-labelledby="decision-titre">
              <h2 id="decision-titre" class="card-h">Décision</h2>
              <dl class="fiche">
                <dt>Statut</dt><dd><span class="status {{ st.classe }}">{{ st.libelle }}</span></dd>
                @if (d.decideParNomComplet) {
                  <dt>Décidée par</dt><dd>{{ d.decideParNomComplet }}</dd>
                  <dt>Le</dt><dd>{{ d.dateDecision | date: 'd MMMM y, HH:mm' }}</dd>
                }
                @if (d.motifDecision) { <dt>{{ d.statut === 'REFUSEE' ? 'Motif' : 'Commentaire' }}</dt><dd>{{ d.motifDecision }}</dd> }
                @if (d.dateEscalade) { <dt>Transmise aux RH</dt><dd>{{ d.dateEscalade | date: 'd MMMM y' }}</dd> }
                @if (d.motifInterruption) { <dt>Interrompu</dt><dd>{{ d.motifInterruption }}</dd> }
                @if (d.statut === 'EN_ATTENTE' && !d.dateEscalade) { <dt>Qui décide</dt><dd>À traiter par le manager<span class="hint"> · transmise aux RH sans réponse sous 3 jours</span></dd> }
              </dl>
            </section>

            @if (peutDecider() || peutInterrompre() || peutAnnuler()) {
              <section class="card" aria-labelledby="actions-titre">
                <h2 id="actions-titre" class="card-h">{{ peutAnnuler() ? 'Votre demande' : 'Votre décision' }}</h2>
                <div class="card-b">
                  @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
                  @if (peutDecider() || peutInterrompre()) {
                    <div class="champ">
                      <label for="motif">{{ peutDecider() ? 'Commentaire ou motif' : 'Motif de l’interruption' }}</label>
                      <textarea pTextarea id="motif" rows="3" maxlength="500" [value]="motif()" (input)="motif.set($any($event.target).value)"></textarea>
                      <span class="aide">{{ peutDecider() ? 'Facultatif pour approuver, obligatoire pour refuser.' : 'Obligatoire. Les jours non pris sont rendus au solde.' }}</span>
                    </div>
                  }
                  @if (peutAnnuler()) {
                    <p>{{ d.statut === 'APPROUVEE' ? 'Le congé n’a pas commencé : en l’annulant, les jours sont rendus à votre solde.' : 'Votre demande attend sa décision : vous pouvez encore l’annuler.' }}</p>
                  }
                </div>
                <div class="card-f">
                  <span></span>
                  <div class="actions">
                    @if (peutDecider()) {
                      <p-button label="Refuser" severity="danger" [outlined]="true" [loading]="occupe() === 'refuser'" [disabled]="!!occupe()" (onClick)="agir('refuser')" />
                      <p-button label="Approuver" [loading]="occupe() === 'approuver'" [disabled]="!!occupe()" (onClick)="agir('approuver')" />
                    }
                    @if (peutInterrompre()) {
                      <p-button label="Interrompre le congé" severity="danger" [outlined]="true" [loading]="occupe() === 'interrompre'" [disabled]="!!occupe()" (onClick)="agir('interrompre')" />
                    }
                    @if (peutAnnuler()) {
                      @if (confirmerAnnulation()) {
                        <span class="hint">Annuler définitivement cette demande ?</span>
                        <p-button label="Non" severity="secondary" (onClick)="confirmerAnnulation.set(false)" />
                        <p-button label="Confirmer l’annulation" severity="danger" [loading]="occupe() === 'annuler'" [disabled]="!!occupe()" (onClick)="agir('annuler')" />
                      } @else {
                        <p-button label="Annuler la demande" severity="danger" [outlined]="true" (onClick)="confirmerAnnulation.set(true)" />
                      }
                    }
                  </div>
                </div>
              </section>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: var(--rh-content-max); }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: var(--rh-space-6); align-items: start; }
    .colonne { display: grid; gap: var(--rh-space-4); min-width: 0; }
    .fiche dd::first-letter { text-transform: uppercase; }
    textarea { width: 100%; resize: vertical; }
    @media (max-width: 1100px) { .grille { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class DemandeDetail {
  private readonly api = inject(RhApi);
  private readonly auth = inject(AuthService);
  private readonly aujourdhui = dateIso(new Date());
  protected readonly statutDemande = statutDemande;
  protected readonly periode = periode;
  protected readonly jours = jours;

  protected readonly demande = signal<DemandeConge | null>(null);
  protected readonly erreurChargement = signal('');
  protected readonly motif = signal('');
  protected readonly erreur = signal('');
  protected readonly occupe = signal<Action | null>(null);
  protected readonly confirmerAnnulation = signal(false);

  private readonly role = computed(() => this.auth.profil()?.role);
  protected readonly moi = computed(() => this.demande()?.employeId === this.auth.profil()?.employeId);
  protected readonly peutAnnuler = computed(() => {
    const d = this.demande();
    return !!d && this.moi() && peutAnnuler(d, this.aujourdhui);
  });
  protected readonly peutDecider = computed(() => {
    const d = this.demande(), r = this.role();
    if (!d || this.moi() || d.statut !== 'EN_ATTENTE') return false;
    // RH and ADMIN decide only once the request has been passed to them (no manager, manager unavailable, delay over)
    return r === 'MANAGER' || ((r === 'RH' || r === 'ADMIN') && !!d.dateEscalade);
  });
  protected readonly peutInterrompre = computed(() => {
    const d = this.demande(), r = this.role();
    return !!d && !this.moi() && (r === 'RH' || r === 'ADMIN') && peutInterrompre(d, this.aujourdhui);
  });

  constructor() {
    // switchMap: a slow answer for a previous id can never replace the current request
    inject(ActivatedRoute).paramMap.pipe(
      switchMap(p => {
        this.demande.set(null);
        this.erreurChargement.set('');
        const id = Number(p.get('id'));
        if (!Number.isInteger(id) || id <= 0) return of<Chargement>({ erreur: 'Demande introuvable.' });
        return this.api.demande(id).pipe(map((d): Chargement => ({ d })), catchError(e => of<Chargement>({ erreur: messageErreur(e) })));
      }),
      takeUntilDestroyed(),
    ).subscribe(r => (r.d ? this.demande.set(r.d) : this.erreurChargement.set(r.erreur ?? '')));
  }

  protected async agir(action: Action): Promise<void> {
    const d = this.demande();
    if (!d || this.occupe()) return;
    const motif = this.motif().trim();
    if ((action === 'refuser' || action === 'interrompre') && !motif) {
      this.erreur.set(action === 'refuser' ? 'Indiquez le motif du refus.' : 'Indiquez le motif de l’interruption.');
      return;
    }
    const appels: Record<Action, () => Observable<DemandeConge>> = {
      annuler: () => this.api.annulerDemande(d.id),
      approuver: () => this.api.validerDemande(d.id, motif),
      refuser: () => this.api.refuserDemande(d.id, motif),
      interrompre: () => this.api.interrompreDemande(d.id, motif),
    };
    this.occupe.set(action);
    this.erreur.set('');
    try {
      this.demande.set(await firstValueFrom(appels[action]()));
      this.motif.set('');
      this.confirmerAnnulation.set(false);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(null);
    }
  }
}
