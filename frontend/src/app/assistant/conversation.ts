import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { RhApi } from '../core/api/rh-api';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';

/** The API accepts at most 20 messages per question (history + the new question), 4000 characters each. */
export const LIMITE_MESSAGES = 20;
export const LIMITE_CARACTERES = 4000;

export interface MessageChat {
  role: 'utilisateur' | 'assistant';
  contenu: string;
  /** Tools the assistant used for this answer. */
  outils?: string[];
}

/**
 * The conversation with the HR assistant, kept for the session (the API keeps none).
 * Each question sends the whole exchange, the new question last.
 * The question being answered belongs to this conversation: clearing it (new conversation,
 * logout, another person) or cancelling drops the request, so a late answer never lands elsewhere.
 * The error and the question given back live here too, so leaving the page loses neither.
 */
@Injectable({ providedIn: 'root' })
export class Conversation {
  private readonly api = inject(RhApi);
  private requete: Subscription | undefined;
  private finRequete: (() => void) | undefined;
  private enAttente: { avant: MessageChat[]; question: string } | undefined;

  readonly messages = signal<MessageChat[]>([]);
  readonly enCours = signal(false);
  /** Last error, shown until the next question. */
  readonly erreur = signal('');
  /** A question that could not be answered, to put back in the input. */
  readonly questionRendue = signal<string | null>(null);
  /** A new question would exceed the API limit: start a new conversation. */
  readonly pleine = computed(() => this.messages().length + 1 > LIMITE_MESSAGES);

  constructor() {
    const auth = inject(AuthService);
    // Keyed on the person, not the session object: a token refresh (here or in another tab) keeps the exchange,
    // a logout or another person never sees it
    const personne = computed(() => auth.profil()?.nomUtilisateur ?? null);
    effect(() => {
      personne();
      untracked(() => this.vider());
    });
  }

  /** New conversation (also on logout): drops the question being answered, if any. */
  vider(): void {
    this.arreterRequete();
    this.messages.set([]);
    this.erreur.set('');
    this.questionRendue.set(null);
  }

  /** Stops waiting for the current answer: the question is taken back out and given back. */
  annuler(): void {
    const attente = this.enAttente;
    if (!attente) return;
    this.arreterRequete();
    this.messages.set(attente.avant);
    this.questionRendue.set(attente.question);
  }

  /** Sends the question. Resolves once answered, failed or cancelled; never rejects. */
  envoyer(question: string): Promise<void> {
    if (this.enCours() || this.pleine()) return Promise.resolve();
    const avant = this.messages();
    const nouvelle: MessageChat = { role: 'utilisateur', contenu: question };
    // Long answers are shortened in what is sent back: the API refuses messages over 4000 characters
    const envoyes = [...avant, nouvelle].map(({ role, contenu }) => ({ role, contenu: contenu.slice(0, LIMITE_CARACTERES) }));
    this.messages.set([...avant, nouvelle]);
    this.erreur.set('');
    this.questionRendue.set(null);
    this.enCours.set(true);
    this.enAttente = { avant, question };
    return new Promise<void>(fin => {
      this.finRequete = fin;
      this.requete = this.api.questionAssistant(envoyes).subscribe({
        next: r => {
          this.messages.update(m => [...m, { role: 'assistant', contenu: r.reponse, outils: r.outilsUtilises }]);
          this.terminer();
        },
        error: e => {
          this.messages.set(avant);
          this.erreur.set(messageErreur(e));
          this.questionRendue.set(question);
          this.terminer();
        },
      });
    });
  }

  private terminer(): void {
    this.requete = undefined;
    this.enAttente = undefined;
    this.enCours.set(false);
    const fin = this.finRequete;
    this.finRequete = undefined;
    fin?.();
  }

  private arreterRequete(): void {
    this.requete?.unsubscribe();
    this.terminer();
  }
}
