import { DemandeConge, Solde } from '../core/api/models';
import { dateIso, peutAnnuler, peutInterrompre, soldeApres } from './regles';

const demande = (d: Partial<DemandeConge>): DemandeConge => ({
  id: 1, employeId: 4, employeNomComplet: 'Lina Gharbi', employeDepartementNom: 'Informatique',
  typeCongeId: 1, typeCongeCode: 'CP', typeCongeLibelle: 'Congé annuel',
  dateDebut: '2026-12-14', dateFin: '2026-12-18', nombreJours: 5, commentaire: null,
  statut: 'EN_ATTENTE', dateCreation: '2026-11-08T10:00:00', decideParNomComplet: null,
  dateDecision: null, motifDecision: null, dateEscalade: null, motifInterruption: null, ...d,
});

describe('dateIso', () => {
  it('writes the local calendar day, whatever the time zone', () => {
    expect(dateIso(new Date(2026, 0, 1))).toBe('2026-01-01');
    expect(dateIso(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31');
  });
});

describe('peutAnnuler', () => {
  it('allows a pending request', () => expect(peutAnnuler(demande({ statut: 'EN_ATTENTE' }), '2026-11-08')).toBe(true));
  it('allows an approved leave that has not started', () =>
    expect(peutAnnuler(demande({ statut: 'APPROUVEE', dateDebut: '2026-11-09' }), '2026-11-08')).toBe(true));
  it('refuses an approved leave that has started', () =>
    expect(peutAnnuler(demande({ statut: 'APPROUVEE', dateDebut: '2026-11-08' }), '2026-11-08')).toBe(false));
  it('refuses refused or cancelled requests', () => {
    expect(peutAnnuler(demande({ statut: 'REFUSEE' }), '2026-11-08')).toBe(false);
    expect(peutAnnuler(demande({ statut: 'ANNULEE' }), '2026-11-08')).toBe(false);
  });
});

describe('peutInterrompre', () => {
  it('allows an approved leave that is not over', () =>
    expect(peutInterrompre(demande({ statut: 'APPROUVEE', dateDebut: '2026-11-02', dateFin: '2026-11-08' }), '2026-11-08')).toBe(true));
  it('refuses a finished leave', () =>
    expect(peutInterrompre(demande({ statut: 'APPROUVEE', dateFin: '2026-11-07' }), '2026-11-08')).toBe(false));
  it('refuses a request that is not approved', () =>
    expect(peutInterrompre(demande({ statut: 'EN_ATTENTE' }), '2026-11-08')).toBe(false));
});

describe('soldeApres', () => {
  it('subtracts the requested days from what is available', () => {
    const s = { joursDisponibles: 22 } as Solde;
    expect(soldeApres(s, 4)).toBe(18);
    expect(soldeApres(s, 2.5)).toBe(19.5);
  });
});
