INSERT INTO roles (nom, description) VALUES
                                         ('ADMIN',    'Accès complet au système'),
                                         ('RH',       'Responsable des ressources humaines'),
                                         ('MANAGER',  'Responsable d''équipe'),
                                         ('EMPLOYE',  'Employé standard');

INSERT INTO departements (nom, description) VALUES
                                                ('Informatique',        'Développement et infrastructure'),
                                                ('Ressources Humaines', 'Gestion du personnel'),
                                                ('Finance',             'Comptabilité et finance');

INSERT INTO employes (nom, prenom, email, date_embauche, salaire, departement_id, role_id, manager_id) VALUES
                             ('Ben Salah', 'Amine', 'admin@rh.com',   '2022-01-10', 5000.00, 1, 1, NULL),
                             ('Trabelsi',  'Sara',  'rh@rh.com',      '2022-03-01', 3500.00, 2, 2, NULL),
                             ('Mansour',   'Karim', 'manager@rh.com', '2021-06-15', 4200.00, 1, 3, NULL),
                             ('Gharbi',    'Lina',  'employe@rh.com', '2023-09-01', 2500.00, 1, 4, 3);