-- Types de congé (la liste peut s'enrichir par une nouvelle migration)
CREATE TABLE types_conge (
                             id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
                             code                VARCHAR(30)  NOT NULL UNIQUE,
                             libelle             VARCHAR(100) NOT NULL,
                             deduit_du_solde     BOOLEAN      NOT NULL,
                             jours_par_an        DECIMAL(5,1) NULL,
                             retroactif_autorise BOOLEAN      NOT NULL
) ENGINE=InnoDB;

-- Solde annuel par employé et par type (créé à la première utilisation)
CREATE TABLE soldes_conge (
                              id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                              employe_id    BIGINT       NOT NULL,
                              type_conge_id BIGINT       NOT NULL,
                              annee         INT          NOT NULL,
                              jours_alloues DECIMAL(5,1) NOT NULL,
                              jours_pris    DECIMAL(5,1) NOT NULL DEFAULT 0,
                              CONSTRAINT uk_solde UNIQUE (employe_id, type_conge_id, annee),
                              CONSTRAINT fk_solde_employe FOREIGN KEY (employe_id)    REFERENCES employes(id),
                              CONSTRAINT fk_solde_type    FOREIGN KEY (type_conge_id) REFERENCES types_conge(id),
                              CONSTRAINT ck_solde_positifs CHECK (jours_alloues >= 0 AND jours_pris >= 0)
) ENGINE=InnoDB;

-- Jours fériés (saisis par RH/ADMIN : les fêtes religieuses changent chaque année)
CREATE TABLE jours_feries (
                              id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                              date_ferie DATE         NOT NULL UNIQUE,
                              libelle    VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE demandes_conge (
                                id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                employe_id     BIGINT       NOT NULL,
                                type_conge_id  BIGINT       NOT NULL,
                                date_debut     DATE         NOT NULL,
                                date_fin       DATE         NOT NULL,
                                nombre_jours   DECIMAL(5,1) NOT NULL,
                                commentaire    VARCHAR(500) NULL,
                                statut         VARCHAR(20)  NOT NULL,
                                date_creation  DATETIME     NOT NULL,
                                decide_par_id  BIGINT       NULL,        -- utilisé à l'étape 8b (validation)
                                date_decision  DATETIME     NULL,
                                motif_decision VARCHAR(500) NULL,
                                version        BIGINT       NOT NULL DEFAULT 0,   -- verrou optimiste : deux actions simultanées ne s'écrasent pas
                                CONSTRAINT fk_demande_employe FOREIGN KEY (employe_id)    REFERENCES employes(id),
                                CONSTRAINT fk_demande_type    FOREIGN KEY (type_conge_id) REFERENCES types_conge(id),
                                CONSTRAINT fk_demande_decideur FOREIGN KEY (decide_par_id) REFERENCES employes(id),
                                CONSTRAINT ck_demande_dates  CHECK (date_fin >= date_debut),
                                CONSTRAINT ck_demande_jours  CHECK (nombre_jours > 0),
                                INDEX idx_demande_employe_dates (employe_id, date_debut, date_fin),
                                INDEX idx_demande_statut (statut)
) ENGINE=InnoDB;

-- Historique des mouvements de solde (alimenté à l'étape 8b)
CREATE TABLE mouvements_solde (
                                  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  solde_id       BIGINT       NOT NULL,
                                  demande_id     BIGINT       NULL,
                                  type_mouvement VARCHAR(20)  NOT NULL,   -- ALLOCATION, DEDUCTION, REMBOURSEMENT, AJUSTEMENT
                                  jours          DECIMAL(5,1) NOT NULL,   -- positif ou négatif
                                  date_mouvement DATETIME     NOT NULL,
                                  commentaire    VARCHAR(255) NULL,
                                  cree_par_id    BIGINT       NULL,
                                  CONSTRAINT fk_mvt_solde   FOREIGN KEY (solde_id)    REFERENCES soldes_conge(id),
                                  CONSTRAINT fk_mvt_demande FOREIGN KEY (demande_id)  REFERENCES demandes_conge(id),
                                  CONSTRAINT fk_mvt_createur FOREIGN KEY (cree_par_id) REFERENCES employes(id),
                                  INDEX idx_mvt_solde (solde_id)
) ENGINE=InnoDB;

-- Données de départ
INSERT INTO types_conge (code, libelle, deduit_du_solde, jours_par_an, retroactif_autorise) VALUES
                                                                                                ('ANNUEL',     'Congé annuel',     TRUE,  22.0, FALSE),   -- 22 jours : valeur à adapter à votre entreprise
                                                                                                ('MALADIE',    'Congé maladie',    FALSE, NULL, TRUE),    -- peut être déclaré après coup
                                                                                                ('SANS_SOLDE', 'Congé sans solde', FALSE, NULL, FALSE);

-- Jours fériés nationaux à date fixe (les fêtes religieuses sont à saisir chaque année par RH/ADMIN)
INSERT INTO jours_feries (date_ferie, libelle) VALUES
                                                   ('2026-01-01', 'Jour de l''An'),
                                                   ('2026-03-20', 'Fête de l''Indépendance'),
                                                   ('2026-04-09', 'Journée des Martyrs'),
                                                   ('2026-05-01', 'Fête du Travail'),
                                                   ('2026-07-25', 'Fête de la République'),
                                                   ('2026-08-13', 'Fête de la Femme'),
                                                   ('2026-10-15', 'Fête de l''Évacuation'),
                                                   ('2026-12-17', 'Fête de la Révolution et de la Jeunesse'),
                                                   ('2027-01-01', 'Jour de l''An'),
                                                   ('2027-03-20', 'Fête de l''Indépendance'),
                                                   ('2027-04-09', 'Journée des Martyrs'),
                                                   ('2027-05-01', 'Fête du Travail'),
                                                   ('2027-07-25', 'Fête de la République'),
                                                   ('2027-08-13', 'Fête de la Femme'),
                                                   ('2027-10-15', 'Fête de l''Évacuation'),
                                                   ('2027-12-17', 'Fête de la Révolution et de la Jeunesse'),
                                                   ('2028-01-01', 'Jour de l''An'),
                                                   ('2028-03-20', 'Fête de l''Indépendance'),
                                                   ('2028-04-09', 'Journée des Martyrs'),
                                                   ('2028-05-01', 'Fête du Travail'),
                                                   ('2028-07-25', 'Fête de la République'),
                                                   ('2028-08-13', 'Fête de la Femme'),
                                                   ('2028-10-15', 'Fête de l''Évacuation'),
                                                   ('2028-12-17', 'Fête de la Révolution et de la Jeunesse');