import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { REGLE_MOT_DE_PASSE } from './auth.models';

/** New password + confirmation, used by activation, reset and password change. */
export function formNouveauMotDePasse() {
  return new FormGroup(
    {
      nouveau: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(REGLE_MOT_DE_PASSE)] }),
      confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: confirmationIdentique },
  );
}

function confirmationIdentique(g: AbstractControl): ValidationErrors | null {
  const confirmation = g.get('confirmation');
  if (!confirmation?.value) return null;
  const differente = g.get('nouveau')?.value !== confirmation.value;
  // The message belongs on the confirmation field, where the user fixes it
  confirmation.setErrors(differente ? { ...(confirmation.errors ?? {}), differente: true } : withoutKey(confirmation.errors, 'differente'));
  return null;
}

function withoutKey(errors: ValidationErrors | null, key: string): ValidationErrors | null {
  if (!errors) return null;
  const { [key]: _, ...reste } = errors;
  return Object.keys(reste).length ? reste : null;
}
