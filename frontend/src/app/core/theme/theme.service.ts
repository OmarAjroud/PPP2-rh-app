import { Injectable, computed, signal } from '@angular/core';

export type ChoixTheme = 'systeme' | 'clair' | 'sombre';

const CLE = 'rh.theme';

/**
 * Light / dark. "Système" follows the OS setting; the choice is remembered on this browser.
 * Applies the "rh-dark" class on <html>, read by tokens.css and the PrimeNG preset.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly choix = signal<ChoixTheme>(lire());
  private readonly media = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly systemeSombre = signal(this.media?.matches ?? false);
  readonly sombre = computed(() => this.choix() === 'sombre' || (this.choix() === 'systeme' && this.systemeSombre()));

  constructor() {
    this.media?.addEventListener('change', e => { this.systemeSombre.set(e.matches); this.appliquer(); });
    this.appliquer();
  }

  choisir(c: ChoixTheme): void {
    this.choix.set(c);
    try { localStorage.setItem(CLE, c); } catch { /* storage unavailable */ }
    this.appliquer();
  }

  private appliquer(): void {
    document.documentElement.classList.toggle('rh-dark', this.sombre());
  }
}

function lire(): ChoixTheme {
  try {
    const v = localStorage.getItem(CLE);
    return v === 'clair' || v === 'sombre' ? v : 'systeme';
  } catch {
    return 'systeme';
  }
}
