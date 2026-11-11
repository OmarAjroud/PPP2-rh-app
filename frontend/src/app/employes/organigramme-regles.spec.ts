import { NoeudOrganigramme } from '../core/api/models';
import { chercher, cheminVers, construireArbre } from './organigramme-regles';

const n = (id: number, prenom: string, nom: string, managerId: number | null, o: Partial<NoeudOrganigramme> = {}): NoeudOrganigramme => ({
  id, nom, prenom, email: `${prenom.toLowerCase()}@rh.com`, roleNom: 'EMPLOYE', departementId: 1, departementNom: 'Informatique', managerId, ...o,
});

// Amine leads Karim and Sara; Karim leads Lina and Youssef; Hédi has no manager
const societe = [
  n(4, 'Lina', 'Gharbi', 3), n(1, 'Amine', 'Ben Salah', null), n(3, 'Karim', 'Mansour', 1),
  n(2, 'Sara', 'Trabelsi', 1), n(5, 'Youssef', 'Ben Ali', 3), n(6, 'Hédi', 'Zouari', null),
];

describe('construireArbre', () => {
  it('puts each person under their manager, the biggest branch first, teams by name', () => {
    const arbre = construireArbre(societe);
    expect(arbre.map(b => b.personne.prenom)).toEqual(['Amine', 'Hédi']);
    expect(arbre[0].equipe.map(b => b.personne.prenom)).toEqual(['Karim', 'Sara']);
    expect(arbre[0].equipe[0].equipe.map(b => b.personne.prenom)).toEqual(['Youssef', 'Lina']); // Ben Ali, Gharbi
  });

  it('counts everyone under a person, not only direct reports', () => {
    const arbre = construireArbre(societe);
    expect(arbre[0].total).toBe(4);
    expect(arbre[0].equipe[0].total).toBe(2);
    expect(arbre[1].total).toBe(0);
  });

  it('puts at the top someone whose manager is unknown (or themself)', () => {
    const arbre = construireArbre([n(1, 'Amine', 'A', 99), n(2, 'Sara', 'B', 2)]);
    expect(arbre.map(b => b.personne.prenom).sort()).toEqual(['Amine', 'Sara']);
  });

  it('survives a loop of managers and shows everyone once', () => {
    const boucle = [n(1, 'A', 'A', 3), n(2, 'B', 'B', 1), n(3, 'C', 'C', 2), n(4, 'D', 'D', 2)];
    const arbre = construireArbre(boucle);
    const vus: number[] = [];
    const parcourir = (b: ReturnType<typeof construireArbre>[number]) => { vus.push(b.personne.id); b.equipe.forEach(parcourir); };
    arbre.forEach(parcourir);
    expect(vus.sort()).toEqual([1, 2, 3, 4]);
    expect(arbre).toHaveLength(1);
    expect(arbre[0].personne.id).toBe(1);   // the loop is broken at its smallest id
  });
});

describe('cheminVers', () => {
  it('lists the managers above someone, from the top down', () => {
    expect(cheminVers(societe, 4)).toEqual([1, 3]);
    expect(cheminVers(societe, 1)).toEqual([]);
  });
  it('stops on a loop', () => {
    expect(cheminVers([n(1, 'A', 'A', 2), n(2, 'B', 'B', 1)], 1).length).toBeLessThanOrEqual(1);
  });
});

describe('chercher', () => {
  it('finds people by first name, last name or email, without caring about accents or case', () => {
    expect(chercher(societe, 'hedi').map(p => p.id)).toEqual([6]);
    expect(chercher(societe, 'BEN').map(p => p.id)).toEqual([5, 1]); // by last name: Ben Ali, then Ben Salah
    expect(chercher(societe, 'karim@').map(p => p.id)).toEqual([3]);
    expect(chercher(societe, ' ')).toEqual([]);
  });
});
