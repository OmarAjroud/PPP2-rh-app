import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RhApi } from '../core/api/rh-api';
import { messageErreur } from '../core/api/erreurs';

export type ListeExport = 'employes' | 'departements' | 'demandes' | 'audit';
export type FormatExport = 'xlsx' | 'pdf';
export type FiltresExport = Record<string, string | number | null | undefined>;

/** File name from Content-Disposition (UTF-8 form first), or `defaut` when the header is missing or hidden. */
export function nomFichier(disposition: string | null, defaut: string): string {
  if (!disposition) return defaut;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8) {
    try { return decodeURIComponent(utf8[1].trim()); } catch { /* malformed: try the plain form */ }
  }
  const simple = /filename="?([^";]+)"?/i.exec(disposition);
  return simple ? simple[1].trim() : defaut;
}

/** Saves a downloaded file under the given name (temporary object URL, released right after). */
function enregistrer(blob: Blob, nom: string): void {
  const url = URL.createObjectURL(blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nom;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  // released later: some browsers still read the link just after the click
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** An error body comes back as a Blob (the request asked for a file): read it to show the server's message. */
async function lireErreur(e: unknown): Promise<string> {
  if (e instanceof HttpErrorResponse && e.error instanceof Blob) {
    try {
      const corps = JSON.parse(await e.error.text());
      return messageErreur(new HttpErrorResponse({ error: corps, status: e.status, statusText: e.statusText }));
    } catch { /* not JSON */ }
  }
  return messageErreur(e);
}

/**
 * "Exporter : Excel | PDF" for a list, with the filters on screen. The file is built by the server
 * (same rights and filters as the list, recorded in the audit journal) and downloaded with the session token.
 */
@Component({
  selector: 'app-export-liste',
  imports: [ButtonModule],
  template: `
    <div class="export" role="group" aria-label="Exporter la liste">
      <span class="libelle" aria-hidden="true">Exporter</span>
      <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" size="small" ariaLabel="Exporter en Excel"
                [loading]="enCours() === 'xlsx'" [disabled]="!!enCours() && enCours() !== 'xlsx'" (onClick)="exporter('xlsx')" />
      <p-button label="PDF" icon="pi pi-file-pdf" severity="secondary" size="small" ariaLabel="Exporter en PDF"
                [loading]="enCours() === 'pdf'" [disabled]="!!enCours() && enCours() !== 'pdf'" (onClick)="exporter('pdf')" />
      @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
      <p class="annonce" aria-live="polite">{{ annonce() }}</p>
    </div>
  `,
  styles: `
    .export { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--rh-space-2); }
    .libelle { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .annonce { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; margin: 0; }
    .alerte { flex-basis: 100%; margin: 0; max-width: 42ch; margin-left: auto; }
  `,
})
export class ExportListe {
  private readonly api = inject(RhApi);

  readonly liste = input.required<ListeExport>();
  /** The list's current filters, with the API's parameter names; empty values are left out. */
  readonly filtres = input<FiltresExport>({});

  protected readonly enCours = signal<FormatExport | null>(null);
  protected readonly erreur = signal('');
  /** Read out to screen readers once the file is saved. */
  protected readonly annonce = signal('');

  protected async exporter(format: FormatExport): Promise<void> {
    if (this.enCours()) return;
    this.enCours.set(format);
    this.erreur.set('');
    this.annonce.set('');
    try {
      const reponse = await firstValueFrom(this.api.exporter(this.liste(), format, this.filtres()));
      if (!reponse.body || reponse.body.size === 0) {
        this.erreur.set('Le fichier reçu est vide. Réessayez dans un instant.');
        return;
      }
      const n = new Date();
      const jour = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`; // local date, not UTC
      const nom = nomFichier(reponse.headers.get('Content-Disposition'), `${this.liste()}-${jour}.${format}`);
      enregistrer(reponse.body, nom);
      this.annonce.set(`Fichier téléchargé : ${nom}`);
    } catch (e) {
      this.erreur.set(await lireErreur(e));
    } finally {
      this.enCours.set(null);
    }
  }
}
