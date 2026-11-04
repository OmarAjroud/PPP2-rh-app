-- Reprise : chaque solde existant reçoit son mouvement d'allocation initiale
-- (l'historique doit toujours totaliser « alloué - pris »)
INSERT INTO mouvements_solde (solde_id, type_mouvement, jours, date_mouvement, commentaire)
SELECT s.id, 'ALLOCATION', s.jours_alloues - s.jours_pris, NOW(), 'Allocation initiale (reprise des données)'
FROM soldes_conge s
WHERE NOT EXISTS (SELECT 1 FROM mouvements_solde m WHERE m.solde_id = s.id);