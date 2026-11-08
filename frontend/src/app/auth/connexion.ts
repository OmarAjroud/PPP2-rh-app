import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../core/auth/auth.service';
import { messageErreur } from '../core/api/erreurs';

@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel carte-auth" aria-labelledby="titre">
      <h1 id="titre" class="panel-h">Connexion</h1>
      <div class="panel-b">
        @if (expiree) { <p class="alerte info">Votre session a expiré. Reconnectez-vous pour continuer.</p> }
        @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
        <form [formGroup]="form" (ngSubmit)="valider()" novalidate>
          <mat-form-field>
            <mat-label>Nom d'utilisateur</mat-label>
            <input matInput formControlName="nomUtilisateur" autocomplete="username" required>
            <mat-error>Indiquez votre nom d'utilisateur.</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Mot de passe</mat-label>
            <input matInput type="password" formControlName="motDePasse" autocomplete="current-password" required>
            <mat-error>Indiquez votre mot de passe.</mat-error>
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="envoi()">{{ envoi() ? 'Connexion…' : 'Se connecter' }}</button>
        </form>
        <p class="liens"><a routerLink="/forgot-password">Mot de passe oublié ?</a></p>
      </div>
    </section>
  `,
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly params = inject(ActivatedRoute).snapshot.queryParamMap;

  protected readonly expiree = this.params.has('expired');
  protected readonly erreur = signal('');
  protected readonly envoi = signal(false);
  protected readonly form = new FormGroup({
    nomUtilisateur: new FormControl('', { nonNullable: true, validators: Validators.required }),
    motDePasse: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  protected async valider(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.envoi.set(true);
    this.erreur.set('');
    try {
      const { nomUtilisateur, motDePasse } = this.form.getRawValue();
      await this.auth.login(nomUtilisateur.trim(), motDePasse);
      const retour = this.params.get('returnUrl');
      // Only same-app paths, never an external URL
      await this.router.navigateByUrl(retour?.startsWith('/') && !retour.startsWith('//') ? retour : '/');
    } catch (e) {
      // The API's 401 text also covers tokens; on this form only the credentials can be wrong
      this.erreur.set(e instanceof HttpErrorResponse && e.status === 401
        ? 'Nom d’utilisateur ou mot de passe incorrect.'
        : messageErreur(e));
    } finally {
      this.envoi.set(false);
    }
  }
}
