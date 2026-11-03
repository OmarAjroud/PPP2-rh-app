CREATE TABLE journal_audit (
                               id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                               date_action       DATETIME(3)  NOT NULL,
                               utilisateur_id    BIGINT       NULL,          -- pas de clé étrangère : le journal survit à la suppression d'un compte
                               nom_utilisateur   VARCHAR(100) NOT NULL,
                               action            VARCHAR(40)  NOT NULL,
                               entite            VARCHAR(60)  NOT NULL,
                               entite_id         VARCHAR(64)  NULL,
                               anciennes_valeurs JSON         NULL,
                               nouvelles_valeurs JSON         NULL,
                               details           VARCHAR(500) NULL,
                               adresse_ip        VARCHAR(45)  NULL,
                               INDEX idx_audit_date (date_action),
                               INDEX idx_audit_utilisateur (nom_utilisateur),
                               INDEX idx_audit_action (action),
                               INDEX idx_audit_entite (entite, entite_id)
) ENGINE=InnoDB;