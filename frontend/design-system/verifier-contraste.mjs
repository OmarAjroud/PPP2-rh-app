// Checks WCAG 2.1 contrast of the design system's key colour pairs, in both themes.
// Usage: node design-system/verifier-contraste.mjs   (exit code 1 if any pair fails)
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const css = readFileSync(fileURLToPath(new URL('./tokens.css', import.meta.url)), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

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

/** Declarations of the first block whose selector is exactly `selecteur`. */
function bloc(selecteur) {
  const re = new RegExp(`(^|\\})\\s*${selecteur.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*\\{([^}]*)\\}`);
  const m = css.match(re);
  if (!m) throw new Error(`Bloc introuvable : ${selecteur}`);
  const vars = {};
  for (const [, nom, valeur] of m[2].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) vars[nom] = valeur.trim();
  return vars;
}

function resoudre(vars, nom, profondeur = 0) {
  const v = vars[nom];
  if (v === undefined) throw new Error(`Jeton absent : ${nom}`);
  const ref = v.match(/^var\((--[\w-]+)\)$/);
  if (ref && profondeur < 10) return resoudre(vars, ref[1], profondeur + 1);
  return v;
}

function luminance(hex, nom) {
  const m = hex.match(/^#([0-9a-f]{6})$/i);
  if (!m) throw new Error(`${nom} doit être un hex opaque à 6 chiffres (trouvé : ${hex})`);
  const [r, g, b] = [0, 2, 4].map(i => parseInt(m[1].slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

const clair = bloc(':root');
const themes = { clair, sombre: { ...clair, ...bloc(':root.rh-dark') } };

let echecs = 0;
for (const [theme, vars] of Object.entries(themes)) {
  for (const [avant, fond, seuil] of PAIRES) {
    let ligne;
    try {
      const [l1, l2] = [luminance(resoudre(vars, avant), avant), luminance(resoudre(vars, fond), fond)];
      const ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
      const ok = ratio >= seuil;
      if (!ok) echecs++;
      ligne = `${ok ? 'OK    ' : 'ÉCHEC '} ${theme.padEnd(6)} ${avant} / ${fond}  ${ratio.toFixed(2)} (min ${seuil})`;
    } catch (e) {
      echecs++;
      ligne = `ÉCHEC  ${theme.padEnd(6)} ${avant} / ${fond}  ${e.message}`;
    }
    console.log(ligne);
  }
}
if (echecs) { console.error(`\n${echecs} paire(s) en échec.`); process.exit(1); }
console.log('\nToutes les paires passent.');
