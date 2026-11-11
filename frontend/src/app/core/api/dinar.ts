import { Pipe, PipeTransform } from '@angular/core';

const avecMillimes = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
const sansMillimes = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/**
 * An amount in Tunisian dinars, as written in Tunisia: "3 500,000 DT" (3 decimals: millimes),
 * or "62 000 DT" without millimes for totals and averages.
 */
export function dinars(montant: number, millimes = true): string {
  return `${(millimes ? avecMillimes : sansMillimes).format(montant)} DT`;
}

/** {{ salaire | dinars }} or {{ total | dinars: false }} */
@Pipe({ name: 'dinars' })
export class DinarsPipe implements PipeTransform {
  transform(montant: number | null | undefined, millimes = true): string {
    return montant === null || montant === undefined ? '' : dinars(montant, millimes);
  }
}
