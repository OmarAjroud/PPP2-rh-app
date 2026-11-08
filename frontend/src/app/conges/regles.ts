import { DemandeConge, Solde } from '../core/api/models';

/** "yyyy-MM-dd" of the local calendar day. Never toISOString(): it converts to UTC and can shift the day. */
export function dateIso(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const jj = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${jj}`;
}

/** Mirrors the API: pending, or approved as long as the leave has not started (days are given back). */
export function peutAnnuler(d: DemandeConge, aujourdhui: string): boolean {
  return d.statut === 'EN_ATTENTE' || (d.statut === 'APPROUVEE' && d.dateDebut > aujourdhui);
}

/** Mirrors the API (RH, ADMIN): an approved leave that is not over yet. */
export function peutInterrompre(d: DemandeConge, aujourdhui: string): boolean {
  return d.statut === 'APPROUVEE' && d.dateFin >= aujourdhui;
}

/** What would remain of a balance after taking these days. */
export function soldeApres(s: Solde, jours: number): number {
  return s.joursDisponibles - jours;
}
