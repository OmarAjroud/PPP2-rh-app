import { DemandeConge, Role } from './models';

/** Status of a leave request as shown everywhere: one word and its tint class (DESIGN.md, Status). */
export function statutDemande(d: DemandeConge): { libelle: string; classe: string } {
  if (d.statut === 'EN_ATTENTE' && d.dateEscalade) return { libelle: 'Transmise aux RH', classe: 's-esc' };
  switch (d.statut) {
    case 'EN_ATTENTE': return { libelle: 'En attente', classe: 's-wait' };
    case 'APPROUVEE': return { libelle: 'Approuvée', classe: 's-ok' };
    case 'REFUSEE': return { libelle: 'Refusée', classe: 's-no' };
    case 'ANNULEE': return { libelle: 'Annulée', classe: 's-off' };
  }
}

const MOIS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

/** "14 – 18 déc.", "28 déc. – 2 janv.", "12 mai" (API dates are yyyy-MM-dd, local). */
export function periode(debut: string, fin: string): string {
  const [, m1, j1] = debut.split('-').map(Number);
  const [, m2, j2] = fin.split('-').map(Number);
  if (debut === fin) return `${j1} ${MOIS[m1 - 1]}`;
  return m1 === m2 ? `${j1} – ${j2} ${MOIS[m2 - 1]}` : `${j1} ${MOIS[m1 - 1]} – ${j2} ${MOIS[m2 - 1]}`;
}

/** "1 jour", "4 jours", "1,5 jour". */
export function jours(n: number): string {
  return `${String(n).replace('.', ',')} ${n > 1 ? 'jours' : 'jour'}`;
}

/** Two-letter initials from a first and last name. */
export function initiales(prenom: string, nom: string): string {
  return `${prenom.trim()[0] ?? ''}${nom.trim()[0] ?? ''}`.toUpperCase();
}

const ROLES: Record<string, string> = { ADMIN: 'Administrateur', RH: 'Ressources humaines', MANAGER: 'Manager', EMPLOYE: 'Employé' };

/** French label for the four system roles; custom roles keep their own name. */
export function libelleRole(nom: string): string {
  return ROLES[nom] ?? nom;
}

/** Select options for roles, labelled in French (system roles) or by their own name. */
export function optionsRoles(roles: Role[]): { label: string; value: number }[] {
  return roles.map(r => ({ label: libelleRole(r.nom), value: r.id }));
}
