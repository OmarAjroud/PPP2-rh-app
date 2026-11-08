---
name: RH App
description: A French internal HR tool in the Linear manner, dense, hairline-ruled, one indigo, dark as the signature with an equal light theme.
colors:
  bg: "#f7f7f8"
  surface: "#ffffff"
  surface-2: "#f4f4f5"
  surface-3: "#ececee"
  line: "#ececee"
  line-row: "#f0f0f2"
  line-strong: "#8e8f96"
  ink: "#1b1c1e"
  ink-2: "#3c3e44"
  muted: "#6b6d73"
  accent: "#5e6ad2"
  accent-hover: "#535fc4"
  accent-ink: "#ffffff"
  accent-text: "#4752b3"
  accent-soft: "rgb(94 106 210 / 0.1)"
  ok: "#19774a"
  wait: "#855c00"
  danger: "#c22f2f"
  on-danger: "#ffffff"
  neutral: "#6b6d73"
  rail-item-hover: "#f0f0f2"
  rail-item-active: "#e6e6e9"
  scrim: "rgb(0 0 0 / 0.4)"
typography:
  title:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.015em"
  panel-title:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.5
  body:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "\"cv11\", \"ss01\""
  label:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.5
  small:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.5
  micro:
    fontFamily: "Inter, Segoe UI, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 500
    lineHeight: 1.5
  reading:
    fontFamily: "JetBrains Mono, ui-monospace, Cascadia Mono, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.5
    fontFeature: "tnum"
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "8": "32px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.accent-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-secondary-hover:
    backgroundColor: "{colors.surface-2}"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.sm}"
    height: "32px"
  button-text-hover:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.on-danger}"
    rounded: "{rounded.sm}"
    height: "32px"
  button-login-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.bg}"
    rounded: "{rounded.sm}"
    height: "32px"
  input-field:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "7px 10px"
    height: "32px"
  nav-item:
    backgroundColor: "transparent"
    textColor: "{colors.ink-2}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "30px"
  nav-item-hover:
    backgroundColor: "{colors.rail-item-hover}"
    textColor: "{colors.ink}"
  nav-item-active:
    backgroundColor: "{colors.rail-item-active}"
    textColor: "{colors.ink}"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  panel-header:
    typography: "{typography.panel-title}"
    padding: "8px 16px"
    height: "44px"
  row:
    backgroundColor: "{colors.surface}"
    padding: "8px 16px"
    height: "40px"
  row-hover:
    backgroundColor: "{colors.surface-2}"
  tag:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink-2}"
    typography: "{typography.micro}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "22px"
  avatar:
    backgroundColor: "{colors.surface-3}"
    textColor: "{colors.ink-2}"
    typography: "{typography.micro}"
    rounded: "50%"
    size: "22px"
---

# Design System: RH App

## Overview

**Creative North Star: "The Quiet Instrument"**

RH App is a working tool shown at a defense, and it earns "premium" through restraint rather than effects: 13px Inter at Linear density, hairlines instead of shadows, one indigo that only means "press here", and figures set in mono so a balance reads like a measurement. The dark theme is the signature; the light theme is the same structure with the same roles, never a different product. Everything a screen needs comes from `frontend/design-system/tokens.css`, in three layers (primitives `--rh-gray-*` / `--rh-indigo-*`, semantic roles redefined under `:root.rh-dark`, component tokens such as `--rh-rail-*` and `--rh-control-height`). The PrimeNG Aura preset (`src/app/core/theme/rh-preset.ts`) points at the same semantic roles with `var()`, so switching the `rh-dark` class on `<html>` restyles the app and PrimeNG together.

The frame is two planes: a quiet app frame that holds the rail, and one inset work sheet with an 8px radius and a single hairline. Lists are dense hairline rows directly on that sheet, not grids of cards. Status is a dot and a word. Motion is a 160ms fade with a 4px rise on arrival, once. The login screens are the one exception: always dark, with a soft violet halo and a frosted panel, the only place the system allows glow, blur or glass.

Confirmed rejections: the generic admin dashboard (same-size cards everywhere, loud colours, heavy shadows); in-app effects (halo, glass, gradient text, looping animation); a light theme that looks like another product; muted grey below 4.5:1 for text or control outlines below 3:1.

**Key Characteristics:**
- Two themes, one set of roles: Système / Clair / Sombre, default Système, remembered in `localStorage` (`rh.theme`).
- Density: 13px body, 32px controls, 40px rows, 44px headers.
- One accent (indigo #5e6ad2 in both themes); selection is grey, not indigo.
- Flat work surfaces separated by hairlines; shadow only on floating layers.
- Mono, tabular figures for every computed number.
- Every colour pair gated by `npm run check:contraste`.

## Colors

A near-neutral cool grey scale in two keys (light paper, near-black ink) with a single desaturated indigo and three quiet status hues. Light values are the frontmatter; dark values live in `:root.rh-dark` and in the sidecar.

### Primary
- **Linear Indigo** (`accent`): the fill of the primary button, the focus ring, the caret and native `accent-color`, the search field's focus edge. The same value in dark; on dark its hover lightens (`--rh-indigo-400`) instead of darkening.
- **Ink Indigo** (`accent-text`): links and accent words. Darker in light (#4752b3), lighter in dark (#a4abf2), so text reaches 4.5:1 where the fill colour cannot.
- **Indigo Wash** (`accent-soft`): text selection (`::selection`) only.

### Neutral
- **App Frame** (`bg`): the plane behind everything, which the rail shares. Dark #08090a.
- **Work Sheet** (`surface`): the inset work area, panels, table rows, secondary buttons. Dark #0f1011.
- **Raised Fill** (`surface-2`): hover on rows and buttons, form field background, tag and alert ground. Dark #141516.
- **Pressed Fill** (`surface-3`): selected option, active theme toggle, avatars, disabled fields. Dark #1c1d1f.
- **Hairline** (`line`) and **Row Rule** (`line-row`): panel edges, header and footer rules; the fainter rule between rows. Dark #1c1d1f / #18191b.
- **Field Edge** (`line-strong`): form field outlines and the hover edge of secondary buttons, kept at 3:1. Dark #686b73.
- **Ink / Second Ink / Muted** (`ink`, `ink-2`, `muted`): primary text, labels and secondary text, hints, breadcrumbs, table headers, group labels. Dark #f7f8f8 / #d0d6e0 / #8a8f98.
- **Rail Hover / Rail Active** (`rail-item-hover`, `rail-item-active`): navigation item states, a step darker than the frame. Dark #18191b / #1f2023.

### Status
- **Approval Green** (`ok`), **Waiting Amber** (`wait`), **Refusal Red** (`danger`), **Off Grey** (`neutral`): dot colours and alert text. Dark #4cb782 / #f2c94c / #eb6f6f / #8a8f98. `on-danger` is white in light and #0f1011 in dark.

### Named Rules
**The One Indigo Rule.** Indigo means "this can be pressed, followed or is focused". Selected rows, active nav items, selected options and active toggles use grey fills (`surface-3`, `rail-item-active`), never indigo.

**The Contrast Gate Rule.** A new colour or role is not done until it is added to the pairs in `design-system/verifier-contraste.mjs` and `npm run check:contraste` passes in both themes: 4.5:1 for text (ink, ink-2, muted on surface, bg and surface-2; accent-text; status colours; accent-ink on accent; on-danger on danger), 3:1 for field edges and the accent fill. Semantic tokens must resolve to opaque 6-digit hex for the checker to read them.

**The Same Roles Rule.** Dark redefines values, never roles. A screen that needs `:root.rh-dark`-specific CSS is a sign a semantic token is missing.

## Typography

**Body Font:** Inter (with Segoe UI, system-ui, sans-serif), self-hosted via `@fontsource` at 400, 500, 600, with `cv11` and `ss01` enabled.
**Mono Font:** JetBrains Mono (with ui-monospace, Cascadia Mono, Consolas), self-hosted at 400 and 500.

**Character:** One crisp grotesque for everything from titles to table headers, at small sizes and tight weights; a mono that appears only where a number has been computed.

### Hierarchy
- **Title** (600, 22px, 1.25, -0.015em): the page `h1` in the header bar and the login title. One per page.
- **Panel title** (600, 13px): panel headers. Hierarchy inside a page comes from weight and rules, not size.
- **Body** (400, 13px, 1.5): all running text, table cells, field values.
- **Label** (500, 13px): buttons, nav items, field labels, status words, person names in lists.
- **Small** (12px): hints, secondary lines under a name, table column headers (500, muted, sentence case), panel footers, paginator.
- **Micro** (500, 11px): tags, rail group labels, keyboard hints, avatar initials. 11px is the floor for functional text.
- **Reading** (JetBrains Mono 500, 13px, tabular): balances, day counts, headcounts, padded IDs; a muted arrow separates before and after ("19 → 15").

### Named Rules
**The Mono Is For Reckoning Rule.** JetBrains Mono is reserved for figures the system computed or identifiers (balances, totals, headcounts, `0042`). Names, labels and dates stay in Inter. All table cells use tabular numerals; numeric columns align right.

**The Sentence Case Rule.** No uppercase labels and no tracking-out: column headers, group labels and tags are sentence case at their size.

## Layout

A 4px grid (4, 8, 12, 16, 20, 24, 32). The shell is a two-column grid: a 232px rail on the app frame, then the work sheet inset from the frame by 8px (top, right, bottom), padded 20px 32px 40px, with an 8px radius and one hairline. The rail is sticky at full height: brand mark, employee search (28px, "Rechercher…" with a `Ctrl K` hint; Ctrl K only focuses the field, there is no command palette), grouped navigation, and the user block (initials, name, role, theme toggle, "Déconnexion") pinned at the bottom above a hairline.

Each page starts with its own header bar (`header.entete`, no global top bar): it runs edge to edge across the sheet (negative margins matching the sheet padding), at least 44px tall, with a hairline below; breadcrumb and title share a baseline on the left (the breadcrumb ends with a muted "›"), actions sit right and wrap. Content below the header is capped at 1200px (`--rh-content-max`), or a page's own `--page-max` (760px for the home page). Record pages use a main column plus a 340px side column, collapsing to one column under 1100px with the side column first.

Responsive steps:
- **Under 900px:** the rail becomes a drawer (min(280px, 85vw)) over a scrim, sliding in at 160ms; a 44px mobile bar with the menu button and brand mark sits sticky on top; the work sheet goes edge to edge with 16px padding and no border or radius.
- **Under 600px:** every button and field reaches 44px; list tables drop their header and secondary columns and stack name, email, then "département · rôle" in one cell, with no horizontal scroll; the login panel starts at the top.
- **Under 560px:** record definition lists go to one column, label above value.

### Named Rules
**The Rows Not Cards Rule.** Collections are hairline rows (40px) on the sheet or inside a panel. Panels group a record's facts; they are not tiles for a dashboard grid.

**The 32 / 44 Rule.** Controls are 32px at desktop density and at least 44px below 600px, including the drawer's links, search, theme buttons and logout.

## Elevation & Depth

Depth is tonal and ruled, not lifted. The frame, the sheet and raised fills differ by one step on the grey scale, and 1px hairlines separate everything else. Work surfaces (sheet, panels, tables) have no shadow at rest or on hover. Shadow exists only for layers that float above the page: select overlays, popovers, dialogs and the mobile drawer.

### Shadow Vocabulary
- **Overlay** (`box-shadow: 0 0 0 1px rgb(0 0 0 / 0.04), 0 12px 32px -8px rgb(0 0 0 / 0.18)`; dark `0 0 0 1px rgb(255 255 255 / 0.06), 0 16px 40px -8px rgb(0 0 0 / 0.7)`): menus, popovers, dialogs, drawer.
- **Focus** (`box-shadow: 0 0 0 2px var(--rh-surface), 0 0 0 4px #5e6ad2`; dark ring #8f98ef): every `:focus-visible`, replacing the outline.
- **Scrim** (`rgb(0 0 0 / 0.4)`, dark 0.6): behind the drawer and modal dialogs.
- **Login panel** (`inset 0 1px 0 rgb(255 255 255 / 0.05), 0 24px 60px -20px rgb(0 0 0 / 0.8)`): login only.

### Named Rules
**The Flat Sheet Rule.** If it sits on the page, it has a hairline and no shadow. If it floats over the page, it gets the overlay shadow.

**The One Halo Rule.** Glow, blur, frosted glass and the violet halo belong to the login layout only. Inside the app they are defects.

## Shapes

Gently rounded, small radii in three steps: 6px for controls, nav items, tags, alerts and select options; 8px for panels, the work sheet and overlays; 12px for dialogs and the login panel; 4px for the tiny theme-toggle buttons and inline `code` chips. Avatars and status dots are the only circles. Borders are always 1px. Panels clip their content (`overflow: hidden`) so tables follow the panel radius. The brand mark is an 18px rounded square (5px radius) with an indigo diagonal gradient; on login it grows to 22px with a soft glow.

## Components

### Buttons
Compact and quiet: label weight, no shadow, colour change only.
- **Shape:** gently rounded (6px), 32px tall, 12px side padding; icon-only buttons are 32px squares; icons 13px.
- **Primary:** indigo fill, white label; hover deepens (light) or lightens (dark).
- **Secondary / Outlined:** sheet fill, hairline border, ink label; hover takes the raised fill and the field-edge border.
- **Text:** transparent, second-ink label; hover takes the raised fill.
- **Danger:** solid red with `on-danger` label; outlined danger keeps a red label and gains a red border on hover.
- **Transitions:** background, border and colour at 120ms on the system ease.

### Inputs / Fields
- **Style:** raised fill (`surface-2`), 1px field-edge outline (3:1), 6px radius, 32px tall (13px text, 7px by 10px padding), no shadow.
- **Focus:** border turns indigo, plus the system focus ring on keyboard focus.
- **Error / Disabled:** red border and a 12px red message under the field; disabled takes the pressed fill and muted text.
- **Field block:** label (13px, 500, second ink) above the control, 6px gap, optional 12px muted help and error lines.

### Status
A 7px dot in the status colour followed by the word in second ink, 13px 500, no tinted background: green (actif, approuvée, disponible), amber (en attente), red (refusée, erreur), grey (inactif, annulée, escaladée, vérification). Status is never carried by colour alone.

### Tags
Neutral and small: 22px tall, 11px 500 second ink on the raised fill with a hairline and a 6px radius. For role, department and seniority, never for status.

### Panels and rows
- **Panel:** sheet fill, hairline border, 8px radius, no shadow.
- **Panel header:** 44px minimum, 8px by 16px, 13px 600, hairline below; an optional small link on the right.
- **Panel body / footer:** 16px padding with 16px gaps; footer 12px by 16px, small muted text, actions pushed right, hairline above.
- **Row:** 40px minimum, label left and value or reading right, a faint row rule between rows, none after the last.
- **Record list:** a 180px label column (muted) beside a value column, row rules between pairs.

### Tables
PrimeNG tables restyled as hairline rows: 34px header in 12px 500 muted on the sheet, 40px body rows with a faint rule and a raised-fill hover at 120ms, no rule after the last row. A sorted column is shown by its 10px arrow only, never a coloured band. Loading dims the table to 0.55 opacity. People cells pair a 22px initials avatar with the name (500) and a muted email line.

### Alerts
Form-level messages: 8px by 12px, 6px radius, raised fill, 1px border in the text colour: red for errors, green for success, second ink with a hairline for information. Field-level messages sit under each field instead.

### Navigation
The rail shares the app frame. Items are 30px tall (44px in the drawer), 8px padding, 6px radius, 13px 500 second ink with a 13px muted icon; hover takes `rail-item-hover`, the current page takes `rail-item-active` with ink text and `aria-current="page"`. Group labels are 11px 500 muted, sentence case. The theme toggle is a three-button segmented control (Système, Clair, Sombre) in a hairline frame, the active one on the pressed fill.

### Motion
Fast and once. State transitions use 120ms (`--rh-fast`) and the drawer 160ms (`--rh-base`), on `cubic-bezier(0.25, 0.8, 0.25, 1)`. On page arrival the page's direct children fade in with a 4px rise over 160ms, 30ms apart, `power2.out`, once, then GSAP clears its inline styles. Reading figures count to their value over 600ms the first time they appear. Nothing loops. Under `prefers-reduced-motion` the duration tokens drop to 0ms and GSAP motion is skipped entirely.

### Login (the one exception)
All public screens ("Connexion", "Mot de passe oublié", account links) are always dark: the layout forces `rh-dark` while shown and gives the user's choice back on leaving. A near-black frame carries a diffuse violet halo at the top (three blurred radial gradients). The form sits in one centred 400px panel: 28px padding (20px on phones), 12px radius, hairline, 82% sheet colour with a 12px backdrop blur (opaque under `prefers-reduced-transparency`), an inset top highlight and a deep drop shadow. The primary button is full width and white on dark (ink fill, frame-coloured label). A small muted footnote sits under the panel.

## Do's and Don'ts

### Do:
- **Do** take every colour, size, radius and duration from `--rh-*` semantic or component tokens; add a token before adding a value.
- **Do** run `npm run check:contraste` after any colour change and add new pairs to the checker.
- **Do** keep controls at 32px and rows at 40px on desktop, and 44px targets below 600px.
- **Do** show lists as hairline rows on the sheet, and group a record's facts in hairline panels.
- **Do** set computed figures in JetBrains Mono with tabular numerals, before and after joined by a muted "→".
- **Do** write status as a dot plus a French word ("En attente", "Approuvée").
- **Do** give each page its own full-bleed header bar with the `h1`, breadcrumb inline before it, actions on the right.

### Don't:
- **Don't** use indigo for selection or the active nav item; those are grey fills.
- **Don't** put shadows on panels, rows or the sheet; shadows are for overlays and the drawer.
- **Don't** use halo, glow, blur, glass or gradient text inside the app; they belong to the login layout only.
- **Don't** tint status backgrounds or carry status by colour alone.
- **Don't** uppercase or letter-space labels, or put small labels above titles.
- **Don't** build dashboard grids of same-size cards or add rail counters and statuses the API does not provide.
- **Don't** make the light theme its own design: same structure, same roles, only values change.
- **Don't** loop any animation, or animate when reduced motion is requested.
