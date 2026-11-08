import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { environment } from '../../environments/environment';
import { messageErreur } from '../core/api/erreurs';

@Component({
  selector: 'app-mot-de-passe-oublie',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, InputTextModule],
  template: `
    <h1 class="auth-titre">Mot de passe oublié</h1>
    @if (envoye()) {
      <div class="auth-form">
        <p class="alerte succes" role="status">{{ envoye() }}</p>
        <p class="hint">Le lien est valable une heure. Pensez à vérifier les courriers indésirables.</p>
      </div>
    } @else {
      <p class="auth-sous-titre">Indiquez l'adresse email de votre compte : vous recevrez un lien pour choisir un nouveau mot de passe.</p>
      <form class="auth-form" (ngSubmit)="valider()" novalidate>
        @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
        <div class="champ">
          <label for="email">Email</label>
          <input pInputText id="email" type="email" [formControl]="email" autocomplete="email" [attr.aria-invalid]="email.invalid && email.touched">
          @if (email.invalid && email.touched) { <span class="err">Indiquez une adresse email valide.</span> }
        </div>
        <p-button type="submit" [label]="envoi() ? 'Envoi…' : 'Recevoir le lien'" [loading]="envoi()" />
      </form>
    }
    <p class="auth-pied"><a routerLink="/login">Retour à la connexion</a></p>
  `,
})
export class MotDePasseOublie {
  private readonly http = inject(HttpClient);
  protected readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  protected readonly envoi = signal(false);
  protected readonly envoye = signal('');
  protected readonly erreur = signal('');

  protected async valider(): Promise<void> {
    if (this.email.invalid) { this.email.markAsTouched(); return; }
    this.envoi.set(true);
    this.erreur.set('');
    try {
      const r = await firstValueFrom(this.http.post<{ message: string }>(
        `${environment.apiUrl}/api/auth/mot-de-passe-oublie`, { email: this.email.value.trim() }));
      this.envoye.set(r.message);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.envoi.set(false);
    }
  }
}
