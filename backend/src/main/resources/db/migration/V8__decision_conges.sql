-- Validation des congés : le manager direct décide, les RH en recours.
-- Quand une demande attend trop longtemps sa décision, elle est transmise aux RH une seule fois (date_escalade).
ALTER TABLE demandes_conge ADD COLUMN date_escalade DATETIME NULL;

-- Interruption d'un congé approuvé par les RH (retour anticipé, annulation exceptionnelle) : motif obligatoire
ALTER TABLE demandes_conge ADD COLUMN motif_interruption VARCHAR(500) NULL;

-- Sert à la recherche périodique des demandes en attente depuis longtemps
CREATE INDEX idx_demande_statut_creation ON demandes_conge (statut, date_creation);
