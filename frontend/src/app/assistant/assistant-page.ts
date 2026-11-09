import { AfterViewChecked, Component, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { RhApi } from '../core/api/rh-api';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';
import { Conversation, LIMITE_CARACTERES } from './conversation';

type Statut = 'verification' | 'disponible' | 'indisponible';

const OUTILS: Record<string, string> = {
  lister_departements: 'Départements',
  rechercher_employes: 'Recherche d’employés',
  consulter_employe: 'Fiche employé',
  statistiques_departement: 'Statistiques du département',
  solde_conges: 'Soldes de congés',
  demandes_en_attente: 'Demandes en attente',
  absences_periode: 'Absences sur la période',
};

const EXEMPLES_EMPLOYE = [
  'Combien de jours de congé me reste-t-il ?',
  'Qui est mon manager ?',
  'Quels sont les départements de l’entreprise ?',
];
const EXEMPLES_DECIDEUR = [
  'Quelles demandes attendent une décision ?',
  'Qui est absent la semaine prochaine ?',
  'Combien de personnes compte mon département ?',
];

/**
 * HR assistant: questions in plain language, answered from the app's data through the API's tools.
 * Answers are shown as text, never as HTML; the tools used are listed under each answer.
 */
@Component({
  selector: 'app-assistant-page',
  imports: [ButtonModule, TextareaModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><h1 class="page-titre">Assistant RH</h1></div>
        @if (conv.messages().length) {
          <p-button label="Nouvelle conversation" severity="secondary" icon="pi pi-plus" [disabled]="conv.enCours()" (onClick)="nouvelle()" />
        }
      </header>

      @switch (statut()) {
        @case ('verification') { <p class="hint" aria-busy="true">Connexion à l’assistant…</p> }
        @case ('indisponible') {
          <div class="indisponible">
            <p><strong>L’assistant n’est pas disponible pour le moment.</strong></p>
            <p class="hint">Le moteur de réponses ne répond pas. Réessayez plus tard ; le reste de l’application fonctionne normalement.</p>
          </div>
        }
        @case ('disponible') {
          <section class="fil" #fil aria-label="Conversation" aria-live="polite">
            @for (m of conv.messages(); track $index) {
              <article class="message" [class.assistant]="m.role === 'assistant'" [class.utilisateur]="m.role === 'utilisateur'">
                <span class="auteur">{{ m.role === 'assistant' ? 'Assistant' : 'Vous' }}</span>
                <p class="texte">{{ m.contenu }}</p>
                @if (m.outils?.length) {
                  <p class="outils"><span class="hint">Données consultées :</span> @for (o of m.outils; track $index) { <span class="tag">{{ libelleOutil(o) }}</span> }</p>
                }
              </article>
            } @empty {
              <div class="accueil">
                <p><strong>Posez une question sur les congés, les absences ou les équipes.</strong></p>
                <p class="hint">L’assistant répond à partir des données de l’application, dans la limite de vos droits.</p>
                <div class="exemples">
                  @for (e of exemples(); track e) { <button type="button" (click)="envoyer(e)">{{ e }}</button> }
                </div>
              </div>
            }
            @if (conv.enCours()) {
              <div class="attente"><p role="status"><span class="points" aria-hidden="true"></span>L’assistant réfléchit…</p><p-button label="Annuler" severity="secondary" [text]="true" size="small" (onClick)="annuler()" /></div>
            }
          </section>

          <form class="saisie" (submit)="$event.preventDefault(); envoyer()">
            @if (conv.erreur()) { <p class="alerte erreur" role="alert">{{ conv.erreur() }}</p> }
            @if (conv.pleine()) {
              <p class="alerte info">La conversation a atteint 20 messages : commencez une nouvelle conversation pour continuer.</p>
            }
            <div class="ligne-saisie">
              <textarea pTextarea #champ rows="2" [maxlength]="limite" [value]="question()" (input)="question.set($any($event.target).value)"
                        (keydown)="touche($event)" [disabled]="conv.pleine()" placeholder="Votre question…" aria-label="Votre question à l’assistant"></textarea>
              <p-button type="submit" icon="pi pi-send" ariaLabel="Envoyer" [loading]="conv.enCours()" [disabled]="!peutEnvoyer()" />
            </div>
            <p class="aide hint">Entrée pour envoyer, Maj + Entrée pour aller à la ligne · <span class="mono">{{ question().length }}/{{ limite }}</span></p>
          </form>
        }
      }
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: 860px; grid-template-rows: auto 1fr auto; min-height: calc(100vh - 120px); }
    .indisponible, .accueil { display: grid; gap: var(--rh-space-2); padding: var(--rh-space-6) 0; }
    .fil { display: flex; flex-direction: column; gap: var(--rh-space-4); overflow-y: auto; padding-bottom: var(--rh-space-2); }
    .message { display: grid; gap: 4px; max-width: 85%; }
    .message.utilisateur { align-self: flex-end; padding: var(--rh-space-3) var(--rh-space-4); border-radius: var(--rh-radius); background: var(--rh-surface-3); }
    .message.assistant { align-self: flex-start; padding-left: var(--rh-space-4); border-left: 2px solid var(--rh-accent); }
    .auteur { font-size: var(--rh-text-sm); font-weight: 600; color: var(--rh-muted); }
    .texte { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--rh-ink); line-height: 1.6; }
    .outils { display: flex; flex-wrap: wrap; align-items: center; gap: var(--rh-space-1) var(--rh-space-2); margin-top: var(--rh-space-1); }
    .exemples { display: flex; flex-wrap: wrap; gap: var(--rh-space-2); margin-top: var(--rh-space-3); }
    .exemples button {
      padding: 8px 14px; border: 1px solid var(--rh-line); border-radius: 999px; background: var(--rh-surface);
      color: var(--rh-ink-2); font: inherit; cursor: pointer; transition: background var(--rh-fast) var(--rh-ease), border-color var(--rh-fast) var(--rh-ease);
    }
    .exemples button:hover { background: var(--rh-surface-2); border-color: var(--rh-line-strong); color: var(--rh-ink); }
    .attente { display: flex; align-items: center; gap: var(--rh-space-3); color: var(--rh-muted); }
    .attente p { display: flex; align-items: center; gap: var(--rh-space-2); margin: 0; }
    .points { width: 8px; height: 8px; border-radius: 50%; background: var(--rh-accent); animation: pulse 1.2s ease-in-out infinite; }
    @keyframes pulse { 0%, 100% { opacity: 0.25; } 50% { opacity: 1; } }
    @media (prefers-reduced-motion: reduce) { .points { animation: none; } }
    .saisie { position: sticky; bottom: 0; display: grid; gap: var(--rh-space-2); padding-top: var(--rh-space-3); border-top: 1px solid var(--rh-line); background: var(--rh-surface); }
    .ligne-saisie { display: flex; align-items: flex-end; gap: var(--rh-space-2); }
    .ligne-saisie textarea { flex: 1; resize: none; max-height: 200px; }
    .aide { font-size: var(--rh-text-sm); }
    @media (max-width: 600px) { .message { max-width: 100%; } }
  `,
})
export class AssistantPage implements AfterViewChecked {
  private readonly api = inject(RhApi);
  private readonly auth = inject(AuthService);
  protected readonly conv = inject(Conversation);
  protected readonly limite = LIMITE_CARACTERES;
  private readonly fil = viewChild<ElementRef<HTMLElement>>('fil');
  private readonly champ = viewChild<ElementRef<HTMLTextAreaElement>>('champ');
  private dernierNombre = -1;

  protected readonly statut = toSignal(
    this.api.statutAssistant().pipe(map((s): Statut => (s.disponible ? 'disponible' : 'indisponible')), catchError(() => of<Statut>('indisponible'))),
    { initialValue: 'verification' as Statut },
  );
  protected readonly exemples = computed(() => (this.auth.profil()?.role === 'EMPLOYE' ? EXEMPLES_EMPLOYE : EXEMPLES_DECIDEUR));
  protected readonly question = signal('');
  protected readonly peutEnvoyer = computed(() => !!this.question().trim() && !this.conv.enCours() && !this.conv.pleine());

  constructor() {
    // A question the service gave back (failure, cancellation, even while this page was closed) returns to the input
    effect(() => {
      const q = this.conv.questionRendue();
      if (q === null) return;
      untracked(() => {
        this.question.set(q);
        this.conv.questionRendue.set(null);
      });
    });
  }

  /** Keeps the latest message in view as the conversation grows. */
  ngAfterViewChecked(): void {
    const n = this.conv.messages().length + (this.conv.enCours() ? 1 : 0);
    const fil = this.fil()?.nativeElement;
    if (fil && n !== this.dernierNombre) {
      this.dernierNombre = n;
      fil.lastElementChild?.scrollIntoView?.({ block: 'end' });
    }
  }

  protected libelleOutil(nom: string): string {
    return OUTILS[nom] ?? nom.replace(/_/g, ' ');
  }

  protected touche(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      void this.envoyer();
    }
  }

  protected nouvelle(): void {
    this.conv.vider();
    this.champ()?.nativeElement.focus();
  }

  protected annuler(): void {
    this.conv.annuler();
    this.champ()?.nativeElement.focus();
  }

  protected async envoyer(texte?: string): Promise<void> {
    const q = (texte ?? this.question()).trim();
    if (!q || this.conv.enCours() || this.conv.pleine()) return;
    this.question.set('');
    await this.conv.envoyer(q);
    this.champ()?.nativeElement.focus();
  }
}
