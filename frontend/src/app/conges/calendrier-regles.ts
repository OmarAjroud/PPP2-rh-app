import { DemandeConge, StatutDemande } from '../core/api/models';

export interface JourCalendrier {
  jour: number;
  iso: string;
  weekend: boolean;
  ferie: boolean;
  /** People off that day (approved or pending). */
  absents: number;
}

export interface Barre {
  id: number;
  /** First and last day of the month covered (1-based, inclusive). */
  debut: number;
  fin: number;
  statut: StatutDemande;
  type: string;
  /** The leave started before this month / ends after it. */
  coupeAvant: boolean;
  coupeApres: boolean;
  dateDebut: string;
  dateFin: string;
}

export interface LigneCalendrier {
  employeId: number;
  nom: string;
  departement: string;
  barres: Barre[];
}

export interface Mois {
  annee: number;
  mois: number;
  jours: JourCalendrier[];
  lignes: LigneCalendrier[];
}

/** Statuses shown on a calendar: what is or may be an absence. */
const VISIBLES: StatutDemande[] = ['APPROUVEE', 'EN_ATTENTE'];

const deux = (n: number) => String(n).padStart(2, '0');

/**
 * A month as a grid: every day (weekends and public holidays marked, people off counted) and one row per
 * person with leave that month, each leave as a bar cut to the month. Refused and cancelled requests are left out.
 * mois: 1 (January) to 12. Dates are compared as yyyy-MM-dd strings (no time zone involved).
 */
export function construireMois(demandes: DemandeConge[], annee: number, mois: number, feries: string[]): Mois {
  const nbJours = new Date(annee, mois, 0).getDate();
  const premier = `${annee}-${deux(mois)}-01`;
  const dernier = `${annee}-${deux(mois)}-${deux(nbJours)}`;
  const ferieSet = new Set(feries);

  const jours: JourCalendrier[] = Array.from({ length: nbJours }, (_, i) => {
    const iso = `${annee}-${deux(mois)}-${deux(i + 1)}`;
    const jourSemaine = new Date(annee, mois - 1, i + 1).getDay();
    return { jour: i + 1, iso, weekend: jourSemaine === 0 || jourSemaine === 6, ferie: ferieSet.has(iso), absents: 0 };
  });

  const parPersonne = new Map<number, LigneCalendrier>();
  const retenues = demandes
    .filter(d => VISIBLES.includes(d.statut) && d.dateDebut <= dernier && d.dateFin >= premier)
    .sort((a, b) => a.dateDebut.localeCompare(b.dateDebut) || a.id - b.id);

  for (const d of retenues) {
    const debutIso = d.dateDebut < premier ? premier : d.dateDebut;
    const finIso = d.dateFin > dernier ? dernier : d.dateFin;
    const barre: Barre = {
      id: d.id,
      debut: Number(debutIso.slice(8, 10)),
      fin: Number(finIso.slice(8, 10)),
      statut: d.statut,
      type: d.typeCongeLibelle,
      coupeAvant: d.dateDebut < premier,
      coupeApres: d.dateFin > dernier,
      dateDebut: d.dateDebut,
      dateFin: d.dateFin,
    };
    let ligne = parPersonne.get(d.employeId);
    if (!ligne) {
      ligne = { employeId: d.employeId, nom: d.employeNomComplet, departement: d.employeDepartementNom, barres: [] };
      parPersonne.set(d.employeId, ligne);
    }
    ligne.barres.push(barre);
  }

  // People off per day: a person counts once per day even with two overlapping requests
  for (const ligne of parPersonne.values()) {
    const vus = new Set<number>();
    for (const b of ligne.barres) for (let j = b.debut; j <= b.fin; j++) vus.add(j);
    for (const j of vus) jours[j - 1].absents++;
  }

  const lignes = [...parPersonne.values()].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'));
  return { annee, mois, jours, lignes };
}
