import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth.service';
import { messageErreur } from '../core/api/erreurs';

@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, InputTextModule, PasswordModule],
  template: `
    <h1 class="auth-titre">Connexion</h1>
    <p class="auth-sous-titre">Accédez à votre espace RH.</p>
    <form class="auth-form" [formGroup]="form" (ngSubmit)="valider()" novalidate>
      @if (expiree) { <p class="alerte info">Votre session a expiré. Reconnectez-vous pour continuer.</p> }
      @if (inactif) { <p class="alerte info">Vous avez été déconnecté après 15 minutes d’inactivité. Reconnectez-vous pour continuer.</p> }
      @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
      <div class="champ">
        <label for="nom">Nom d'utilisateur</label>
        <input pInputText id="nom" formControlName="nomUtilisateur" autocomplete="username" [attr.aria-invalid]="invalide('nomUtilisateur')">
        @if (invalide('nomUtilisateur')) { <span class="err">Indiquez votre nom d'utilisateur.</span> }
      </div>
      <div class="champ">
        <label for="mdp">Mot de passe</label>
        <p-password inputId="mdp" formControlName="motDePasse" [feedback]="false" [toggleMask]="true" autocomplete="current-password" />
        @if (invalide('motDePasse')) { <span class="err">Indiquez votre mot de passe.</span> }
      </div>
      <p class="auth-liens"><a routerLink="/forgot-password">Mot de passe oublié ?</a></p>
      <p-button type="submit" [label]="envoi() ? 'Connexion…' : 'Se connecter'" [loading]="envoi()" />
    </form>
    @if (comptesDemo) {
      <p class="auth-pied">Comptes de démonstration : <code>admin</code><code>rh</code><code>manager</code><code>employe</code> · mot de passe <code>Test1234!</code></p>
    }
  `,
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly params = inject(ActivatedRoute).snapshot.queryParamMap;

  protected readonly comptesDemo = environment.comptesDemo;
  protected readonly expiree = this.params.has('expired');
  protected readonly inactif = this.params.has('idle');
  protected readonly erreur = signal('');
  protected readonly envoi = signal(false);
  protected readonly form = new FormGroup({
    nomUtilisateur: new FormControl('', { nonNullable: true, validators: Validators.required }),
    motDePasse: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  protected invalide(nom: 'nomUtilisateur' | 'motDePasse'): boolean {
    const c = this.form.controls[nom];
    return c.invalid && c.touched;
  }

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
