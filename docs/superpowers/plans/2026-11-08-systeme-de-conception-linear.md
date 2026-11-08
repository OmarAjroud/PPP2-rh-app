# Système de conception « Linear » : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer « Nuit et lumière » par un système sombre/clair dans l'esprit de Linear (direction A), appliqué à tous les écrans existants, avec le halo uniquement sur les écrans de connexion.

**Architecture:** `design-system/tokens.css` devient la source unique en trois couches (primitifs → sémantiques → composants). Le preset PrimeNG ne copie plus les couleurs : ses rôles sémantiques pointent vers `var(--rh-…)`, si bien que basculer les variables suffit à changer de thème, y compris dans un sous-arbre (connexion toujours sombre). Les écrans gardent leur structure ; seuls leurs styles et quelques gabarits changent.

**Tech Stack:** Angular 21.2, PrimeNG 21.1.10 + `@primeuix/themes` 2.0.3 (Aura), GSAP 3.15, Vitest via `@angular/build:unit-test` (jsdom), `@fontsource`.

**Spec:** `docs/superpowers/specs/2026-11-08-systeme-de-conception-linear-design.md`

## Global Constraints

- Tout se passe dans `frontend/` ; commandes lancées depuis `frontend/`.
- Interface en français ; chemins d'URL en anglais, inchangés. Pas de changement d'API ni de routes.
- PrimeNG reste en 21.1.10 (pas de 22 : licence). Pas de Tailwind, pas de mode unstyled.
- Le thème suit la classe `rh-dark` sur `<html>` posée par `ThemeService` (Système / Clair / Sombre) ; ce service ne change pas.
- Polices : Inter (400, 500, 600) pour tout, JetBrains Mono (400, 500) seulement pour les chiffres calculés. Bricolage Grotesque et Hanken Grotesk retirés.
- Accent `#5E6AD2` dans les deux thèmes (texte d'accent éclairci en sombre si nécessaire pour 4,5:1).
- Contraste WCAG AA : texte ≥ 4,5:1, contours de contrôles ≥ 3:1.
- Corps 13 px, texte fonctionnel ≥ 11 px, titres de page 20–24 px / 600, en-têtes de colonnes 12 px sans majuscules.
- Rayons 6 px (contrôles, navigation, puces), 8 px (panneaux), 12 px (dialogues). Contrôles 32 px ; cibles ≥ 44 px sous 600 px.
- Pas d'ombre sur les surfaces de travail ; ombre seulement sur menus, dialogues, listes déroulantes.
- Aucun halo, verre, dégradé sur du texte ni animation en boucle hors des écrans de `auth-layout`.
- Transitions 120–160 ms ease-out ; arrivée de page = léger fondu une fois ; rien sous `prefers-reduced-motion`.
- Pas de données inventées : pas de colonne Statut d'employé, pas de compteurs dans le rail, pas de palette Ctrl K (Ctrl K garde son comportement actuel).
- Messages de commit en français, sans mention d'IA ni ligne `Co-Authored-By` (préférence de l'utilisateur).

## Review Focus

1. **Listes déroulantes et superpositions dans le sous-arbre forcé sombre.** PrimeNG attache ses superpositions à `<body>`, hors de `auth-layout` : un panneau ouvert depuis la connexion en thème clair doit rester lisible. → test dans la tâche 5 (aucun composant à superposition dans `auth-layout`) et vérification visuelle.
2. **Noms très longs** (rail, ligne d'employé, fiche, titre de page) : coupés avec points de suspension ou retour à la ligne, jamais de débordement horizontal à 390 px. → vérifications des tâches 4, 6, 8.
3. **Focus clavier visible dans les deux thèmes**, y compris sur le rail et les lignes de tableau cliquables. → jeton `--rh-focus` vérifié par le script de contraste (tâche 1), contrôle au clavier en tâche 8.
4. **Bascule de thème pendant qu'une page est ouverte** (y compris une liste déroulante ouverte) : toutes les surfaces PrimeNG suivent sans rechargement. → tâche 2, étape de vérification.
5. **Mouvement réduit** : aucune animation GSAP ni transition CSS perceptible. → test dans la tâche 3.

---

## Fichiers

| Fichier | Rôle | Tâche |
|---|---|---|
| `design-system/tokens.css` | Jetons 3 couches, thèmes clair / sombre / sombre forcé | 1 |
| `design-system/verifier-contraste.mjs` (nouveau) | Vérifie les paires de contraste dans les deux thèmes | 1 |
| `package.json`, `angular.json` | Polices, script `check:contraste` | 1 |
| `src/app/core/theme/rh-preset.ts` | Preset Aura → `var(--rh-…)` | 2 |
| `src/styles.scss` | Surcharges PrimeNG + motifs partagés | 2, 3 |
| `src/app/core/motion/motion.ts` | Fondu d'arrivée, durées | 3 |
| `src/app/layout/shell.{html,scss,ts}` + `shell.spec.ts` (nouveau) | Rail Linear, tiroir < 900 px | 4 |
| `src/app/layout/auth-layout.ts` + `auth-layout.spec.ts` (nouveau) | Connexion centrée, toujours sombre, halo | 5 |
| `src/app/employes/*` | Liste, fiche, formulaire | 6 |
| `src/app/{accueil,departements,roles,compte,shared}/*` | Autres écrans | 7 |
| `DESIGN.md` (racine) | Documentation du système | 8 |

---

### Task 1: Jetons en trois couches, polices, contrôle de contraste

**Files:**
- Modify: `design-system/tokens.css` (réécriture complète)
- Create: `design-system/verifier-contraste.mjs`
- Modify: `package.json` (dépendances, script), `angular.json:32-38` (styles)

**Interfaces:**
- Produces (noms utilisés par toutes les tâches suivantes) :
  - Couche 1 : `--rh-gray-0…--rh-gray-1000` (échelle neutre, valeurs de la spec), `--rh-indigo-300…700` (`--rh-indigo-500: #5e6ad2`), `--rh-green-*`, `--rh-amber-*`, `--rh-red-*`.
  - Couche 2 (redéfinie pour le sombre) : `--rh-bg` (fond / rail), `--rh-surface` (zone de travail), `--rh-surface-2` (survol, champ), `--rh-surface-3` (puce active, ligne sélectionnée), `--rh-line`, `--rh-line-row`, `--rh-line-strong` (contour de contrôle), `--rh-ink`, `--rh-ink-2`, `--rh-muted`, `--rh-accent`, `--rh-accent-hover`, `--rh-accent-ink`, `--rh-accent-text`, `--rh-accent-soft`, `--rh-focus`, `--rh-ok`, `--rh-wait`, `--rh-danger`, `--rh-neutral`, `--rh-on-danger`, `--rh-shadow-overlay`, `--rh-scrim`.
  - Couche 3 : `--rh-rail-width: 232px`, `--rh-rail-bg`, `--rh-rail-item-hover`, `--rh-rail-item-active`, `--rh-control-height: 32px`, `--rh-row-height: 40px`, `--rh-header-height: 44px`, `--rh-content-max: 1200px`.
  - Type : `--rh-font` (Inter), `--rh-font-mono`, `--rh-text-xs: 11px`, `--rh-text-sm: 12px`, `--rh-text-md: 13px`, `--rh-text-lg: 15px`, `--rh-title: 22px`, `--rh-figure: 24px`.
  - Forme / mouvement : `--rh-radius-sm: 6px`, `--rh-radius: 8px`, `--rh-radius-lg: 12px`, `--rh-space-1…--rh-space-10` (grille de 4 px, mêmes noms qu'aujourd'hui), `--rh-ease: cubic-bezier(0.25, 0.8, 0.25, 1)`, `--rh-fast: 120ms`, `--rh-base: 160ms`.
  - Sélecteurs de thème : clair sur `:root` ; sombre sur `:root.rh-dark, .rh-theme-sombre` (la seconde classe force le sombre sur un sous-arbre, utilisée en tâche 5).
  - Supprimés : `--rh-rail-ink*`, `--rh-rail-light`, `--rh-rail-accent`, `--rh-font-display`, `--rh-figure-xl`, `--rh-shadow-card`, `--rh-shadow-button`, `--rh-*-soft` des statuts, `--rh-escalated*` (Transmise aux RH = `--rh-neutral` + mot).
  - Script npm : `check:contraste` → `node design-system/verifier-contraste.mjs`, code de sortie 1 en cas d'échec.

- [ ] **Step 1: Écrire le script de contraste (le test)**

`verifier-contraste.mjs` lit `tokens.css`, extrait les déclarations du bloc `:root {` et du bloc `:root.rh-dark, .rh-theme-sombre {` (le sombre hérite du clair pour ce qu'il ne redéfinit pas), résout les `var()` en chaîne, et calcule le rapport WCAG 2.1 (luminance relative) de chaque paire. Les jetons de texte et de fond des paires doivent être des hex opaques (le script échoue avec un message clair sinon). Paires et seuils, dans chaque thème :

```js
const PAIRES = [
  ['--rh-ink', '--rh-surface', 4.5], ['--rh-ink', '--rh-bg', 4.5],
  ['--rh-ink-2', '--rh-surface', 4.5], ['--rh-muted', '--rh-surface', 4.5],
  ['--rh-muted', '--rh-bg', 4.5], ['--rh-muted', '--rh-surface-2', 4.5],
  ['--rh-accent-text', '--rh-surface', 4.5], ['--rh-accent-ink', '--rh-accent', 4.5],
  ['--rh-ok', '--rh-surface', 4.5], ['--rh-wait', '--rh-surface', 4.5],
  ['--rh-danger', '--rh-surface', 4.5], ['--rh-neutral', '--rh-surface', 4.5],
  ['--rh-on-danger', '--rh-danger', 4.5],
  ['--rh-line-strong', '--rh-surface', 3], ['--rh-accent', '--rh-surface', 3],
];
```

Sortie : une ligne par paire (`OK  clair  --rh-muted / --rh-surface  4.84`), `ÉCHEC` en tête de ligne si sous le seuil.

- [ ] **Step 2: Lancer le script sur l'ancien fichier : il doit échouer**

Run: `node design-system/verifier-contraste.mjs`
Expected: sortie non nulle (jetons `--rh-accent-text` / `--rh-line-row` absents, ou bloc `.rh-theme-sombre` introuvable).

- [ ] **Step 3: Réécrire `tokens.css`**

Les trois couches et les noms de l'Interface ci-dessus, avec les valeurs de départ de la spec (tableau « Couleur ») ; ajuster les nuances jusqu'à ce que le script passe. Garder en fin de fichier les règles navigateur existantes (`::selection`, `accent-color`, `:focus-visible { box-shadow: var(--rh-focus) }`, liens en `--rh-accent-text`) et le bloc `prefers-reduced-motion` qui met `--rh-fast` et `--rh-base` à `0ms`. `color-scheme: light` / `dark` dans chaque bloc.

- [ ] **Step 4: Polices**

Run: `npm i @fontsource/inter && npm un @fontsource/bricolage-grotesque @fontsource/hanken-grotesk`
Dans `angular.json` (build), remplacer les lignes Hanken / Bricolage par `@fontsource/inter/400.css`, `500.css`, `600.css`. Ajouter `"check:contraste": "node design-system/verifier-contraste.mjs"` aux scripts.

- [ ] **Step 5: Vérifier**

Run: `npm run check:contraste && npx ng build`
Expected: toutes les lignes `OK` dans les deux thèmes ; le build passe (des avertissements de variables inconnues dans les écrans sont attendus, ils disparaissent aux tâches 2–7).

- [ ] **Step 6: Commit**

```bash
git add design-system package.json package-lock.json angular.json
git commit -m "feat(design): jetons Linear en trois couches, Inter, contrôle du contraste"
```

---

### Task 2: Preset PrimeNG sur les variables, composants PrimeNG au style Linear

**Files:**
- Modify: `src/app/core/theme/rh-preset.ts` (réécriture)
- Modify: `src/styles.scss` (section « PrimeNG » : boutons, champs, tableaux, paginateur, superpositions)
- Modify: `src/app/app.config.ts:24` (`darkModeSelector`)

**Interfaces:**
- Consumes: jetons de la tâche 1.
- Produces: classes PrimeNG prêtes à l'emploi pour les écrans ; boutons : principal = fond `--rh-accent`, texte `--rh-accent-ink` ; `p-button-secondary` / `outlined` = fond `--rh-surface`, contour `--rh-line-strong` ; `p-button-text` = sans fond ; danger = `--rh-danger`.

- [ ] **Step 1: Réécrire le preset**

`definePreset(Aura, …)` : `primitive.borderRadius` = `{ none: '0', xs: '4px', sm: '6px', md: '6px', lg: '8px', xl: '12px' }` ; `semantic.primary` = l'échelle indigo (500 = `#5e6ad2`) ; dans `colorScheme.light` **et** `colorScheme.dark`, les mêmes valeurs `var()` pour `primary` (`var(--rh-accent)`, `var(--rh-accent-ink)`, `var(--rh-accent-hover)`), `highlight` (`var(--rh-surface-3)`, `var(--rh-ink)`), `formField` (fond `var(--rh-surface-2)`, bordure `var(--rh-line-strong)`, survol `var(--rh-muted)`, focus `var(--rh-accent)`, texte `var(--rh-ink)`, placeholder `var(--rh-muted)`), `text`, `content`, `overlay` (fond `var(--rh-surface)`, bordure `var(--rh-line)`, ombre `var(--rh-shadow-overlay)`), `mask` (`var(--rh-scrim)`). `formField.paddingY` / `paddingX` tels que la hauteur rendue soit 32 px. Seules les palettes `surface` gardent des hex (miroir de `--rh-gray-*`, commentaire « miroir de tokens.css »).

- [ ] **Step 2: Sélecteur sombre**

`darkModeSelector: '.rh-dark, .rh-theme-sombre'`.

- [ ] **Step 3: Réécrire la section PrimeNG de `styles.scss`**

Supprimer les dégradés et ombres des boutons ; boutons, champs, `p-select`, `p-datepicker`, `p-password`, `p-inputnumber` à `height: var(--rh-control-height)` et `font-size: var(--rh-text-md)` ; tableau : `th` 12 px `--rh-muted` 500 sans `text-transform`, `td` hauteur `--rh-row-height`, filets `--rh-line-row` (sombre inclus), survol de ligne `--rh-surface-2`, en-tête et corps sur `--rh-surface`, tableau découpé au rayon du conteneur (`overflow: hidden` sur le conteneur arrondi) ; paginateur transparent, 12 px. Sous 600 px : contrôles et boutons `min-height: 44px`.

- [ ] **Step 4: Vérifier**

Run: `npx ng test --watch=false && npx ng build`
Expected: tous les tests passent ; build sans erreur.
Puis `npx ng serve`, ouvrir `/employees` connecté en RH (données de démo), basculer Clair ↔ Sombre avec une liste déroulante ouverte : fond, texte, bordures et panneau de la liste suivent sans rechargement (Review Focus 4).

- [ ] **Step 5: Commit**

```bash
git add src/app/core/theme/rh-preset.ts src/app/app.config.ts src/styles.scss
git commit -m "feat(design): preset PrimeNG branché sur les jetons, contrôles et tableaux au style Linear"
```

---

### Task 3: Motifs partagés et mouvement

**Files:**
- Modify: `src/styles.scss` (sections en-tête, cartes, lignes, fiche, lectures, statuts, tag, alertes, champs)
- Modify: `src/app/core/motion/motion.ts`
- Test: `src/app/core/motion/motion.spec.ts` (nouveau)

**Interfaces:**
- Produces (classes utilisées par les tâches 4–7, mêmes noms qu'aujourd'hui pour limiter les changements de gabarits) :
  - `.entete` : barre fine, `min-height: var(--rh-header-height)`, fil d'Ariane `.crumb` + `h1.page-titre` à gauche (sur une ligne si la place le permet), actions à droite, filet bas `--rh-line`.
  - `.page-titre` : `600 var(--rh-title)/1.2 var(--rh-font)`, `letter-spacing: -0.015em`.
  - `.card` → panneau : fond `--rh-surface`, filet `--rh-line`, rayon `--rh-radius`, **sans ombre**, `overflow: hidden`. `.card-h` / `.card-b` / `.card-f` gardent leur rôle, espacements 12/16 px.
  - `.ligne`, `.fiche` : hauteur `--rh-row-height`, filets `--rh-line-row`.
  - `.status` : point + mot, **sans fond** ; couleurs `.s-ok/.s-wait/.s-no/.s-off/.s-esc` (`.s-esc` = `--rh-neutral`).
  - `.tag` : 11 px, fond `--rh-surface-2`, filet `--rh-line`, rayon `--rh-radius-sm`.
  - `.chiffre` : `600 var(--rh-figure) var(--rh-font-mono)`, chiffres tabulaires. `.lecture`, `.mono` inchangés en rôle.
  - `.alerte.*` : texte coloré + filet de la couleur, fond `--rh-surface-2`.
  - Supprimé : `.auth-*` (déplacé dans `auth-layout`, tâche 5) ; `--rh-font-display` partout.
  - `Apparition` : fondu `autoAlpha: 0 → 1`, `y: 4`, `duration: 0.16`, `stagger: 0.03`, une fois.

- [ ] **Step 1: Écrire les tests de mouvement**

```ts
describe('Apparition', () => {
  it('ne lance aucune animation sous prefers-reduced-motion', () => {
    // matchMedia renvoie matches: true pour '(prefers-reduced-motion: reduce)'
    // monter un hôte <div rhApparition><p></p><p></p></div>
    expect(gsap.from).not.toHaveBeenCalled();
  });
  it('fait un fondu court, une fois', () => {
    // matchMedia renvoie matches: false
    expect(gsap.from).toHaveBeenCalledTimes(1);
    expect(vi.mocked(gsap.from).mock.calls[0][1]).toMatchObject({ autoAlpha: 0, y: 4, duration: 0.16 });
  });
});
```

`gsap.from` espionné avec `vi.spyOn(gsap, 'from')`.

- [ ] **Step 2: Lancer : le second test échoue**

Run: `npx ng test --watch=false --include src/app/core/motion/motion.spec.ts`
Expected: FAIL (`y: 8`, `duration: 0.26`).

- [ ] **Step 3: Mettre à jour `motion.ts` puis réécrire les motifs de `styles.scss`**

Valeurs de l'Interface. Typo de base du `body` : `400 var(--rh-text-md)/1.5 var(--rh-font)`, `font-feature-settings: "cv11", "ss01"` (Inter), chiffres tabulaires sur `td`, `.mono`, `.chiffre`.

- [ ] **Step 4: Vérifier**

Run: `npx ng test --watch=false && npx ng build`
Expected: tous les tests passent (dont `accueil.spec.ts`, qui lit `.status`).

- [ ] **Step 5: Commit**

```bash
git add src/styles.scss src/app/core/motion
git commit -m "feat(design): motifs partagés (en-tête, panneaux, lignes, statuts) et fondu d'arrivée"
```

---

### Task 4: Coque (rail) au style Linear, tiroir sous 900 px

**Files:**
- Modify: `src/app/layout/shell.html`, `shell.scss` (réécriture), `shell.ts`
- Test: `src/app/layout/shell.spec.ts` (nouveau)

**Interfaces:**
- Consumes: jetons tâche 1, motifs tâche 3.
- Produces: `Shell.menuOuvert: WritableSignal<boolean>` ; bouton `button.menu` (`aria-controls="rail"`, `aria-expanded`), visible sous 900 px ; le rail porte `id="rail"` et la classe `ouvert` quand le tiroir est ouvert ; le tiroir se ferme à chaque fin de navigation (`NavigationEnd`) et sur Échap.

Gabarit : marque (carré dégradé indigo 18 px + « RH App » 13 px / 600), recherche d'employés (inchangée fonctionnellement, Ctrl K conservé, hauteur 28 px), groupes en 11 px `--rh-muted` sans majuscules, liens 28–30 px de haut / rayon 6 px / icône 14 px, lien actif fond `--rh-rail-item-active` et texte `--rh-ink` ; bloc utilisateur en bas : avatar 22 px fond `--rh-surface-3` (plus de dégradé ni de hex), nom tronqué, bascule de thème et Déconnexion (texte `--rh-muted`, pas en accent). Le rail est sur `--rh-rail-bg`, la zone de travail sur `--rh-surface` avec un filet gauche `--rh-line`. Sous 900 px : barre du haut 44 px (bouton menu + marque), rail en tiroir fixe à gauche avec voile `--rh-scrim`.

- [ ] **Step 1: Écrire les tests**

```ts
it('ouvre et ferme le tiroir avec le bouton menu', () => {
  const bouton = el.querySelector<HTMLButtonElement>('button.menu')!;
  expect(bouton.getAttribute('aria-expanded')).toBe('false');
  bouton.click(); fixture.detectChanges();
  expect(bouton.getAttribute('aria-expanded')).toBe('true');
  expect(el.querySelector('#rail')!.classList).toContain('ouvert');
});
it('ferme le tiroir après une navigation', async () => {
  // ouvrir, puis router.navigateByUrl('/employees')
  expect(el.querySelector('#rail')!.classList).not.toContain('ouvert');
});
it('ferme le tiroir sur Échap', () => { /* keydown Escape sur document → fermé */ });
```

Monter `Shell` avec `provideRouter([])`, un `AuthService` simulé (profil RH) comme dans `app.spec.ts`.

- [ ] **Step 2: Lancer : échec (`button.menu` absent)**

Run: `npx ng test --watch=false --include src/app/layout/shell.spec.ts`
Expected: FAIL.

- [ ] **Step 3: Implémenter gabarit, styles et `menuOuvert`**

- [ ] **Step 4: Vérifier**

Run: `npx ng test --watch=false`
Expected: tout passe, dont `app.spec.ts` et `roles-acces.spec.ts` (ils lisent `nav[aria-label="Navigation principale"]` : garder ce libellé sur le rail).
Visuel : à 1440 px et 390 px, dans les deux thèmes, un nom d'utilisateur de 40 caractères est tronqué sans débordement (Review Focus 2) ; Tab parcourt le rail avec un focus visible (Review Focus 3).

- [ ] **Step 5: Commit**

```bash
git add src/app/layout/shell.*
git commit -m "feat(design): rail au style Linear, tiroir de navigation sur petit écran"
```

---

### Task 5: Écrans de connexion : centrés, toujours sombres, halo

**Files:**
- Modify: `src/app/layout/auth-layout.ts` (gabarit et styles réécrits)
- Modify: `src/app/auth/connexion.ts`, `mot-de-passe-oublie.ts`, `lien-mot-de-passe.ts` (seulement les classes `auth-*` si leur nom change)
- Test: `src/app/layout/auth-layout.spec.ts` (nouveau)

**Interfaces:**
- Consumes: classe `.rh-theme-sombre` (tâche 1), `darkModeSelector` (tâche 2).
- Produces: le conteneur racine de `AuthLayout` porte `class="auth rh-theme-sombre"` ; styles `.auth-titre`, `.auth-sous-titre`, `.auth-form`, `.auth-liens`, `.auth-pied` déplacés de `styles.scss` vers ce composant (`ViewEncapsulation.None` limité par le préfixe `.auth`).

Gabarit : fond `--rh-bg` (sombre) avec halo violet diffus en haut (radial-gradient indigo/violet à faible opacité, statique), marque centrée au-dessus, panneau de 400 px max (fond `--rh-surface` légèrement translucide, filet `--rh-line`, reflet intérieur `inset 0 1px 0 rgb(255 255 255 / .05)`, rayon 12 px) contenant le `router-outlet`, une ligne discrète « Stage 2026 · données de démonstration » en bas. Bouton principal blanc (fond `--rh-ink`, texte `--rh-bg`) limité à `.auth`. La promesse et l'exemple de calcul de l'ancien panneau marine sont retirés. Plus aucun hex dans le composant hors du halo.

- [ ] **Step 1: Écrire les tests**

```ts
it('force le thème sombre même quand l'application est en clair', () => {
  document.documentElement.classList.remove('rh-dark');
  expect(el.querySelector('.auth')!.classList).toContain('rh-theme-sombre');
});
it("n'utilise aucun composant à superposition", () => {
  // Review Focus 1 : les superpositions PrimeNG s'attachent à <body>, hors du sous-arbre sombre
  expect(el.querySelector('p-select, p-datepicker, p-autocomplete, p-multiselect')).toBeNull();
});
```

Le second test monte `AuthLayout` avec une route enfant `Connexion` (et refait le contrôle pour `MotDePasseOublie` et `LienMotDePasse`).

- [ ] **Step 2: Lancer : échec**

Run: `npx ng test --watch=false --include src/app/layout/auth-layout.spec.ts`
Expected: FAIL (`.auth` absent).

- [ ] **Step 3: Implémenter**

- [ ] **Step 4: Vérifier**

Run: `npx ng test --watch=false`
Expected: tout passe.
Visuel : `/login`, `/forgot-password`, `/reset-password?token=x` en thème Clair et Sombre, 1440 px et 390 px : sombre dans les deux cas, champ mot de passe et bouton lisibles, focus visible.

- [ ] **Step 5: Commit**

```bash
git add src/app/layout/auth-layout.* src/app/auth src/styles.scss
git commit -m "feat(design): écrans de connexion centrés, toujours sombres, avec halo"
```

---

### Task 6: Employés (liste, fiche, formulaire)

**Files:**
- Modify: `src/app/employes/employes-liste.{html,scss}`, `employe-detail.{html,scss}`, `employe-form.{html,scss}`
- Test: `src/app/employes/employes-liste.spec.ts` (ajouts)

**Interfaces:**
- Consumes: motifs tâche 3, composants tâche 2.

Liste : `.entete` de la tâche 3 (« Entreprise › Employés » + action « Ajouter un employé ») ; barre de filtres compacte (recherche, Département, Rôle à 32 px, total à droite en 12 px `--rh-muted`) ; colonnes inchangées ; avatar 22 px fond `--rh-surface-3` ; N° en mono `--rh-muted`. **Sous 600 px**, les lignes s'empilent : le tableau devient une liste (nom + email, puis département · rôle), sans défilement horizontal (`tableStyle min-width` retiré sous ce seuil). Fiche : avatar 48 px fond `--rh-surface-3` (plus de dégradé ni de hex), soldes et dernières demandes placés juste après l'en-tête d'identité sous 1100 px, valeurs chiffrées en `.lecture`. Formulaire : champs 32 px alignés, actions en bas à droite.

- [ ] **Step 1: Écrire le test**

```ts
it("affiche chaque employé avec son nom, son département et son rôle en français", () => {
  // données : { prenom: 'Lina', nom: 'Benali', departementNom: 'Produit', roleNom: 'MANAGER', … }
  expect(ligne.textContent).toContain('Lina Benali');
  expect(ligne.textContent).toContain('Produit');
  expect(ligne.textContent).toContain('Manager');
});
```

(protège le contenu pendant la refonte du gabarit empilé ; passe déjà ou échoue selon le gabarit actuel, puis doit passer après).

- [ ] **Step 2: Implémenter les trois écrans**

- [ ] **Step 3: Vérifier**

Run: `npx ng test --watch=false && npx ng build && grep -rnE "#[0-9a-fA-F]{3,6}\b|font-display" src/app/employes`
Expected: tests verts ; build OK ; grep sans résultat.
Visuel : liste, fiche, formulaire en Clair et Sombre, 1440 et 390 px ; un nom de 40 caractères ne déborde pas (Review Focus 2).

- [ ] **Step 4: Commit**

```bash
git add src/app/employes
git commit -m "feat(design): écrans employés au style Linear, liste empilée sur mobile"
```

---

### Task 7: Accueil, départements, rôles, sécurité, formulaire partagé

**Files:**
- Modify: `src/app/accueil/accueil.{html,scss}`, `src/app/departements/*.ts`, `src/app/roles/*.ts`, `src/app/compte/securite.ts`, `src/app/shared/nom-description-form.ts`
- Test: `src/app/roles/role-detail.spec.ts` (nouveau)

**Interfaces:**
- Consumes: motifs tâche 3 ; `libelleRole(nom: string): string` existant (celui qu'utilise `employes-liste`).

Chaque écran passe à `.entete` / panneaux / lignes de la tâche 3, styles inline nettoyés de toute référence supprimée (`--rh-font-display`, `--rh-shadow-card`, `--rh-*-soft` de statut). Effectifs des départements et des rôles en `.mono`. Titre de la fiche rôle en français via `libelleRole`. Pas de petit libellé au-dessus des titres sauf un vrai fil d'Ariane cliquable ; sur l'accueil, la date devient une ligne `--rh-muted` sous le titre.

- [ ] **Step 1: Écrire le test**

```ts
it('affiche le rôle ADMIN sous le titre « Administrateur »', () => {
  // RhApi simulé : role { id: 1, nom: 'ADMIN', … }
  expect(el.querySelector('h1')!.textContent!.trim()).toBe('Administrateur');
});
```

- [ ] **Step 2: Lancer : échec (titre « ADMIN »)**

Run: `npx ng test --watch=false --include src/app/roles/role-detail.spec.ts`
Expected: FAIL.

- [ ] **Step 3: Implémenter**

- [ ] **Step 4: Vérifier**

Run: `npx ng test --watch=false && npx ng build && grep -rnE "#[0-9a-fA-F]{3,6}\b|font-display|shadow-card|-soft\)" src/app --include=*.ts --include=*.scss --include=*.html | grep -v core/theme`
Expected: tests verts ; build sans avertissement de variable ; grep sans résultat.

- [ ] **Step 5: Commit**

```bash
git add src/app
git commit -m "feat(design): accueil, départements, rôles et sécurité au style Linear"
```

---

### Task 8: DESIGN.md et revue finale

**Files:**
- Modify: `DESIGN.md` (racine, réécriture)
- Modify: `.impeccable/surfaces/frontend-src-app.md` (contrat de direction « Linear »)

- [ ] **Step 1: Captures**

Avec le back-end de démo lancé (`docker compose up -d` à la racine) et `npx ng serve` : captures Playwright de connexion, accueil, employés (liste, fiche, formulaire), départements, rôles, sécurité, en Clair et Sombre, à 1440 et 390 px.
Expected: aucun débordement horizontal à 390 px, aucun texte illisible, connexion sombre dans les deux thèmes.

- [ ] **Step 2: Revue accessibilité**

Skill `design:accessibility-review` sur les captures et au clavier (Tab dans le rail, une liste, un formulaire, le tiroir mobile).
Expected: aucun défaut AA ouvert ; corriger ceux trouvés.

- [ ] **Step 3: Revue de finition**

Agent `impeccable:impeccable-finish-reviewer` contre la spec et la maquette `.superpowers/brainstorm/488-1794106133/content/direction-a-modes.html`. Corriger les points matériels.

- [ ] **Step 4: Documenter**

Agent `impeccable:impeccable-documenter` : réécrire `DESIGN.md` d'après ce qui est livré (jetons, typographie, composants, thèmes, exception de la connexion, mouvement), et mettre à jour le contrat de surface.

- [ ] **Step 5: Vérification finale**

Run: `npm run check:contraste && npx ng test --watch=false && npx ng build`
Expected: tout passe.

- [ ] **Step 6: Commit**

```bash
git add DESIGN.md .impeccable/surfaces/frontend-src-app.md src
git commit -m "docs(design): DESIGN.md du système Linear, corrections de la revue finale"
```
