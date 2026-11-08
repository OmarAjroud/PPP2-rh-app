import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../core/auth/auth.service';
import { MESSAGE_MOT_DE_PASSE } from '../core/auth/auth.models';
import { formNouveauMotDePasse } from '../core/auth/mot-de-passe';
import { appliquerErreursChamps, messageErreur } from '../core/api/erreurs';

@Component({
  selector: 'app-securite',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <p class="crumb">Mon compte</p>
    <h1 class="page-titre">Sécurité du compte</h1>

    <form class="panel" [formGroup]="form" (ngSubmit)="changer()" novalidate aria-labelledby="mdp-titre">
      <h2 id="mdp-titre" class="panel-h">Changer de mot de passe</h2>
      <div class="panel-b">
        @if (succes()) { <p class="alerte succes" role="status">Mot de passe modifié. Vos sessions sur les autres appareils sont fermées.</p> }
        @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
        <mat-form-field>
          <mat-label>Mot de passe actuel</mat-label>
          <input matInput type="password" formControlName="motDePasseActuel" autocomplete="current-password" required>
          <mat-error>{{ form.controls.motDePasseActuel.errors?.['serveur'] ?? 'Indiquez votre mot de passe actuel.' }}</mat-error>
        </mat-form-field>
        <ng-container formGroupName="nouveau">
          <mat-form-field>
            <mat-label>Nouveau mot de passe</mat-label>
            <input matInput type="password" formControlName="nouveau" autocomplete="new-password" required>
            <mat-hint>{{ regle }}</mat-hint>
            <mat-error>{{ regle }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Confirmation</mat-label>
            <input matInput type="password" formControlName="confirmation" autocomplete="new-password" required>
            <mat-error>Les deux mots de passe doivent être identiques.</mat-error>
          </mat-form-field>
        </ng-container>
      </div>
      <div class="panel-f">
        <button mat-flat-button type="submit" [disabled]="envoi()">{{ envoi() ? 'Enregistrement…' : 'Changer le mot de passe' }}</button>
      </div>
    </form>

    <section class="panel" aria-labelledby="sessions-titre">
      <h2 id="sessions-titre" class="panel-h">Sessions ouvertes</h2>
      <div class="panel-b">
        <p>Téléphone perdu, ordinateur partagé : fermez toutes vos sessions, sur tous vos appareils. Vous devrez vous reconnecter ici aussi.</p>
        @if (erreurSessions()) { <p class="alerte erreur" role="alert">{{ erreurSessions() }}</p> }
      </div>
      <div class="panel-f">
        @if (confirmer()) {
          <span class="hint">Fermer toutes les sessions maintenant ?</span>
          <button mat-stroked-button type="button" (click)="confirmer.set(false)">Annuler</button>
          <button mat-flat-button type="button" (click)="fermerTout()">Confirmer</button>
        } @else {
          <button mat-stroked-button type="button" (click)="confirmer.set(true)">Fermer toutes mes sessions</button>
        }
      </div>
    </section>
  `,
  styles: `
    :host { display: grid; gap: var(--rh-space-6); max-width: 560px; }
    .panel-b { gap: var(--rh-space-2); }
  `,
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
