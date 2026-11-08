import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';

/** Error body returned by the API for every failure (backend ErreurApi). */
export interface ErreurApi {
  horodatage: string;
  statut: number;
  erreur: string;
  message: string;
  chemin: string;
  erreursChamps: Record<string, string> | null;
}

function corps(err: unknown): ErreurApi | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const e = err.error as Partial<ErreurApi> | null;
  return e && typeof e === 'object' && typeof e.message === 'string' ? (e as ErreurApi) : null;
}

/** One sentence the user can act on, in French. */
export function messageErreur(err: unknown): string {
  if (err instanceof HttpErrorResponse && err.status === 0) {
    return 'Le serveur ne répond pas. Vérifiez votre connexion puis réessayez.';
  }
  return corps(err)?.message ?? 'Une erreur inattendue est survenue. Réessayez dans un instant.';
}

/**
 * Puts the server's per-field validation messages on the matching form controls
 * (error key "serveur"). Returns true when at least one field received a message.
 */
export function appliquerErreursChamps(form: FormGroup, err: unknown): boolean {
  const champs = corps(err)?.erreursChamps;
  if (!champs) return false;
  let applique = false;
  for (const [nom, message] of Object.entries(champs)) {
    const control: AbstractControl | null = form.get(nom);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), serveur: message });
      control.markAsTouched();
      applique = true;
    }
  }
  return applique;
}
