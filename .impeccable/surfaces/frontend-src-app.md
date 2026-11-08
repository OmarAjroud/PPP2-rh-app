---
version: 2
slug: "frontend-src-app"
primary_target: "frontend/src/app"
related_targets: ["frontend/design-system/tokens.css", "frontend/src/styles.scss"]
---

# Surface brief: RH App application (direction "Linear")

Scope: the whole Angular app (shell, login and account links, home / Mon profil, employees list, record and form, departments, roles, security; leave, notifications, dashboard and assistant screens later reuse the tokens and shared classes). Mode: Operate. Desktop first, works at 390px. Light + dark (Système / Clair / Sombre, default Système). French UI, English URL paths, WCAG AA. PrimeNG 21 styled mode on an Aura preset that points at `var(--rh-*)`; GSAP for arrival and counters.

Why: the user rejected the monochrome system and then "Nuit et lumière" (navy rail, floating cards, #2D5BFF, Bricolage Grotesque), and asked for a clean, modern, premium app in the spirit of Linear for the internship defense. From three mockups they chose A "Linear sombre" with an equivalent light theme, and the violet halo of mockup C on the login screen only. Spec: docs/superpowers/specs/2026-11-08-systeme-de-conception-linear-design.md. System of record: DESIGN.md.

## Direction contract

THESIS: A quiet instrument: premium through restraint and detail (density, hairlines, one indigo, crisp type, fast transitions, keyboard), not through effects. It refuses the generic admin dashboard (same-size cards, loud colour, heavy shadows), in-app halo, glass or gradient text, looping motion, and a light theme that looks like another product.

OWN-WORLD: Three-layer tokens in design-system/tokens.css (primitives, semantic roles redefined under :root.rh-dark, component tokens). App frame #F7F7F8 (dark #08090A) shared by a 232px rail; one inset work sheet #FFFFFF (dark #0F1011), 8px radius, single hairline. Ink #1B1C1E / muted #6B6D73 (dark #F7F8F8 / #8A8F98), muted text kept at 4.5:1 and field edges at 3:1 by `npm run check:contraste`. One indigo #5E6AD2 for action, focus and links (link text #4752B3, dark #A4ABF2); selection and the active nav item are grey fills. Inter for everything at 13px body, 22px/600 titles; JetBrains Mono only for computed figures. Radii 6 / 8 / 12px; 32px controls, 40px rows, 44px headers, 44px touch targets under 600px. Status is a dot plus a word, no tint. No shadow on work surfaces; overlay shadow only for menus, dialogs and the drawer.

STORY: Each person sees who they are (initials, name, role, department), what they have (balances as mono readings, "19 → 15"), and what they can do, in a tool that feels considered and fast.

FIRST VIEWPORT: Rail left on the app frame: brand mark "RH App", employee search with a Ctrl K hint (Ctrl K focuses it; no command palette), grouped nav, user block with initials, the Système / Clair / Sombre toggle and "Déconnexion" at the bottom. Work sheet: a full-bleed slim header bar per page (breadcrumb inline before the h1 on the left, primary action right), then compact filters, then hairline rows; record pages put a 340px side column beside the main one. Under 900px the rail becomes a drawer behind a 44px mobile bar. Login: always dark, near-black frame with a diffuse violet halo at the top, one centred 400px frosted panel with a hairline and inner highlight, white full-width primary button.

FORM: user-pinned mockup A "Linear sombre" plus its light equivalent, halo from mockup C on login only; approach 1 (rewrite tokens, keep PrimeNG structure). Signature interaction: figures that read like instruments (mono, tabular, before → after) and a theme that switches app and PrimeNG together by one class. Motion grammar: state transitions 120ms, drawer 160ms, on cubic-bezier(0.25, 0.8, 0.25, 1); page children fade in with a 4px rise over 160ms, 30ms apart, once; readings count up over 600ms on first show; nothing loops; all off under prefers-reduced-motion.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md and its sidecar, and every shipping raster carrying its provenance
