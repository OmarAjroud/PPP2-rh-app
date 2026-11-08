import { Component, input } from '@angular/core';
import { Solde } from '../core/api/models';
import { Compteur } from '../core/motion/motion';

/** Balances of the year as readings: "19 → 15" while requests are pending, else the available days. */
@Component({
  selector: 'app-soldes',
  imports: [Compteur],
  template: `
    <section class="card" aria-labelledby="soldes-titre">
      <h2 id="soldes-titre" class="card-h">Soldes {{ soldes()[0]?.annee ?? '' }}</h2>
      @for (s of soldes(); track s.typeCongeId) {
        @let l = lecture(s);
        <div class="ligne">
          <span>{{ s.typeCongeLibelle }}<small>{{ s.joursAlloues }} alloués · {{ s.joursPris }} pris@if (s.joursEnAttente > 0) { · {{ s.joursEnAttente }} en attente }</small></span>
          <span class="lecture" [attr.aria-label]="l.avant !== null ? l.avant + ' jours, puis ' + l.apres + ' après les demandes en attente' : l.apres + ' jours disponibles'">
            @if (l.avant !== null) { {{ l.avant }}<span class="fleche" aria-hidden="true">→</span> }<span [rhCompteur]="l.apres" aria-hidden="true"></span>
          </span>
        </div>
      } @empty {
        <p class="vide hint">Aucun solde pour cette année.</p>
      }
    </section>
  `,
  styles: `.vide { padding: var(--rh-space-5); }`,
})
export class SoldesPanel {
  readonly soldes = input.required<Solde[]>();

  protected lecture(s: Solde): { avant: number | null; apres: number } {
    return s.joursEnAttente > 0
      ? { avant: s.joursAlloues - s.joursPris, apres: s.joursDisponibles }
      : { avant: null, apres: s.joursDisponibles };
  }
}
