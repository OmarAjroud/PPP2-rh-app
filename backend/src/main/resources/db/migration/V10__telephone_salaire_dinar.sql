-- Numéro de téléphone de l'employé, et salaires en dinars tunisiens (TND) : 3 décimales (millimes).
-- Le montant ne change pas : seule l'unité affichée change (DT au lieu de €).

ALTER TABLE employes
    ADD COLUMN telephone VARCHAR(30) NULL AFTER email,
    MODIFY COLUMN salaire DECIMAL(11,3);

UPDATE employes SET telephone = '+216 71 234 501' WHERE email = 'admin@rh.com';
UPDATE employes SET telephone = '+216 71 234 502' WHERE email = 'rh@rh.com';
UPDATE employes SET telephone = '+216 22 418 307' WHERE email = 'manager@rh.com';
UPDATE employes SET telephone = '+216 98 562 114' WHERE email = 'employe@rh.com';
