import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth/auth.service';
import { MESSAGE_MOT_DE_PASSE } from '../core/auth/auth.models';
import { formNouveauMotDePasse } from '../core/auth/mot-de-passe';
import { appliquerErreursChamps, messageErreur } from '../core/api/erreurs';
import { Apparition } from '../core/motion/motion';

@Component({
  selector: 'app-securite',
  imports: [ReactiveFormsModule, ButtonModule, PasswordModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete"><div><p class="crumb">Mon compte</p><h1 class="page-titre">Sécurité</h1></div></header>

      <form class="card" [formGroup]="form" (ngSubmit)="changer()" novalidate aria-labelledby="mdp-titre">
        <h2 id="mdp-titre" class="card-h">Changer de mot de passe</h2>
        <div class="card-b">
          @if (succes()) { <p class="alerte succes" role="status">Mot de passe modifié. Vos sessions sur les autres appareils sont fermées.</p> }
          @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
          <div class="champ">
            <label for="actuel">Mot de passe actuel</label>
            <p-password inputId="actuel" formControlName="motDePasseActuel" [feedback]="false" [toggleMask]="true" autocomplete="current-password" />
            @if (form.controls.motDePasseActuel.invalid && form.controls.motDePasseActuel.touched) {
              <span class="err">{{ form.controls.motDePasseActuel.errors?.['serveur'] ?? 'Indiquez votre mot de passe actuel.' }}</span>
            }
          </div>
          <ng-container formGroupName="nouveau">
            <div class="champ">
              <label for="nouveau">Nouveau mot de passe</label>
              <p-password inputId="nouveau" formControlName="nouveau" [feedback]="false" [toggleMask]="true" autocomplete="new-password" />
              <span [class]="form.controls.nouveau.controls.nouveau.invalid && form.controls.nouveau.controls.nouveau.touched ? 'err' : 'aide'">{{ regle }}</span>
            </div>
            <div class="champ">
              <label for="confirmation">Confirmation</label>
              <p-password inputId="confirmation" formControlName="confirmation" [feedback]="false" [toggleMask]="true" autocomplete="new-password" />
              @if (form.controls.nouveau.controls.confirmation.invalid && form.controls.nouveau.controls.confirmation.touched) {
                <span class="err">Les deux mots de passe doivent être identiques.</span>
              }
            </div>
          </ng-container>
        </div>
        <div class="card-f">
          <span>Vos autres sessions sont fermées au changement.</span>
          <div class="actions"><p-button type="submit" [label]="envoi() ? 'Enregistrement…' : 'Changer le mot de passe'" [loading]="envoi()" /></div>
        </div>
      </form>

      <section class="card" aria-labelledby="sessions-titre">
        <h2 id="sessions-titre" class="card-h">Sessions ouvertes</h2>
        <div class="card-b">
          <p>Téléphone perdu, ordinateur partagé : fermez toutes vos sessions, sur tous vos appareils. Vous devrez vous reconnecter ici aussi.</p>
          @if (erreurSessions()) { <p class="alerte erreur" role="alert">{{ erreurSessions() }}</p> }
        </div>
        <div class="card-f">
          @if (confirmer()) {
            <span>Fermer toutes les sessions maintenant ?</span>
            <div class="actions">
              <p-button label="Annuler" severity="secondary" (onClick)="confirmer.set(false)" />
              <p-button label="Confirmer" severity="danger" (onClick)="fermerTout()" />
            </div>
          } @else {
            <div class="actions"><p-button label="Fermer toutes mes sessions" severity="danger" [outlined]="true" (onClick)="confirmer.set(true)" /></div>
          }
        </div>
      </section>
    </div>
  `,
  styles: `.page { display: grid; gap: var(--rh-space-4); --page-max: 640px; }`,
})
export class Securite {
  private readonly auth = inject(AuthService);
  protected readonly regle = MESSAGE_MOT_DE_PASSE;
  protected readonly form = new FormGroup({
    motDePasseActuel: new FormControl('', { nonNullable: true, validators: Validators.required }),
    nouveau: formNouveauMotDePasse(),
  });
  protected readonly envoi = signal(false);
  protected readonly succes = signal(false);
  protected readonly erreur = signal('');
  protected readonly confirmer = signal(false);
  protected readonly erreurSessions = signal('');

  protected async changer(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true);
    this.erreur.set('');
    this.succes.set(false);
    try {
      const v = this.form.getRawValue();
      await this.auth.changerMotDePasse(v.motDePasseActuel, v.nouveau.nouveau);
      this.form.reset();
      this.succes.set(true);
    } catch (e) {
      if (!appliquerErreursChamps(this.form, e)) this.erreur.set(messageErreur(e));
    } finally {
      this.envoi.set(false);
    }
  }

  protected async fermerTout(): Promise<void> {
    this.erreurSessions.set('');
    try {
      await this.auth.logoutPartout();
    } catch (e) {
      this.erreurSessions.set(messageErreur(e));
      this.confirmer.set(false);
    }
  }
}
