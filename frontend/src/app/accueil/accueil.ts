import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { Apparition } from '../core/motion/motion';
import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth.service';
import { Moi } from '../core/auth/moi';

type EtatApi = 'verification' | 'UP' | 'DOWN' | 'injoignable';

/**
 * Temporary home page: a greeting and the API status.
 * Replaced by the dashboard in a later step.
 */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, ButtonModule, Apparition],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly moi = inject(Moi);
  protected readonly prenom = computed(() => this.moi.fiche()?.prenom ?? this.auth.profil()?.nomUtilisateur ?? '');
  protected readonly apiUrl = environment.apiUrl;
  protected readonly etat = signal<EtatApi>('verification');
  protected readonly aujourdhui = new Date();

  constructor() {
    this.verifier();
  }

  protected verifier(): void {
    this.etat.set('verification');
    this.http.get<{ status: 'UP' | 'DOWN' }>(`${this.apiUrl}/actuator/health`).subscribe({
      next: r => this.etat.set(r.status),
      error: () => this.etat.set('injoignable'),
    });
  }
}
