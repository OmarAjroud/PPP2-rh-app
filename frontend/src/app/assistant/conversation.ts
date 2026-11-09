import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RhApi } from '../core/api/rh-api';
import { AuthService } from '../core/auth/auth.service';

/** The API accepts at most 20 messages per question (history + the new question). */
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
 * Each question sends the whole exchange, the new question last. Forgotten on logout.
 */
@Injectable({ providedIn: 'root' })
export class Conversation {
  private readonly api = inject(RhApi);

  readonly messages = signal<MessageChat[]>([]);
  readonly enCours = signal(false);
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

  vider(): void {
    this.messages.set([]);
  }

  /** Sends the question; on failure it is taken back out and the error is passed on. */
  async envoyer(question: string): Promise<void> {
    if (this.enCours()) throw new Error('Une question est déjà en cours.');
    if (this.pleine()) throw new Error('La conversation est pleine : commencez-en une nouvelle.');
    const avant = this.messages();
    const nouvelle: MessageChat = { role: 'utilisateur', contenu: question };
    this.messages.set([...avant, nouvelle]);
    this.enCours.set(true);
    try {
      const r = await firstValueFrom(this.api.questionAssistant([...avant, nouvelle].map(({ role, contenu }) => ({ role, contenu }))));
      this.messages.update(m => [...m, { role: 'assistant', contenu: r.reponse, outils: r.outilsUtilises }]);
    } catch (e) {
      this.messages.set(avant);
      throw e;
    } finally {
      this.enCours.set(false);
    }
  }
}
