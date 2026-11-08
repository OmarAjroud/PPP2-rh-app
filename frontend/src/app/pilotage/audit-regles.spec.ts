import { actionConnue, differences, libelleAction } from './audit-regles';

describe('differences', () => {
  it('lists only the fields that changed, before → after', () => {
    expect(differences({ nom: 'Gharbi', salaire: 2500, email: 'a@rh.com' }, { nom: 'Gharbi', salaire: 2700, email: 'b@rh.com' })).toEqual([
      { champ: 'email', avant: 'a@rh.com', apres: 'b@rh.com' },
      { champ: 'salaire', avant: '2500', apres: '2700' },
    ]);
  });

  it('shows a creation (no before) and a deletion (no after)', () => {
    expect(differences(null, { nom: 'Finance' })).toEqual([{ champ: 'nom', avant: null, apres: 'Finance' }]);
    expect(differences({ nom: 'Finance' }, null)).toEqual([{ champ: 'nom', avant: 'Finance', apres: null }]);
  });

  it('copes with missing or non-object values', () => {
    expect(differences(null, null)).toEqual([]);
    expect(differences('texte', 42)).toEqual([{ champ: 'valeur', avant: 'texte', apres: '42' }]);
  });

  it('writes nested values as JSON', () => {
    expect(differences({ manager: { id: 1 } }, { manager: { id: 2 } })).toEqual([{ champ: 'manager', avant: '{"id":1}', apres: '{"id":2}' }]);
  });
});

describe('libelleAction', () => {
  it('says each action in French, and keeps an unknown one readable', () => {
    expect(libelleAction('CONNEXION')).toBe('Connexion');
    expect(libelleAction('ECHEC_CONNEXION')).toBe('Échec de connexion');
    expect(libelleAction('NOUVELLE_ACTION')).toBe('Nouvelle action');
  });

  it('has a French label for every action the backend records', () => {
    const backend = ['CREATION', 'MODIFICATION', 'SUPPRESSION', 'CONNEXION', 'ECHEC_CONNEXION', 'DECONNEXION', 'INVITATION', 'ACTIVATION_COMPTE', 'CHANGEMENT_MOT_DE_PASSE', 'REINITIALISATION_MOT_DE_PASSE', 'APPEL_ASSISTANT', 'DESACTIVATION_COMPTE', 'REACTIVATION_COMPTE'];
    expect(libelleAction('DECONNEXION')).toBe('Déconnexion');
    for (const a of backend) expect(actionConnue(a), a).toBe(true);
  });
});
