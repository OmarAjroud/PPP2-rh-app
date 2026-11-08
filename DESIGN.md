---
name: RH App
description: A French internal HR tool on one neutral scale, where every computed figure shows its working.
colors:
  ground: "#fafaf9"
  surface: "#ffffff"
  surface-2: "#f4f4f2"
  surface-3: "#ececea"
  hairline: "#e7e7e5"
  hairline-strong: "#d6d6d3"
  ink: "#111214"
  ink-2: "#3a3d42"
  muted: "#64686f"
  accent: "#4c4fd8"
  accent-hover: "#3f42c4"
  accent-ink: "#ffffff"
  accent-soft: "#eeeffc"
  accent-line: "#c9caf4"
  ok: "#1f7a4d"
  ok-soft: "#e8f4ee"
  wait: "#875a00"
  wait-soft: "#fbf3e0"
  danger: "#c2362b"
  danger-soft: "#fbecea"
  on-danger: "#ffffff"
  out: "#6b6f76"
  neutral: "#64686f"
  neutral-soft: "#f1f1ef"
  escalated: "#3a3d42"
  escalated-soft: "#ececea"
  offday: "#f4f4f2"
  offday-stripe: "#ececea"
typography:
  figure:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "2rem"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
  headline:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.01em"
  title-panel:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.25
  body:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1.5
  meta:
    fontFamily: "Figtree, Segoe UI, system-ui, -apple-system, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.2
rounded:
  sm: "5px"
  md: "7px"
  lg: "10px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "8": "32px"
  "10": "40px"
  "12": "48px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.accent-ink}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "36px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
    textColor: "{colors.accent-ink}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "36px"
  button-secondary-hover:
    backgroundColor: "{colors.surface-2}"
  button-ghost:
    textColor: "{colors.ink-2}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "36px"
  button-ghost-hover:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
  button-danger:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.danger}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "36px"
  button-danger-hover:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
  field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 12px"
    height: "36px"
  field-disabled:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.muted}"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "20px"
  status-wait:
    backgroundColor: "{colors.wait-soft}"
    textColor: "{colors.wait}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 8px 2px 7px"
  status-ok:
    backgroundColor: "{colors.ok-soft}"
    textColor: "{colors.ok}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 8px 2px 7px"
  status-refused:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 8px 2px 7px"
  status-cancelled:
    backgroundColor: "{colors.neutral-soft}"
    textColor: "{colors.neutral}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 8px 2px 7px"
  status-escalated:
    backgroundColor: "{colors.escalated-soft}"
    textColor: "{colors.escalated}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 8px 2px 7px"
  day-counted:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    rounded: "{rounded.sm}"
    padding: "6px 8px"
  day-off:
    backgroundColor: "{colors.offday}"
    textColor: "{colors.muted}"
    rounded: "{rounded.sm}"
    padding: "6px 8px"
  nav-item:
    textColor: "{colors.ink-2}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "7px 12px"
  nav-item-active:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
  toast:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.ground}"
    rounded: "{rounded.md}"
    padding: "12px 16px"
---

# Design System: RH App

## Overview

**Creative North Star: "The Working Proof"**

RH App is a working screen that proves its numbers. One neutral scale, near-white ground, white panels on one-pixel hairlines, near-black ink, carries almost everything; a single indigo marks only what can be pressed, selected or focused. The signature is the proof row: every computed figure (jours ouvrables, solde après) opens its derivation inline, so Lina sees why she is taking 4 days and Karim decides with the same arithmetic in front of him.

Density is calm and desk-grade: 14px body, 36px controls, hairline rows, tabular figures everywhere a number lives. Depth is flat; only overlays (dialog, menu, snackbar) cast a shadow. The dark theme is the same scale inverted to graphite with the indigo lifted, driven entirely by `tokens.css`.

The system sits on Angular Material (M3) but refuses its admin default: no default violet, no pill buttons, no resting elevation, no coloured toolbar, no grid of same-size cards as page structure, no chips everywhere.

**Key Characteristics:**
- One neutral scale; indigo reserved for press, selection, focus and links.
- Status is always a dot plus a French word, never colour alone.
- Every computed figure opens its derivation (the proof row).
- Three weights with fixed jobs: 400 text and key figures, 500 labels and nav, 600 titles and buttons.
- One radius per size class: 5px small marks, 7px controls, 10px panels.
- Flat surfaces; shadow only on overlays.
- Motion 150 to 200ms ease-out on state changes, nothing on entry.

### Wiring it into Angular (step 13)

Both files live in `frontend/design-system/`. `tokens.css` is the single source of truth; `material-theme.scss` only maps Material's `--mat-sys-*` system tokens onto `--rh-*` variables, so light/dark switching never touches SCSS.

`angular.json`, under `projects.<app>.architect.build.options` (paths relative to the workspace root; adjust if the Angular workspace is not `frontend/`):

```json
"styles": ["design-system/tokens.css", "src/styles.scss"],
"stylePreprocessorOptions": { "includePaths": ["."] }
```

`src/styles.scss`:

```scss
@use 'design-system/material-theme';
```

Load Figtree at 400, 500 and 600 only (the reference loads `https://fonts.googleapis.com/css2?family=Figtree:wght@400;500;600&display=swap`).

`app.config.ts` providers (SCSS cannot switch field appearance or locale):

```ts
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';

providers: [
  { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline', subscriptSizing: 'dynamic' } },
  provideNativeDateAdapter(),
  { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' }, // jj/mm/aaaa, lundi en premier
]
```

Theme switching: light is default; dark follows `prefers-color-scheme` unless `<html data-theme="light">` is set, and `<html data-theme="dark">` forces dark. The reference toggle offers Système / Clair / Sombre and persists the choice under `localStorage['rh-theme']`. `material-theme.scss` was written before the Angular project existed: compile-check it when scaffolding (13a).

## Colors

A monochrome product palette: warm-neutral greys from paper to ink, one cool indigo for interaction, and four muted status hues that always travel with a word. Dark values are defined in `tokens.css` and listed in the sidecar.

### Primary
- **Interaction Indigo** (accent): primary buttons, links, the proof-row toggle, focus rings, the counted days in the day strip, the selected row in a decision list. Hover deepens to **Pressed Indigo** (accent-hover). Text on indigo uses accent-ink.
- **Selection Wash** (accent-soft) with **Selection Edge** (accent-line): the background and border of selected things (counted day cells, the open request row, text selection).

### Neutral
- **Paper Ground** (ground): page background and the sidebar.
- **White Panel** (surface): panels, tables, fields, secondary buttons.
- **Quiet Fill** (surface-2): hover rows, active nav item, disabled fields, ghost-button hover.
- **Pressed Fill** (surface-3): pressed states and skeleton shimmer.
- **Hairline** (hairline): every 1px divider: panel borders, header/footer rules, table rows, list rows.
- **Field Edge** (hairline-strong): field and secondary-button borders, the comment quote rule.
- **Ink** (ink): primary text, the unread notification dot, the toast background.
- **Second Ink** (ink-2): labels, table headers, proof-row terms, ghost buttons.
- **Muted Grey** (muted): hints, meta, breadcrumbs, sidebar group names (4.8:1 on ground). Also the field hover border.

### Status
- **Approval Green** (ok on ok-soft): Approuvée; also account state Actif.
- **Waiting Amber** (wait on wait-soft): En attente; also Invitation envoyée.
- **Refusal Red** (danger on danger-soft): Refusée, field errors, danger buttons. on-danger is text on a filled danger surface.
- **Cancelled Grey** (neutral on neutral-soft): Annulée.
- **Escalation Ink** (escalated on escalated-soft): Transmise aux RH, deliberately ink on neutral grey; the word carries the meaning, not a hue.
- **Out-of-range Grey** (out): day-strip labels for days outside the requested range (5:1 on surface).
- **Off-day Stripe** (offday / offday-stripe): a 135deg 6px stripe marking weekends and public holidays inside the range.

### Named Rules
**The One Indigo Rule.** Indigo appears only on what can be pressed, selected or focused, plus links. Never on headings, icons at rest, decorative fills, charts' default series or status. If it is indigo, it responds to the pointer or the keyboard.

**The Dot and Word Rule.** Every status is a 7px dot in currentColor plus its French word on a soft tint. Colour alone never carries information.

**The Neutral Material Rule.** Material's secondary and tertiary roles are mapped to Second Ink and Quiet Fill. Only primary is indigo; chips, selected list items and datepicker ranges stay neutral unless they are a selection.

## Typography

**Display Font:** none; the system has one family.
**Body Font:** Figtree (with Segoe UI, system-ui, -apple-system, sans-serif)

**Character:** A single humanist-geometric sans, friendly at 14px and firm at 600, with tabular figures wherever a number appears so columns and proofs align.

### Hierarchy
- **Figure** (400, 2rem, line-height 1, -0.02em, tabular): the one key total on a form ("4 jours"). Regular weight, large size; the number is big, not loud.
- **Headline** (600, 1.5rem, 1.25, -0.01em): page titles ("Demander un congé"). Drops to 1.25rem under 860px.
- **Title** (600, 1.25rem, 1.25, -0.01em): section titles.
- **Panel title** (600, 1rem): panel headers, empty-state titles, in-row facts and day numbers.
- **Body** (400, 0.875rem, 1.5): body, table cells, controls. Prose blocks cap at 52 to 70ch.
- **Label** (500, 0.8125rem): field labels, table headers, status words, sidebar group names, breadcrumbs (breadcrumbs and hints at 400).
- **Meta** (400, 0.75rem, 1.2): day-strip sublabels and table meta only; never body.

### Named Rules
**The Three Weights Rule.** 400 for text and key figures, 500 for labels and nav, 600 for titles and buttons. No other weights are loaded.

**The Tabular Rule.** Any element that shows a count, balance, date figure or amount uses `font-variant-numeric: tabular-nums` (`.num`, `td.num`, Material tables, proof rows, figures). Numeric columns align right.

## Layout

A 4px grid (4, 8, 12, 16, 20, 24, 32, 40, 48). Gaps follow fixed jobs: 4 icon-to-text, 8 within a group, 16 between fields, 24 between panels, 32 page margins and the work/rail gutter.

The app shell is a 232px sidebar plus a main column (content max 1240px). Main padding is 20px top, 32px sides. The top bar holds breadcrumb and page title on the left, notifications on the right. The working area is a main panel beside a 340px right rail (balances, recent requests) with a 32px gutter. Panel footers put context on the left ("Décidée par …") and actions bottom right.

Controls are 36px tall, 44px on coarse pointers (`--rh-control-h`).

Responsive:
- **Under 1100px:** the right rail drops below the main panel; single column.
- **Under 860px:** the sidebar becomes a horizontal scrolling strip under the top edge (group names and the user block hide; the current item is scrolled into view while keeping the brand visible when possible). Three-field rows stack; fact grids go to two columns; main padding becomes 20px/16px; the page title drops to 1.25rem.
- **Under 520px:** the total stacks (label above the figure); day cells tighten and drop their sublabel; footer buttons stretch to equal widths across the panel.

**The Rows Not Cards Rule.** Page structure is panels with hairline rows and lists, never a grid of same-size cards. A selected request opens in place inside its row and changes status in place without moving.

## Elevation & Depth

Flat by default. Depth comes from tonal layering (Paper Ground under White Panel) and 1px hairlines, never from resting shadows. Material's level1 is set to none; levels 2 and 3 map to the overlay shadow. Focus is a 3px indigo ring drawn with box-shadow (inset inside list rows).

### Shadow Vocabulary
- **Overlay** (`--rh-shadow-overlay`): dialogs, menus, the snackbar/toast. Nothing else.
- **Focus ring** (`--rh-focus`): every `:focus-visible`, replacing the outline.

### Named Rules
**The Overlays Only Rule.** A shadow means the thing floats above the page. Panels, buttons, fields and cards never cast one.

## Shapes

Small, consistent radii, one per size class: 5px for small marks (status badges, day cells, swatches, inline focus targets), 7px for controls (buttons, fields, nav items, segmented control, toast), 10px for containers (panels, dialogs, the app frame). Material's corner tokens map extra-small to 5px, small to 7px, medium and up to 10px. Circles only for dots, the spinner and the count badge. Borders are 1px everywhere; dashed borders mark days outside the requested range.

**The Size Class Rule.** Pick the radius by what the element is (mark, control, container), never by taste. There is no pill.

## Components

### Buttons
Flat and firm; the label is the action.
- **Shape:** gently squared (7px), 36px tall, 16px horizontal padding, 600 weight, no letter-spacing.
- **Primary:** Interaction Indigo with accent-ink text; one per panel, bottom right.
- **Secondary:** White Panel with Field Edge border and Ink text; hover to Quiet Fill.
- **Ghost:** no border or fill, Second Ink; hover to Quiet Fill and Ink. Used for icon buttons and low-priority actions.
- **Danger:** secondary shape with Refusal Red text; stays quiet until hover, which fills danger-soft and turns the border red.
- **Disabled:** 50% opacity, not-allowed cursor. **Busy:** a 14px spinner plus a progressive label ("Envoi…").
- **Transitions:** background and border-color, 150ms ease-out.

### Status badges
- **Style:** 5px radius, soft tint background, 7px dot in currentColor, 500 label at 13px, padding 2px 8px 2px 7px. Colour transitions 200ms when a status changes in place.
- **Vocabulary:** En attente, Approuvée, Refusée, Annulée, Transmise aux RH. An interrupted leave keeps Approuvée and shows its motif d'interruption beside it; there is no sixth status.

### Panels
- **Corner Style:** 10px. **Background:** White Panel. **Border:** 1px Hairline. **Shadow:** none.
- **Anatomy:** header (16px/20px padding, 600, hairline below), body (20px padding, 20px gaps), footer (12px/20px, hairline above, context left, buttons right). List rows inside a panel are 12px/20px with hairlines between.

### Inputs / Fields
- **Style:** outlined; 1px Field Edge, White Panel fill, 7px radius, 36px tall, 12px padding. Label above in 500/13px Second Ink; hint below in Muted Grey.
- **Hover:** border to Muted Grey. **Focus:** border to Interaction Indigo plus the focus ring, 150ms.
- **Error:** border Refusal Red; message below in red with an alert icon, naming the problem and the recovery ("Cette date n'existe pas. Format attendu : jj/mm/aaaa."). **Disabled:** Quiet Fill, Muted Grey text; say why ("Non visible pour votre rôle").

### Navigation
- **Sidebar:** Paper Ground, hairline right edge. Items are 500 Second Ink with optional 16px stroke icons, 7px radius; hover and current page both use Quiet Fill with Ink (never indigo). Group names (Congés, Entreprise) are 500/13px Muted Grey. The user's name and role sit at the bottom above a hairline.
- **Mobile:** see Layout, a horizontal scrolling strip under 860px.

### Tables
Hairline rows, 500/13px Second Ink headers, 12px/16px cells, Quiet Fill row hover, tabular figures, numeric columns right-aligned. Sortable headers are buttons with a sort icon; the sorted column is Ink. Pager below: "1–4 sur 4 employés" with Précédent / Suivant.

### Feedback
- **Toast:** Ink background, ground-coloured text, 7px radius, overlay shadow, max 440px, optional ghost action ("Voir").
- **Notifications:** an 8px Ink dot marks unread; read items keep the space empty. Title, then muted sentence and relative time.
- **Empty, denied, error states:** a 16px title and a sentence that says what happens next or how to recover ("Vos saisies sont conservées. Vérifiez la connexion puis réessayez."), with a retry button when one helps.
- **Loading:** neutral skeleton bars with a shimmer (disabled under reduced motion).

### Proof row (signature)
Every computed figure opens its derivation inline. A disclosure ("Voir le calcul", Interaction Indigo, 500, chevron rotating 180deg in 150ms) sits under the figure, separated by a hairline. Open, it shows a two-column definition list in tabular figures: terms in Second Ink on the left, values right-aligned in 500; subtotal rows carry a hairline above and 600 Ink. Example: Jours en semaine du 14 au 18 déc. 5 / Jour férié (jeu. 17, Fête de la Révolution) − 1 / **Jours ouvrables 4**; Solde annuel 19 / **Solde après validation 15**. Figures come from the API, never estimated client-side.

### Day strip
A seven-column week grid (Monday first) of 5px-radius cells: weekday label, day number in 600 tabular, and a sublabel (férié, week-end). Counted days use Selection Wash, Selection Edge and an indigo number; weekends and holidays inside the range use the off-day stripe; days outside the range are dashed and Out-of-range Grey. A note below names each excluded holiday and states that Saturday and Sunday are not counted.

## Do's and Don'ts

### Do:
- **Do** import `tokens.css` before the Material theme and let `--rh-*` variables drive light and dark; never hard-code a hex in a component.
- **Do** open the derivation of every computed figure with a proof row.
- **Do** show status as dot plus word from the fixed vocabulary: En attente, Approuvée, Refusée, Annulée, Transmise aux RH. Interrupted leave stays Approuvée with its motif.
- **Do** name the action on every control ("Envoyer la demande", "Valider", "Refuser", "Tout marquer comme lu"), and write every error as problem plus recovery, in French.
- **Do** say who decides and what happens next in the panel footer ("Décidée par Karim Mansour, votre manager. Sans réponse sous 3 jours, elle passe aux RH.").
- **Do** require a motif before a refusal is sent, and explain unavailable actions instead of hiding them.
- **Do** use tabular figures for every number and right-align numeric columns.
- **Do** meet WCAG 2.1 AA: text contrast, full keyboard use, the visible 3px focus ring, labels on icon buttons and badges.
- **Do** keep motion to 150 to 200ms ease-out on state changes; nothing animates on entry, and reduced motion sets durations to 0.

### Don't:
- **Don't** ship Material's default violet, pill-shaped buttons or resting elevation; the theme overrides exist to remove them.
- **Don't** use indigo for anything that cannot be pressed, selected or focused.
- **Don't** build pages as grids of same-size cards; use panels with hairline rows.
- **Don't** add kickers or eyebrow labels above titles.
- **Don't** use gradient text.
- **Don't** convey a status by colour alone, or invent a status outside the vocabulary.
- **Don't** cast shadows on anything but overlays.
- **Don't** load a fourth weight or a second family.
