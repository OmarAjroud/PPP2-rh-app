-- Un compte peut exister sans mot de passe tant que l'employé ne l'a pas activé
ALTER TABLE utilisateurs MODIFY COLUMN mot_de_passe_hash VARCHAR(255) NULL;

-- Incrémentée à chaque changement de mot de passe : invalide tous les jetons JWT déjà émis
ALTER TABLE utilisateurs ADD COLUMN version_jeton INT NOT NULL DEFAULT 0;

-- Jetons à usage unique : activation de compte et réinitialisation de mot de passe
CREATE TABLE jetons_utilisateur (
                                    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    utilisateur_id   BIGINT       NOT NULL,
                                    type_jeton       VARCHAR(20)  NOT NULL,
                                    jeton_hash       VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 du jeton (jamais le jeton lui-même)
                                    date_creation    DATETIME     NOT NULL,
                                    date_expiration  DATETIME     NOT NULL,
                                    date_utilisation DATETIME     NULL,
                                    CONSTRAINT fk_jeton_utilisateur FOREIGN KEY (utilisateur_id)
                                        REFERENCES utilisateurs(id) ON DELETE CASCADE,
                                    INDEX idx_jeton_utilisateur (utilisateur_id)
) ENGINE=InnoDB;