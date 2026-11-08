import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { PasswordModule } from 'primeng/password';
import { environment } from '../../environments/environment';
import { messageErreur } from '../core/api/erreurs';
import { MESSAGE_MOT_DE_PASSE } from '../core/auth/auth.models';
import { formNouveauMotDePasse } from '../core/auth/mot-de-passe';

/** What differs between the two email links that let someone choose a password. */
interface Variante {
  titre: string;
  intro: string;
  bouton: string;
  succes: string;
  endpoint: string;
  champ: 'motDePasse' | 'nouveauMotDePasse';
}

const VARIANTES: Record<'activation' | 'reinitialisation', Variante> = {
  activation: {
    titre: 'Activer votre compte',
    intro: 'Choisissez le mot de passe de votre compte RH App.',
    bouton: 'Activer mon compte',
    succes: 'Votre compte est activé. Vous pouvez maintenant vous connecter.',
    endpoint: 'activation',
    champ: 'motDePasse',
  },
  reinitialisation: {
    titre: 'Nouveau mot de passe',
    intro: 'Choisissez un nouveau mot de passe. Vos autres sessions seront fermées.',
    bouton: 'Enregistrer le mot de passe',
    succes: 'Votre mot de passe est modifié. Vous pouvez maintenant vous connecter.',
    endpoint: 'reinitialisation',
    champ: 'nouveauMotDePasse',
  },
};

/**
 * /activate-account?token=… and /reset-password?token=… (links sent by email).
 * The route data "variante" picks the wording and endpoint.
 */
@Component({
  selector: 'app-lien-mot-de-passe',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, PasswordModule],
  template: `
    <h1 class="auth-titre">{{ v.titre }}</h1>
    @if (!jeton) {
      <div class="auth-form">
        <p class="alerte erreur" role="alert">Ce lien est incomplet. Ouvrez le lien reçu par email tel quel, ou demandez-en un nouveau.</p>
        <p class="auth-liens"><a routerLink="/forgot-password">Recevoir un nouveau lien</a></p>
      </div>
    } @else if (termine()) {
      <div class="auth-form">
        <p class="alerte succes" role="status">{{ v.succes }}</p>
        <p class="auth-liens"><a routerLink="/login">Se connecter</a></p>
      </div>
    } @else {
      <p class="auth-sous-titre">{{ v.intro }}</p>
      <form class="auth-form" [formGroup]="form" (ngSubmit)="valider()" novalidate>
        @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
        <div class="champ">
          <label for="nouveau">Mot de passe</label>
          <p-password inputId="nouveau" formControlName="nouveau" [feedback]="false" [toggleMask]="true" autocomplete="new-password" />
          <span [class]="form.controls.nouveau.invalid && form.controls.nouveau.touched ? 'err' : 'aide'">{{ regle }}</span>
        </div>
        <div class="champ">
          <label for="confirmation">Confirmation</label>
          <p-password inputId="confirmation" formControlName="confirmation" [feedback]="false" [toggleMask]="true" autocomplete="new-password" />
          @if (form.controls.confirmation.invalid && form.controls.confirmation.touched) { <span class="err">Les deux mots de passe doivent être identiques.</span> }
        </div>
        <p-button type="submit" [label]="envoi() ? 'Enregistrement…' : v.bouton" [loading]="envoi()" />
      </form>
    }
  `,
})
export class LienMotDePasse {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute).snapshot;

  protected readonly v = VARIANTES[this.route.data['variante'] as keyof typeof VARIANTES];
  protected readonly jeton = this.route.queryParamMap.get('token');
  protected readonly regle = MESSAGE_MOT_DE_PASSE;
  protected readonly form = formNouveauMotDePasse();
  protected readonly envoi = signal(false);
  protected readonly termine = signal(false);
  protected readonly erreur = signal('');

  protected async valider(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true);
    this.erreur.set('');
    try {
      await firstValueFrom(this.http.post(`${environment.apiUrl}/api/auth/${this.v.endpoint}`,
        { jeton: this.jeton, [this.v.champ]: this.form.getRawValue().nouveau }));
      this.termine.set(true);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.envoi.set(false);
    }
  }
}
