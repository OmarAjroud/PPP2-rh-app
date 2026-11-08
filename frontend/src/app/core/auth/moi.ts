import { Injectable, effect, inject, signal } from '@angular/core';
import { RhApi } from '../api/rh-api';
import { EmployeDetail } from '../api/models';
import { AuthService } from './auth.service';

/**
 * The signed-in person's own record (name, department…), loaded once per session.
 * The login profile only carries the username and role.
 */
@Injectable({ providedIn: 'root' })
export class Moi {
  private readonly api = inject(RhApi);
  readonly fiche = signal<EmployeDetail | null>(null);

  constructor() {
    const auth = inject(AuthService);
    effect(() => {
      const employeId = auth.profil()?.employeId;
      this.fiche.set(null);
      if (employeId === undefined) return;
      this.api.monProfil().subscribe({ next: f => this.fiche.set(f), error: () => this.fiche.set(null) });
    });
  }
}
