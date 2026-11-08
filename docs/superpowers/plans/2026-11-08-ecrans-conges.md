# Écrans de congés (étapes 14a et 14b) : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Demander un congé avec son calcul exact, suivre ses demandes, et décider (manager, RH) depuis le front-end.

**Architecture:** Nouvelles pages Angular autonomes sous `src/app/conges/`, branchées sur l'API existante (`/api/conges/...`, `/api/types-conge`). Les règles restent côté API ; le front montre le calcul (jours ouvrables, solde après) et masque les actions impossibles. Style : système Linear existant (`.entete`, `.card`, `.ligne`, `.status`, `.lecture`).

**Tech Stack:** Angular 21, PrimeNG 21 (Select, DatePicker, Textarea, Table, Paginator), Vitest.

**Spec :** l'utilisateur a délégué les choix (« you choose, and start »). Décisions ci-dessous ; intention du produit dans `docs/superpowers/specs/2026-11-08-systeme-de-conception-linear-design.md` (signature : « chaque chiffre montre son calcul »).

## Décisions (prises par Claude, déléguées par l'utilisateur)

- Routes (anglais) : `/leave-requests` (Mes congés), `/leave-requests/new`, `/leave-requests/:id` (déjà utilisé par les notifications du back-end), `/approvals` (manager, RH, admin).
- Navigation : groupe « Congés » avec « Mes congés » (tous) et « À décider » (MANAGER, RH, ADMIN).
- Le formulaire montre la preuve : jours calendaires, jours ouvrables (API `jours-ouvrables`), solde actuel → solde après (types déduits du solde), et qui décide (le manager de la fiche, sinon les RH).
- Détail : statut, dates, preuve, décision ou transmission aux RH ; actions selon le rôle et l'état : annuler (propriétaire : en attente, ou approuvée non commencée), approuver (commentaire facultatif), refuser (motif obligatoire), interrompre (RH, admin : approuvée en cours, motif obligatoire). L'API reste juge : son message d'erreur est affiché.
- « À décider » : onglets « À décider » (`/a-decider`), « Équipe » (`/equipe`), « Toutes » (RH, admin : liste globale).
- Accueil : soldes, trois dernières demandes, et pour les décideurs le nombre de demandes à décider ; l'état du serveur devient une ligne discrète.
- Zone de travail : la feuille inset est conservée.

## Global Constraints

- Interface en français, chemins en anglais ; pas de changement d'API.
- Dates envoyées en `yyyy-MM-dd` **locales** (jamais `toISOString`, qui décale d'un jour selon le fuseau).
- Chiffres calculés en `.lecture` (mono) ; statuts en point + mot (`statutDemande`).
- Commits en français, sans mention d'IA.

## Review Focus

1. Fuseau horaire : une date choisie le 1er reste le 1er dans la requête.
2. Dates incomplètes ou fin avant début : pas d'appel au calcul, message clair.
3. Solde insuffisant : signalé avant l'envoi, l'API garde le dernier mot.
4. Employé sans manager : « Les RH décident ».
5. Double clic sur une action : un seul envoi (bouton occupé).

---

### Task 1: API, modèles et règles d'affichage
- Create `src/app/conges/regles.ts` : `dateIso(d: Date): string`, `peutAnnuler(d: DemandeConge, aujourdhui: string): boolean`, `peutInterrompre(d: DemandeConge, aujourdhui: string): boolean`, `soldeApres(s: Solde, jours: number): number`.
- Test `regles.spec.ts` (dont fuseau : `dateIso(new Date(2026, 0, 1))` → `'2026-01-01'`).
- Modify `models.ts` (`TypeConge`, `JoursOuvrables`, `DemandeCongeRequest`), `rh-api.ts` (types, jours ouvrables, créer, détail, annuler, valider, refuser, interrompre, mes demandes paginées/filtrées, à décider, équipe, toutes).

### Task 2: Routes et navigation
- Routes ci-dessus, `/approvals` gardée par `roleGuard('MANAGER','RH','ADMIN')`. Groupe « Congés » dans le rail.
- Test : un employé voit « Mes congés » mais pas « À décider » ; `/approvals` le renvoie à l'accueil.

### Task 3: Mes congés (`/leave-requests`)
- Soldes de l'année + liste paginée filtrable (statut), lien vers le détail, action « Annuler » quand `peutAnnuler`.
- Test : la liste appelle `/moi` avec le filtre ; l'annulation appelle `/annulation` puis recharge.

### Task 4: Nouvelle demande (`/leave-requests/new`)
- Type, début, fin, commentaire ; preuve recalculée à chaque changement de dates ; envoi → détail.
- Tests : calcul demandé avec des dates locales ; pas d'appel si fin < début ; solde après affiché ; envoi du bon corps.

### Task 5: Détail d'une demande (`/leave-requests/:id`)
- Tests : refus impossible sans motif ; approbation envoie le commentaire ; annulation visible seulement si `peutAnnuler`.

### Task 6: À décider (`/approvals`)
- Onglets, décision rapide depuis la liste (approuver ; refuser ouvre le motif).
- Test : l'onglet « Toutes » n'existe que pour RH/admin.

### Task 7: Accueil
- Soldes, trois dernières demandes, compteur à décider (décideurs). Test : le compteur n'est demandé que pour les décideurs.

### Task 8: Vérification
- Captures des écrans (deux thèmes, 1440 et 390), revue finale de la branche, PR.
