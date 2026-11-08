import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel carte-auth" aria-labelledby="titre">
      <h1 id="titre" class="panel-h">{{ v.titre }}</h1>
      <div class="panel-b">
        @if (!jeton) {
          <p class="alerte erreur" role="alert">Ce lien est incomplet. Ouvrez le lien reçu par email tel quel, ou demandez-en un nouveau.</p>
          <p class="liens"><a routerLink="/forgot-password">Recevoir un nouveau lien</a></p>
        } @else if (termine()) {
          <p class="alerte succes" role="status">{{ v.succes }}</p>
          <p class="liens"><a routerLink="/login">Se connecter</a></p>
        } @else {
          <p class="hint">{{ v.intro }}</p>
          @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
          <form [formGroup]="form" (ngSubmit)="valider()" novalidate>
            <mat-form-field>
              <mat-label>Mot de passe</mat-label>
              <input matInput type="password" formControlName="nouveau" autocomplete="new-password" required>
              <mat-hint>{{ regle }}</mat-hint>
              <mat-error>{{ regle }}</mat-error>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Confirmation</mat-label>
              <input matInput type="password" formControlName="confirmation" autocomplete="new-password" required>
              <mat-error>Les deux mots de passe doivent être identiques.</mat-error>
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="envoi()">{{ envoi() ? 'Enregistrement…' : v.bouton }}</button>
          </form>
        }
      </div>
    </section>
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
