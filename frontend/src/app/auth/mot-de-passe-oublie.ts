import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { environment } from '../../environments/environment';
import { messageErreur } from '../core/api/erreurs';

@Component({
  selector: 'app-mot-de-passe-oublie',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel carte-auth" aria-labelledby="titre">
      <h1 id="titre" class="panel-h">Mot de passe oublié</h1>
      <div class="panel-b">
        @if (envoye()) {
          <p class="alerte succes" role="status">{{ envoye() }}</p>
          <p class="hint">Le lien est valable une heure. Pensez à vérifier les courriers indésirables.</p>
        } @else {
          <p class="hint">Indiquez l'adresse email de votre compte : nous vous enverrons un lien pour choisir un nouveau mot de passe.</p>
          @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
          <form (ngSubmit)="valider()" novalidate>
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" [formControl]="email" autocomplete="email" required>
              <mat-error>Indiquez une adresse email valide.</mat-error>
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="envoi()">{{ envoi() ? 'Envoi…' : 'Recevoir le lien' }}</button>
          </form>
        }
        <p class="liens"><a routerLink="/login">Retour à la connexion</a></p>
      </div>
    </section>
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
