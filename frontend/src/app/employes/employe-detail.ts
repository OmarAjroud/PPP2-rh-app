import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { Compte, DemandeConge, EmployeDetail, Solde } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { initiales, jours, libelleRole, periode, statutDemande } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Apparition, Compteur } from '../core/motion/motion';
import { HistoriqueSoldes } from '../conges/historique-soldes';

type Etat = { statut: 'chargement' } | { statut: 'ok'; e: EmployeDetail } | { statut: 'erreur'; message: string };
/** undefined: loading or not allowed to see it, null: no account yet. */
type EtatCompte = Compte | null | undefined;

const STATUTS_COMPTE: Record<Compte['statut'], { libelle: string; classe: string }> = {
  EN_ATTENTE: { libelle: 'Invitation envoyée', classe: 's-wait' },
  ACTIF: { libelle: 'Actif', classe: 's-ok' },
  DESACTIVE: { libelle: 'Désactivé', classe: 's-off' },
};

/** /employees/:id, and /profile (route data "moi") for the signed-in user's own record. */
@Component({
  selector: 'app-employe-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, ButtonModule, Apparition, Compteur, HistoriqueSoldes],
  templateUrl: './employe-detail.html',
  styleUrl: './employe-detail.scss',
})
export class EmployeDetailPage {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly moi = this.route.snapshot.data['moi'] === true;
  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  protected readonly libelleListe = computed(() => (this.auth.profil()?.role === 'MANAGER' ? 'Mon équipe' : 'Employés'));
  protected readonly statutsCompte = STATUTS_COMPTE;
  protected readonly statutDemande = statutDemande;
  protected readonly periode = periode;
  protected readonly jours = jours;
  protected readonly libelleRole = libelleRole;
  /** One's own record opened from a link (not /profile): HR may not adjust their own balance. */
  protected readonly monId = computed(() => this.auth.profil()?.employeId);

  protected readonly etat = toSignal(
    this.route.paramMap.pipe(
      switchMap(p => (this.moi ? this.api.monProfil() : this.api.employe(Number(p.get('id')))).pipe(
        map((e): Etat => ({ statut: 'ok', e })),
        catchError(err => of<Etat>({ statut: 'erreur', message: messageErreur(err) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  protected readonly employe = computed(() => { const s = this.etat(); return s.statut === 'ok' ? s.e : null; });
  protected readonly initiales = computed(() => { const e = this.employe(); return e ? initiales(e.prenom, e.nom) : ''; });
  /** The salary is hidden from managers; ADMIN, RH and the person themself see it. */
  protected readonly peutVoirSalaire = computed(() => this.gestion() || this.employe()?.id === this.auth.profil()?.employeId);

  // ---- Balances and recent requests ----
  /** undefined: loading or not visible to this role. */
  protected readonly soldes = signal<Solde[] | undefined>(undefined);
  protected readonly demandes = signal<DemandeConge[] | undefined>(undefined);

  // ---- Account (ADMIN, RH) ----
  protected readonly compte = signal<EtatCompte>(undefined);
  protected readonly messageCompte = signal<{ type: 'succes' | 'erreur'; texte: string } | null>(null);
  protected readonly confirmerDesactivation = signal(false);

  // ---- Deletion ----
  protected readonly confirmerSuppression = signal(false);
  protected readonly erreurSuppression = signal('');
  protected readonly occupe = signal(false);

  constructor() {
    this.route.paramMap.pipe(
      switchMap(p => {
        const id = Number(p.get('id'));
        if (this.moi) return this.api.mesSoldes();
        return this.api.soldes(id);
      }),
      catchError(() => of(undefined)),
    ).subscribe(s => this.soldes.set(s));

    // Recent requests: my own, or anyone's for ADMIN and RH (the global view is theirs only)
    if (this.moi || this.gestion()) {
      this.route.paramMap.pipe(
        switchMap(p => (this.moi ? this.api.mesDemandes(4) : this.api.demandesDe(Number(p.get('id')), 4))),
        map(page => page.contenu),
        catchError(() => of(undefined)),
      ).subscribe(d => this.demandes.set(d));
    }

    if (!this.moi && this.gestion()) {
      this.route.paramMap.pipe(
        switchMap(p => this.api.compte(Number(p.get('id'))).pipe(
          catchError(err => of(err instanceof HttpErrorResponse && err.status === 404 ? null : undefined)),
        )),
      ).subscribe(c => this.compte.set(c));
    }
  }

  /** After an HR adjustment: the balances shown above the history are read again. */
  protected rechargerSoldes(): void {
    const e = this.employe();
    if (!e) return;
    (this.moi ? this.api.mesSoldes() : this.api.soldes(e.id)).pipe(catchError(() => of(undefined))).subscribe(s => this.soldes.set(s));
  }

  /** "19 → 15" while requests are pending (before → after), else the available days. */
  protected lecture(s: Solde): { avant: number | null; apres: number } {
    return s.joursEnAttente > 0
      ? { avant: s.joursAlloues - s.joursPris, apres: s.joursDisponibles }
      : { avant: null, apres: s.joursDisponibles };
  }

  protected async actionCompte(action: 'inviter' | 'desactiver' | 'reactiver'): Promise<void> {
    const e = this.employe();
    if (!e) return;
    this.occupe.set(true);
    this.messageCompte.set(null);
    try {
      const c = await firstValueFrom(this.api[action](e.id));
      this.compte.set(c);
      this.confirmerDesactivation.set(false);
      this.messageCompte.set({ type: 'succes', texte: {
        inviter: `Invitation envoyée à ${e.email}. Le lien est valable 48 heures.`,
        desactiver: 'Compte désactivé : connexion refusée et sessions fermées immédiatement.',
        reactiver: 'Compte réactivé : la personne peut de nouveau se connecter.',
      }[action] });
    } catch (err) {
      this.messageCompte.set({ type: 'erreur', texte: messageErreur(err) });
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(): Promise<void> {
    const e = this.employe();
    if (!e) return;
    this.occupe.set(true);
    this.erreurSuppression.set('');
    try {
      await firstValueFrom(this.api.supprimerEmploye(e.id));
      await this.router.navigate(['/employees']);
    } catch (err) {
      this.erreurSuppression.set(messageErreur(err));
      this.confirmerSuppression.set(false);
    } finally {
      this.occupe.set(false);
    }
  }
}
