-- Notifications dans l'application (icône cloche)
CREATE TABLE notifications (
                               id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                               destinataire_id BIGINT       NOT NULL,
                               type            VARCHAR(40)  NOT NULL,     -- CONGE_DEMANDE, CONGE_APPROUVE, CONGE_REFUSE, CONGE_ANNULE
                               titre           VARCHAR(150) NOT NULL,
                               message         VARCHAR(500) NOT NULL,
                               lien            VARCHAR(255) NULL,         -- chemin dans le front-end (ex. /conges/demandes/12)
                               lue             BOOLEAN      NOT NULL DEFAULT FALSE,
                               date_creation   DATETIME     NOT NULL,
                               date_lecture    DATETIME     NULL,
                               CONSTRAINT fk_notification_destinataire FOREIGN KEY (destinataire_id)
                                   REFERENCES employes(id) ON DELETE CASCADE,
                               -- sert au compteur « non lues » (interrogé régulièrement) et à la liste
                               INDEX idx_notification_destinataire (destinataire_id, lue, date_creation)
) ENGINE=InnoDB;
