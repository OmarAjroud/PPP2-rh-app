# Tableau de bord et journal d'audit (étape 16) : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Donner aux RH, administrateurs et managers une vue chiffrée (effectif, demandes, embauches, absences, congés, masse salariale), et aux administrateurs le journal des actions sensibles.

**Architecture:** Deux pages sous `src/app/pilotage/` sur l'API existante (`/api/tableau-de-bord`, `/api/admin/audit`). Graphiques en HTML/CSS (pas de bibliothèque) : deux composants réutilisables, barres horizontales et colonnes, une seule série chacun, couleur d'accent, valeur à l'extrémité, info-bulle au survol et au focus, vue tableau.

**Tech Stack:** Angular 21, PrimeNG 21 (Select, DatePicker, Paginator), Vitest. Règles graphiques : skill `dataviz` (barres ≤ 24 px, extrémité arrondie 4 px, base carrée, texte en couleurs de texte, une seule série → pas de légende, vue tableau). Palette validée (`#5e6ad2`, clair et sombre : tout passe).

## Décisions (déléguées par l'utilisateur à Claude)

- Rail : groupe « Pilotage » : « Tableau de bord » (ADMIN, RH, MANAGER), « Journal d'audit » (ADMIN).
- Routes : `/dashboard`, `/audit`.
- Tableau de bord : filtres année (année courante et deux précédentes) et département (ADMIN, RH ; le manager voit son département, imposé par l'API). Indicateurs en tuiles ; effectif par département et congés par type en barres horizontales ; embauches par mois en colonnes (12 mois) ; masse salariale en tableau (ADMIN, RH, quand l'API la fournit).
- Journal d'audit : filtres utilisateur, action, entité, du / au ; 20 lignes par page ; une ligne s'ouvre pour montrer les champs modifiés (avant → après), le détail et l'adresse IP. Libellés d'actions en français.

## Global Constraints

- Interface en français, chemins en anglais ; pas de changement d'API.
- Montants en euros, format français ; chiffres en `.lecture`/mono.
- Dates de filtre en `yyyy-MM-dd` locales (`dateIso`).
- Commits en français, sans mention d'IA.

## Review Focus

1. Manager : pas de filtre département, pas de masse salariale.
2. Valeurs à zéro : pas de barre fantôme ni de division par zéro.
3. Journal : valeurs JSON absentes ou non objets (connexion, suppression) affichées sans erreur.
4. Filtres d'audit dans l'URL (page rechargeable, partageable).
5. Graphiques lisibles sans couleur ni survol (valeurs écrites, vue tableau).

---

### Task 1: API et modèles
### Task 2: Composants graphiques (barres, colonnes) — tests : proportions, zéro, nom accessible, vue tableau
### Task 3: Page tableau de bord — tests : indicateurs, filtres selon le rôle, masse salariale RH seulement
### Task 4: Page journal d'audit — tests : filtres → requête, détail avant/après, valeurs absentes
### Task 5: Routes et navigation — tests d'accès par rôle
### Task 6: Vérification (captures API simulée), revue finale, PR
