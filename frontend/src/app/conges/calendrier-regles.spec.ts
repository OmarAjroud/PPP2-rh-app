import { DemandeConge } from '../core/api/models';
import { construireMois } from './calendrier-regles';

const d = (o: Partial<DemandeConge>): DemandeConge => ({
  id: 1, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2026-12-14', dateFin: '2026-12-18', nombreJours: 5, commentaire: null,
  statut: 'APPROUVEE', dateCreation: '2026-11-01T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...o,
});

describe('construireMois', () => {
  it('lists every day of the month, with weekends and public holidays marked', () => {
    const m = construireMois([], 2026, 12, ['2026-12-25']);
    expect(m.jours).toHaveLength(31);
    expect(m.jours[0]).toMatchObject({ jour: 1, iso: '2026-12-01', weekend: false, ferie: false });
    expect(m.jours[4]).toMatchObject({ jour: 5, weekend: true });   // Saturday
    expect(m.jours[24]).toMatchObject({ jour: 25, ferie: true });
  });

  it('gives each person a row, with bars placed on their days', () => {
    const m = construireMois([d({}), d({ id: 2, employeId: 5, employeNomComplet: 'Youssef Ben Ali', dateDebut: '2026-12-01', dateFin: '2026-12-02', statut: 'EN_ATTENTE' })], 2026, 12, []);
    expect(m.lignes.map(l => l.nom)).toEqual(['Lina Gharbi', 'Youssef Ben Ali']);
    expect(m.lignes[0].barres[0]).toMatchObject({ id: 1, debut: 14, fin: 18, statut: 'APPROUVEE' });
    expect(m.lignes[1].barres[0]).toMatchObject({ debut: 1, fin: 2, statut: 'EN_ATTENTE' });
  });

  it('cuts a leave that starts before or ends after the month to the month', () => {
    const m = construireMois([d({ dateDebut: '2026-11-25', dateFin: '2026-12-03' })], 2026, 12, []);
    expect(m.lignes[0].barres[0]).toMatchObject({ debut: 1, fin: 3, coupeAvant: true, coupeApres: false });
  });

  it('leaves out refused, cancelled and other-month requests', () => {
    const m = construireMois([
      d({ id: 1, statut: 'REFUSEE' }), d({ id: 2, statut: 'ANNULEE' }),
      d({ id: 3, dateDebut: '2026-11-02', dateFin: '2026-11-06' }),
    ], 2026, 12, []);
    expect(m.lignes).toEqual([]);
  });

  it('keeps one row per person even with several leaves, in date order', () => {
    const m = construireMois([d({ id: 2, dateDebut: '2026-12-21', dateFin: '2026-12-22' }), d({ id: 1, dateDebut: '2026-12-03', dateFin: '2026-12-04' })], 2026, 12, []);
    expect(m.lignes).toHaveLength(1);
    expect(m.lignes[0].barres.map(b => b.id)).toEqual([1, 2]);
  });

  it('counts the people off on a given day', () => {
    const m = construireMois([d({}), d({ id: 2, employeId: 5, employeNomComplet: 'Youssef', dateDebut: '2026-12-15', dateFin: '2026-12-15' })], 2026, 12, []);
    expect(m.jours[14].absents).toBe(2);   // 15 December
    expect(m.jours[13].absents).toBe(1);
  });

  it('puts the reader\'s own row first, marked as theirs, and never counts a request twice', () => {
    const m = construireMois([d({}), d({ id: 2, employeId: 9, employeNomComplet: 'Zied Manager', dateDebut: '2026-12-01', dateFin: '2026-12-02' }), d({})], 2026, 12, [], 9);
    expect(m.lignes.map(l => [l.nom, l.moi])).toEqual([['Zied Manager', true], ['Lina Gharbi', false]]);
    expect(m.lignes[1].barres).toHaveLength(1);
    expect(m.jours[13].absents).toBe(1);
  });
});
