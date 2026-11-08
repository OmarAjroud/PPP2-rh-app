import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { environment } from '../../environments/environment';

type EtatApi = 'verification' | 'UP' | 'DOWN' | 'injoignable';

/**
 * Temporary home page (step 13a): proves the setup works end to end
 * (API reachable over CORS, French locale, themed Material components).
 * Replaced by the dashboard in a later step.
 */
@Component({
  selector: 'app-accueil',
  imports: [DatePipe, MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatInputModule],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly nom = computed(() => this.auth.profil()?.nomUtilisateur ?? '');
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
