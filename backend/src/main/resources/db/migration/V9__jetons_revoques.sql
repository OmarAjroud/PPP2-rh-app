-- Jetons de rafraîchissement déjà utilisés ou révoqués (déconnexion).
-- Chaque jeton de rafraîchissement ne sert qu'une fois : le présenter à nouveau révèle un vol, et toutes les
-- sessions de l'utilisateur sont alors fermées. Les lignes sont supprimées une fois le jeton expiré.
CREATE TABLE jetons_revoques (
                                 jti            VARCHAR(36)  NOT NULL PRIMARY KEY,   -- identifiant unique du jeton (UUID)
                                 utilisateur_id BIGINT       NOT NULL,
                                 date_expiration DATETIME    NOT NULL,
                                 CONSTRAINT fk_jeton_revoque_utilisateur FOREIGN KEY (utilisateur_id)
                                     REFERENCES utilisateurs(id) ON DELETE CASCADE,
                                 INDEX idx_jeton_revoque_expiration (date_expiration)
) ENGINE=InnoDB;
