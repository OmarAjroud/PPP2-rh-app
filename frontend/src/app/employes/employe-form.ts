import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { optionsRoles } from '../core/api/libelles';
import { EmployeRequest } from '../core/api/models';
import { appliquerErreursChamps, messageErreur } from '../core/api/erreurs';
import { Apparition } from '../core/motion/motion';

/** /employees/new and /employees/:id/edit (ADMIN, RH). */
/** At least 8 digits, with spaces, dots, dashes or brackets and an optional leading +: "+216 22 345 678", "22.345.678". Same rule as the API. */
const TELEPHONE = /^\s*\+?(?=(?:\D*\d){8})[0-9][0-9 .()\u00a0\u202f-]{6,24}[0-9]\s*$/;

@Component({
  selector: 'app-employe-form',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, DatePickerModule, InputNumberModule, InputTextModule, SelectModule, Apparition],
  templateUrl: './employe-form.html',
  styleUrl: './employe-form.scss',
})
export class EmployeForm {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  protected readonly id = numeroOuNull(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  protected readonly departements = toSignal(this.api.departements().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly roles = toSignal(this.api.roles().pipe(map(optionsRoles), catchError(() => of([]))), { initialValue: [] });
  /** Possible managers: everyone except the person being edited. */
  protected readonly managers = toSignal(
    this.api.employes({ page: 0, size: 500, sort: 'nom,asc' }).pipe(
      map(p => p.contenu.filter(e => e.id !== this.id).map(e => ({ label: `${e.prenom} ${e.nom}`, value: e.id }))),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );

  protected readonly form = new FormGroup({
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    prenom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(150)] }),
    telephone: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(30), Validators.pattern(TELEPHONE)] }),
    dateEmbauche: new FormControl<Date | null>(null, Validators.required),
    salaire: new FormControl<number | null>(null, [Validators.min(0), Validators.max(99999999.999)]),
    departementId: new FormControl<number | null>(null, Validators.required),
    roleId: new FormControl<number | null>(null, Validators.required),
    managerId: new FormControl<number | null>(null),
  });

  protected readonly chargement = signal(this.id !== null);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal('');

  constructor() {
    if (this.id !== null) {
      firstValueFrom(this.api.employe(this.id)).then(e => {
        this.form.setValue({
          nom: e.nom, prenom: e.prenom, email: e.email, telephone: e.telephone ?? '',
          dateEmbauche: new Date(`${e.dateEmbauche}T00:00:00`),
          salaire: e.salaire, departementId: e.departementId, roleId: e.roleId, managerId: e.managerId,
        });
      }).catch(err => this.erreur.set(messageErreur(err))).finally(() => this.chargement.set(false));
    }
  }

  /** Missing or malformed phone number: two different messages. */
  protected erreurTelephone(): string | null {
    const c = this.form.controls.telephone;
    if (!c.invalid || !c.touched) return null;
    if (c.errors?.['serveur']) return c.errors['serveur'] as string;
    return c.errors?.['required'] ? 'Indiquez un numéro de téléphone.' : 'Numéro non valide : au moins 8 chiffres (ex. +216 22 345 678).';
  }

  /** Field message: the server's when it sent one, else the local rule. */
  protected erreurChamp(nom: keyof typeof this.form.controls, defaut: string): string | null {
    const c = this.form.controls[nom];
    if (!c.invalid || !c.touched) return null;
    return (c.errors?.['serveur'] as string | undefined) ?? defaut;
  }

  protected async enregistrer(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const requete: EmployeRequest = {
      nom: v.nom.trim(), prenom: v.prenom.trim(), email: v.email.trim(), telephone: v.telephone.trim().replace(/\s+/g, ' '),
      dateEmbauche: isoLocal(v.dateEmbauche!),
      salaire: v.salaire, departementId: v.departementId!, roleId: v.roleId!, managerId: v.managerId,
    };
    this.envoi.set(true);
    this.erreur.set('');
    try {
      const e = await firstValueFrom(this.id === null ? this.api.creerEmploye(requete) : this.api.modifierEmploye(this.id, requete));
      await this.router.navigate(['/employees', e.id]);
    } catch (err) {
      if (!appliquerErreursChamps(this.form, err)) this.erreur.set(messageErreur(err));
    } finally {
      this.envoi.set(false);
    }
  }
}

/** yyyy-MM-dd in local time (toISOString would shift the day in UTC+1). */
function isoLocal(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function numeroOuNull(v: string | null): number | null {
  return v === null ? null : Number(v);
}
