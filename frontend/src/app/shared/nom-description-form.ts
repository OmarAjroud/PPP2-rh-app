import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { Apparition } from '../core/motion/motion';
import { RhApi } from '../core/api/rh-api';
import { NomDescription } from '../core/api/models';
import { appliquerErreursChamps, messageErreur } from '../core/api/erreurs';

interface Config {
  liste: string;          // route of the list, e.g. /departments
  libelleListe: string;   // breadcrumb
  titreAjout: string;
  titreModif: string;
  boutonAjout: string;
  maxNom: number;         // backend @Size on "nom"
  lire: (api: RhApi, id: number) => Observable<NomDescription>;
  creer: (api: RhApi, v: NomDescription) => Observable<{ id: number }>;
  modifier: (api: RhApi, id: number, v: NomDescription) => Observable<{ id: number }>;
}

const CONFIGS: Record<'departement' | 'role', Config> = {
  departement: {
    liste: '/departments', libelleListe: 'Départements', titreAjout: 'Ajouter un département', titreModif: 'Modifier le département',
    boutonAjout: 'Ajouter le département', maxNom: 100,
    lire: (a, id) => a.departement(id), creer: (a, v) => a.creerDepartement(v), modifier: (a, id, v) => a.modifierDepartement(id, v),
  },
  role: {
    liste: '/roles', libelleListe: 'Rôles', titreAjout: 'Ajouter un rôle', titreModif: 'Modifier le rôle',
    boutonAjout: 'Ajouter le rôle', maxNom: 50,
    lire: (a, id) => a.role(id), creer: (a, v) => a.creerRole(v), modifier: (a, id, v) => a.modifierRole(id, v),
  },
};

/** Create/edit form for departments and roles (both are a name and a description). Route data "entite" picks which. */
@Component({
  selector: 'app-nom-description-form',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, InputTextModule, TextareaModule, Apparition],
  template: `
    <header class="entete">
      <div>
        <p class="crumb"><a [routerLink]="c.liste">{{ c.libelleListe }}</a></p>
        <h1 class="page-titre">{{ id === null ? c.titreAjout : c.titreModif }}</h1>
      </div>
    </header>
    @if (chargement()) {
      <p class="hint" aria-busy="true">Chargement…</p>
    } @else {
      <form class="card" [formGroup]="form" (ngSubmit)="enregistrer()" novalidate rhApparition>
        <div class="card-b">
          @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
          <div class="champ">
            <label for="nom">Nom</label>
            <input pInputText id="nom" formControlName="nom" autocomplete="off" [attr.maxlength]="c.maxNom">
            @if (form.controls.nom.invalid && form.controls.nom.touched) {
              <span class="err">{{ form.controls.nom.errors?.['serveur'] ?? 'Le nom est obligatoire (' + c.maxNom + ' caractères au plus).' }}</span>
            }
          </div>
          <div class="champ">
            <label for="description">Description</label>
            <textarea pTextarea id="description" formControlName="description" rows="3" maxlength="255"></textarea>
            @if (form.controls.description.errors?.['serveur']; as m) { <span class="err">{{ m }}</span> } @else { <span class="aide">Facultatif, 255 caractères au plus.</span> }
          </div>
        </div>
        <div class="card-f">
          <span></span>
          <div class="actions">
            <a pButton severity="secondary" [routerLink]="id === null ? [c.liste] : [c.liste, id]">Annuler</a>
            <p-button type="submit" [label]="envoi() ? 'Enregistrement…' : (id === null ? c.boutonAjout : 'Enregistrer')" [loading]="envoi()" />
          </div>
        </div>
      </form>
    }
  `,
  styles: `
    :host { display: grid; gap: var(--rh-space-4); --page-max: 640px; }
    :host > :not(.entete) { max-width: var(--page-max); }
  `,
})
export class NomDescriptionForm {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute).snapshot;
  protected readonly c = CONFIGS[this.route.data['entite'] as 'departement' | 'role'];
  protected readonly id = this.route.paramMap.has('id') ? Number(this.route.paramMap.get('id')) : null;

  protected readonly form = new FormGroup({
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(this.c.maxNom)] }),
    description: new FormControl('', { nonNullable: true, validators: Validators.maxLength(255) }),
  });
  protected readonly chargement = signal(this.id !== null);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal('');

  constructor() {
    if (this.id !== null) {
      firstValueFrom(this.c.lire(this.api, this.id))
        .then(v => this.form.setValue({ nom: v.nom, description: v.description ?? '' }))
        .catch(err => this.erreur.set(messageErreur(err)))
        .finally(() => this.chargement.set(false));
    }
  }

  protected async enregistrer(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const corps: NomDescription = { nom: v.nom.trim(), description: v.description.trim() || null };
    this.envoi.set(true);
    this.erreur.set('');
    try {
      const r = await firstValueFrom(this.id === null ? this.c.creer(this.api, corps) : this.c.modifier(this.api, this.id, corps));
      await this.router.navigate([this.c.liste, r.id]);
    } catch (err) {
      if (!appliquerErreursChamps(this.form, err)) this.erreur.set(messageErreur(err));
    } finally {
      this.envoi.set(false);
    }
  }
}
