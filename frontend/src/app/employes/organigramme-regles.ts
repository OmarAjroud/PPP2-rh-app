import { NoeudOrganigramme } from '../core/api/models';

/** A person and everyone under them. total: all the people below, at every level. */
export interface Branche {
  personne: NoeudOrganigramme;
  equipe: Branche[];
  total: number;
}

const parNom = (a: NoeudOrganigramme, b: NoeudOrganigramme) =>
  a.nom.localeCompare(b.nom, 'fr') || a.prenom.localeCompare(b.prenom, 'fr') || a.id - b.id;

/**
 * Each person's manager in the chart, or null for the top of a branch: no manager, an unknown manager,
 * oneself, or a loop of managers (A → B → A, from bad data), broken at the smallest id of the loop.
 */
function managers(noeuds: NoeudOrganigramme[]): Map<number, number | null> {
  const ids = new Set(noeuds.map(p => p.id));
  const parent = new Map<number, number | null>();
  for (const p of noeuds) {
    parent.set(p.id, p.managerId !== null && p.managerId !== p.id && ids.has(p.managerId) ? p.managerId : null);
  }
  const sansBoucle = new Set<number>();
  for (const p of noeuds) {
    const chemin: number[] = [];
    const surChemin = new Set<number>();
    let courant: number | null = p.id;
    while (courant !== null && !sansBoucle.has(courant)) {
      if (surChemin.has(courant)) {
        const boucle = chemin.slice(chemin.indexOf(courant));
        parent.set(Math.min(...boucle), null);
        break;
      }
      surChemin.add(courant);
      chemin.push(courant);
      courant = parent.get(courant) ?? null;
    }
    chemin.forEach(id => sansBoucle.add(id));
  }
  return parent;
}

/** The org chart as trees: the biggest branch first, each team sorted by name. */
export function construireArbre(noeuds: NoeudOrganigramme[]): Branche[] {
  const parent = managers(noeuds);
  const enfants = new Map<number | null, NoeudOrganigramme[]>();
  for (const p of noeuds) {
    const m = parent.get(p.id) ?? null;
    const liste = enfants.get(m);
    if (liste) liste.push(p); else enfants.set(m, [p]);
  }
  const branche = (p: NoeudOrganigramme): Branche => {
    const equipe = (enfants.get(p.id) ?? []).sort(parNom).map(branche);
    return { personne: p, equipe, total: equipe.reduce((t, b) => t + 1 + b.total, 0) };
  };
  return (enfants.get(null) ?? []).sort(parNom).map(branche)
    .sort((a, b) => b.total - a.total || parNom(a.personne, b.personne));
}

/** The managers above someone, from the top down (to open the way to them). */
export function cheminVers(noeuds: NoeudOrganigramme[], id: number): number[] {
  const parent = managers(noeuds);
  const chemin: number[] = [];
  let courant = parent.get(id) ?? null;
  while (courant !== null && !chemin.includes(courant)) {
    chemin.unshift(courant);
    courant = parent.get(courant) ?? null;
  }
  return chemin;
}

const simplifier = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

/** People whose first name, last name or email contains the text (accents and case ignored), by name. */
export function chercher(noeuds: NoeudOrganigramme[], texte: string): NoeudOrganigramme[] {
  const q = simplifier(texte);
  if (!q) return [];
  return noeuds
    .filter(p => [p.prenom, p.nom, `${p.prenom} ${p.nom}`, `${p.nom} ${p.prenom}`, p.email].some(v => simplifier(v).includes(q)))
    .sort(parNom);
}
