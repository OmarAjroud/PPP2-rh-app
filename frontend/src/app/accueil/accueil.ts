import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { Apparition } from '../core/motion/motion';
import { environment } from '../../environments/environment';
import { RhApi } from '../core/api/rh-api';
import { DemandeConge, Solde } from '../core/api/models';
import { jours, periode, statutDemande } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Moi } from '../core/auth/moi';
import { SoldesPanel } from '../conges/soldes-panel';

type EtatApi = 'verification' | 'UP' | 'DOWN' | 'injoignable';

/**
 * Home: greeting, what I have (balances), my last requests and, for those who decide,
 * how many requests wait for them. The server status is a discreet line in the header.
 */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, RouterLink, ButtonModule, Apparition, SoldesPanel],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  private readonly http = inject(HttpClient);
  private readonly api = inject(RhApi);
  private readonly auth = inject(AuthService);
  private readonly moi = inject(Moi);
  protected readonly prenom = computed(() => this.moi.fiche()?.prenom ?? this.auth.profil()?.nomUtilisateur ?? '');
  protected readonly apiUrl = environment.apiUrl;
  protected readonly etat = signal<EtatApi>('verification');
  protected readonly aujourdhui = new Date();
  protected readonly statutDemande = statutDemande;
  protected readonly periode = periode;
  protected readonly jours = jours;

  protected readonly decideur = computed(() => ['MANAGER', 'RH', 'ADMIN'].includes(this.auth.profil()?.role ?? ''));
  protected readonly soldes = toSignal(this.api.mesSoldes().pipe(catchError(() => of<Solde[]>([]))));
  protected readonly demandes = toSignal(this.api.mesDemandesPage({ page: 0, size: 3 }).pipe(map(p => p.contenu), catchError(() => of<DemandeConge[]>([]))));
  protected readonly aDecider = signal<number | null>(null);

  constructor() {
    this.verifier();
    if (this.decideur()) {
      this.api.aDecider(0, 1).subscribe({ next: p => this.aDecider.set(p.totalElements), error: () => this.aDecider.set(null) });
    }
  }

  protected verifier(): void {
    this.etat.set('verification');
    this.http.get<{ status: 'UP' | 'DOWN' }>(`${this.apiUrl}/actuator/health`).subscribe({
      next: r => this.etat.set(r.status),
      error: () => this.etat.set('injoignable'),
    });
  }
}
