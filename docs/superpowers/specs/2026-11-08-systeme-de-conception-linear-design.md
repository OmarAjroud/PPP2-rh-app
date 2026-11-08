# Système de conception du front-end : direction « Linear »

Date : 2026-11-08 · Branche : `feature/front-socle` · Point de sauvegarde avant refonte : `28138b2`

## Intention

Le design « Nuit et lumière » (rail bleu marine, cartes flottantes, bleu `#2D5BFF`, Bricolage Grotesque) ne plaît pas à l'utilisateur, qui avait déjà rejeté le design monochrome précédent. Il veut une application **propre, moderne, premium et impressionnante, dans l'esprit de Linear**. L'application sera montrée lors de la soutenance du stage : la première impression compte.

Parmi trois maquettes (Linear sombre, Linear clair, sombre lumineux), l'utilisateur a choisi **A · Linear sombre**, avec un thème clair équivalent, et le halo lumineux de la maquette C **uniquement sur l'écran de connexion**.

Maquettes de référence : `.superpowers/brainstorm/488-1794106133/content/direction.html` (option A) et `direction-a-modes.html` (A sombre et clair côte à côte).

## Objectifs

- Un seul système, deux thèmes : sombre (la signature) et clair (même structure, mêmes rôles de couleur).
- L'effet « premium » vient de la retenue et des détails : densité, filets fins, une seule couleur d'accent, typographie nette, transitions rapides, clavier.
- Jetons en trois couches (primitifs → sémantiques → composants), source unique pour le CSS de l'application **et** le preset PrimeNG.
- Tous les écrans existants passent au nouveau système : coque (rail + barre du haut), connexion et liens de compte, accueil / Mon profil, employés (liste, fiche, formulaire), départements, rôles, sécurité.
- Les futurs écrans (congés, notifications, tableau de bord, assistant) n'ont qu'à utiliser les jetons et les classes partagées.
- `DESIGN.md` réécrit pour décrire ce système (il décrit encore le monochrome rejeté).

## Non-objectifs

- Pas de nouvelles fonctionnalités métier, pas de changement d'API ni de routes.
- Pas de changement de bibliothèque de composants (PrimeNG 21 reste ; PrimeNG 22 exige une licence).
- Pas de Tailwind, pas de mode « unstyled » de PrimeNG.

## Anti-objectifs (échec même si « ça marche »)

- Un tableau de bord d'admin générique (cartes de même taille partout, couleurs vives, ombres lourdes).
- Des effets dans l'application : halo, verre, dégradés sur le texte, animations qui bouclent. Le halo est réservé à la connexion.
- Un thème clair qui ressemble à un autre produit.
- Un contraste insuffisant : le gris discret de Linear doit rester ≥ 4,5:1 pour le texte (WCAG AA), ≥ 3:1 pour les contours de contrôles.

## Contraintes

- Angular 21.2, PrimeNG 21.1.10 (preset Aura via `@primeuix/themes`), GSAP 3.15 déjà installés.
- Interface en français ; chemins d'URL en anglais (inchangés).
- Le thème suit la classe `rh-dark` sur `<html>`, posée par `ThemeService` (Système / Clair / Sombre, mémorisé dans `localStorage`) : ce mécanisme reste.
- Fonctionne à 390 px de large ; accessible au clavier ; `prefers-reduced-motion` respecté.
- Les tests existants (`ng test`) restent verts.

## Approches envisagées

1. **Refaire les jetons, garder la structure (choisie).** Réécrire `design-system/tokens.css` en trois couches, faire pointer le preset PrimeNG sur les mêmes variables CSS, puis adapter la coque et les écrans. Le plus rapide et le plus sûr ; la plupart des écrans lisent déjà les jetons (57 jetons utilisés, 13 couleurs codées en dur hors du thème).
2. PrimeNG en mode « unstyled » avec notre propre CSS pour chaque composant : contrôle au pixel près, mais plusieurs fois le travail, pour une différence visible faible.
3. Notre propre couche de composants, PrimeNG seulement pour les composants complexes : le plus sur mesure, mais duplique PrimeNG et le plus risqué avant la soutenance.

L'utilisateur a choisi l'approche 1 : l'apparence de Linear tient surtout à la couleur, la densité, la typographie et les filets, que les jetons couvrent.

## Décisions de l'utilisateur

- Direction A « Linear sombre » ; thème clair équivalent ; bascule Système / Clair / Sombre conservée.
- Halo lumineux (violet, maquette C) uniquement sur l'écran de connexion.
- Approche 1.
- **Pas de palette de commandes.** Ctrl K garde son rôle actuel (placer le curseur dans la recherche d'employés, pour les rôles qui la voient).

## Décisions de Claude (à vérifier à la lecture)

- **Valeur par défaut du thème : Système** (comme aujourd'hui).
- **Typographie : Inter** pour tout (titres et texte), **JetBrains Mono** seulement pour les chiffres calculés (soldes, nombres de jours, montants). Bricolage Grotesque et Hanken Grotesk sont retirés. Polices auto-hébergées via `@fontsource` (comme aujourd'hui).
- **Aucune donnée inventée.** Les maquettes montrent un statut d'employé, des puces de filtre avec compteurs et des compteurs dans le rail ; l'API n'a pas de statut d'employé et les écrans de congés n'existent pas encore. La liste des employés garde donc ses colonnes et ses filtres réels (recherche, Département, Rôle), restylés en contrôles compacts à la manière de Linear ; pas de colonne Statut, pas de compteurs dans le rail.
- **En-têtes par page, pas de barre globale.** Chaque page garde son `header.entete`, restylé en barre fine : fil d'Ariane et titre `h1` (20–24 px, reste le titre de la page pour l'accessibilité) à gauche, action principale à droite. Pas de nouvelle barre au niveau de la coque.
- **Connexion toujours sombre.** Tous les écrans de `auth-layout` (connexion, mot de passe oublié, activation, réinitialisation) restent sombres avec le halo, quel que soit le thème choisi ; le bouton principal blanc fonctionne donc partout. Après connexion, l'application suit le thème choisi.
- Les corrections de la revue précédente encore valables sont intégrées : hauteur unique des contrôles, filets de tableau en sombre, tableau découpé au rayon du conteneur, lignes empilées sous 600 px, titre du rôle en français (« Administrateur »), effectifs en chiffres mono, alignement des actions d'en-tête, pas de petits libellés au-dessus des titres.

## Le système

### Couleur (valeurs de départ, ajustées pour le contraste)

Rôles sémantiques identiques dans les deux thèmes ; seules les valeurs changent.

| Rôle | Sombre | Clair |
|---|---|---|
| Fond de l'application (rail) | `#08090A` | `#F7F7F8` |
| Zone de travail | `#0F1011` | `#FFFFFF` |
| Surface relevée (survol, champ, puce active) | `#141516` / `#1C1D1F` | `#F0F0F2` / `#EBEBED` |
| Filet | `#1C1D1F` (lignes de tableau `#18191B`) | `#ECECEE` |
| Contour de contrôle | `#23252A` | `#E3E3E6` |
| Texte principal | `#F7F8F8` | `#1B1C1E` |
| Texte secondaire | `#D0D6E0` | `#3C3E44` |
| Texte discret | `#8A8F98` | `#6B6D73` |
| Accent (action, sélection, focus, liens) | `#5E6AD2` (texte d'accent éclairci si besoin pour 4,5:1) | `#5E6AD2` |

Statuts : un point + un mot, sans pastille colorée en fond (vert = actif / approuvée, ambre = en congé / en attente, rouge = refusée / erreur, gris = inactif / annulée). Aucun statut n'est porté par la couleur seule.

Profondeur : pas d'ombre sur les surfaces de travail (les filets séparent) ; ombre seulement sur les couches flottantes (menus, dialogues, palette).

### Typographie

Inter, corps 13 px (densité Linear), libellés et navigation 13 px / 500, titres de page 20–24 px / 600 avec un léger interlettrage négatif, en-têtes de colonnes 12 px discrets (pas de majuscules). Taille minimale de texte fonctionnel : 11 px. Chiffres tabulaires partout.

### Forme et espace

Grille de 4 px. Rayons : 6 px (contrôles, éléments de navigation, puces), 8 px (panneaux), 12 px (dialogues, palette). Hauteur des contrôles : 32 px par défaut (densité Linear), cibles tactiles ≥ 44 px sous 600 px.

### Mise en page

- Rail de 220–240 px sur le fond de l'application, sans bordure lourde : marque, recherche d'employés (Ctrl K, comportement actuel), navigation groupée, bloc utilisateur en bas.
- Zone de travail : en-tête de page en barre fine (fil d'Ariane + titre à gauche, action principale à droite), puis filtres compacts, puis contenu.
- Les listes sont des lignes denses séparées par des filets, pas des cartes.
- Sous 900 px, le rail devient un tiroir.

### Mouvement

Transitions d'état 120–160 ms, ease-out. À l'arrivée sur une page : léger fondu, une fois. Les compteurs animés actuels sont conservés seulement s'ils restent sobres. Rien ne boucle. Tout est coupé sous `prefers-reduced-motion`.

### Écran de connexion (seule exception)

Fond quasi noir avec un halo violet diffus en haut, formulaire centré sur un panneau à filet fin et reflet intérieur, bouton principal blanc. Même typographie et mêmes jetons qu'ailleurs (valeurs du thème sombre, imposées sur `auth-layout` quel que soit le thème).

### Architecture des jetons

- `design-system/tokens.css` : couche 1 primitifs (`--rh-gray-*`, `--rh-indigo-*`, tailles, rayons, durées) ; couche 2 sémantiques (`--rh-bg`, `--rh-surface`, `--rh-ink`, `--rh-line`, `--rh-accent`…), redéfinis sous `:root.rh-dark` ; couche 3 composants (`--rh-rail-*`, `--rh-row-*`, `--rh-field-*`, `--rh-control-height`…).
- `core/theme/rh-preset.ts` : les jetons sémantiques de PrimeNG pointent vers `var(--rh-…)` au lieu de copier les valeurs. Là où PrimeNG exige une vraie palette (dérivation de nuances), le preset garde des valeurs, documentées comme miroir de `tokens.css`.
- `styles.scss` : motifs partagés (en-tête de page, lignes, fiche, statuts, champs, boutons, tableaux) réécrits sur les nouveaux jetons.
- Les 13 couleurs codées en dur dans les composants passent aux jetons.

## Laissé au constructeur

- Les valeurs exactes des nuances et le contrôle de contraste de chaque paire (outil de contraste, puis ajustement).
- Le découpage du travail et l'ordre des écrans.
- La vérification visuelle (captures Playwright des deux thèmes, à 1440 px et 390 px) et la revue finale avant de déclarer terminé.
