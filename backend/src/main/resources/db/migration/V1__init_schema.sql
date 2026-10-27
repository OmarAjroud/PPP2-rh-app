-- Rôles applicatifs : ADMIN, RH, MANAGER, EMPLOYE
CREATE TABLE roles (
                       id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                       nom         VARCHAR(50)  NOT NULL UNIQUE,
                       description VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE departements (
                              id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                              nom         VARCHAR(100) NOT NULL UNIQUE,
                              description VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE employes (
                          id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                          nom            VARCHAR(100)  NOT NULL,
                          prenom         VARCHAR(100)  NOT NULL,
                          email          VARCHAR(150)  NOT NULL UNIQUE,
                          date_embauche  DATE          NOT NULL,
                          salaire        DECIMAL(10,2),
                          departement_id BIGINT        NOT NULL,
                          role_id        BIGINT        NOT NULL,
                          manager_id     BIGINT        NULL,
                          CONSTRAINT fk_employe_departement FOREIGN KEY (departement_id) REFERENCES departements(id),
                          CONSTRAINT fk_employe_role        FOREIGN KEY (role_id)        REFERENCES roles(id),
                          CONSTRAINT fk_employe_manager     FOREIGN KEY (manager_id)     REFERENCES employes(id)
) ENGINE=InnoDB;

CREATE TABLE utilisateurs (
                              id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                              employe_id        BIGINT       NOT NULL UNIQUE,
                              nom_utilisateur   VARCHAR(100) NOT NULL UNIQUE,
                              mot_de_passe_hash VARCHAR(255) NOT NULL,
                              actif             BOOLEAN      NOT NULL DEFAULT TRUE,
                              CONSTRAINT fk_utilisateur_employe FOREIGN KEY (employe_id) REFERENCES employes(id)
) ENGINE=InnoDB;