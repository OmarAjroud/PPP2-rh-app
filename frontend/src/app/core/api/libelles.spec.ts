import { optionsRoles } from './libelles';

describe('optionsRoles', () => {
  it('labels system roles in French and keeps custom role names', () => {
    expect(optionsRoles([{ id: 4, nom: 'EMPLOYE', description: null }, { id: 9, nom: 'Stagiaire', description: null }]))
      .toEqual([{ label: 'Employé', value: 4 }, { label: 'Stagiaire', value: 9 }]);
  });
});
