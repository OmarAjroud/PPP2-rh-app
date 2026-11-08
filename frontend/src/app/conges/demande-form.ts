import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { RhApi } from '../core/api/rh-api';
import { JoursOuvrables, Solde, TypeConge } from '../core/api/models';
import { appliquerErreursChamps, messageErreur } from '../core/api/erreurs';
import { jours } from '../core/api/libelles';
import { Moi } from '../core/auth/moi';
import { Apparition } from '../core/motion/motion';
import { dateIso, soldeApres } from './regles';

type Calcul = { statut: 'calcul' } | { statut: 'ok'; j: JoursOuvrables } | { statut: 'erreur'; message: string };

/**
 * New leave request. The proof is computed as the dates change: calendar days, working days
 * (weekends and public holidays excluded, by the API), the balance before → after, and who decides.
 */
@Component({
  selector: 'app-demande-form',
  imports: [RouterLink, ReactiveFormsModule, ButtonModule, DatePickerModule, SelectModule, TextareaModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb"><a routerLink="/leave-requests">Mes congés</a></p><h1 class="page-titre">Nouvelle demande</h1></div>
      </header>

      <form class="grille" [formGroup]="form" (ngSubmit)="envoyer()" novalidate>
        <section class="card" aria-labelledby="demande-titre">
          <h2 id="demande-titre" class="card-h">Votre demande</h2>
          <div class="card-b">
            @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }
            <div class="champ">
              <label for="type">Type de congé</label>
              <p-select inputId="type" formControlName="typeCongeId" [options]="types()" optionLabel="libelle" optionValue="id" placeholder="Choisir" />
              @if (invalide('typeCongeId')) { <span class="err">Choisissez un type de congé.</span> }
            </div>
            <div class="dates">
              <div class="champ">
                <label for="debut">Premier jour</label>
                <p-datepicker inputId="debut" formControlName="dateDebut" [showIcon]="true" iconDisplay="input" dateFormat="dd/mm/yy" placeholder="jj/mm/aaaa" />
                @if (invalide('dateDebut')) { <span class="err">Indiquez le premier jour.</span> }
              </div>
              <div class="champ">
                <label for="fin">Dernier jour</label>
                <p-datepicker inputId="fin" formControlName="dateFin" [showIcon]="true" iconDisplay="input" dateFormat="dd/mm/yy" placeholder="jj/mm/aaaa" />
                @if (invalide('dateFin')) { <span class="err">Indiquez le dernier jour.</span> }
              </div>
            </div>
            @if (probleme()) { <p class="alerte erreur" role="alert">{{ probleme() }}</p> }
            <div class="champ">
              <label for="commentaire">Commentaire</label>
              <textarea pTextarea id="commentaire" formControlName="commentaire" rows="3" maxlength="500"></textarea>
              <span class="aide">Facultatif. Visible de la personne qui décide.</span>
            </div>
          </div>
          <div class="card-f">
            <span></span>
            <div class="actions">
              <a pButton routerLink="/leave-requests" severity="secondary">Annuler</a>
              <p-button type="submit" [label]="envoi() ? 'Envoi…' : 'Envoyer la demande'" [loading]="envoi()" />
            </div>
          </div>
        </section>

        <aside class="card preuve" aria-labelledby="calcul-titre" aria-live="polite">
          <h2 id="calcul-titre" class="card-h">Le calcul</h2>
          @switch (calcul()?.statut) {
            @case ('calcul') { <p class="vide hint" aria-busy="true">Calcul…</p> }
            @case ('erreur') { <p class="vide alerte erreur">{{ $any(calcul()).message }}</p> }
            @case ('ok') {
              @let j = $any(calcul()).j;
              <div class="ligne"><span>Jours calendaires</span><span class="lecture">{{ j.joursCalendaires }}</span></div>
              <div class="ligne"><span>Week-ends et jours fériés</span><span class="lecture">− {{ j.joursCalendaires - j.joursOuvrables }}</span></div>
              <div class="ligne total"><span><b>{{ jours(j.joursOuvrables) }} ouvrables</b></span><span class="lecture">{{ j.joursOuvrables }}</span></div>
              @if (solde(); as s) {
                <div class="ligne">
                  <span>Solde {{ s.typeCongeLibelle }}<small>disponible → après cette demande</small></span>
                  <span class="lecture">{{ s.joursDisponibles }}<span class="fleche" aria-hidden="true">→</span>{{ apres() }}</span>
                </div>
                @if ((apres() ?? 0) < 0) {
                  <p class="vide alerte erreur" role="alert">Solde insuffisant : il manque {{ jours(-apres()!) }}.</p>
                }
              } @else if (type() && !type()!.deduitDuSolde) {
                <div class="ligne"><span>Solde</span><span class="hint">Non déduit pour ce type</span></div>
              }
            }
            @default { <p class="vide hint">Choisissez les dates : le nombre de jours ouvrables et votre solde s'affichent ici.</p> }
          }
          <div class="ligne decide">
            <span>Qui décide<small>Sans réponse sous 3 jours, la demande est transmise aux RH.</small></span>
            <b>{{ decideur() }}</b>
          </div>
        </aside>
      </form>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: 1040px; }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: var(--rh-space-6); align-items: start; }
    .dates { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--rh-space-4); }
    textarea { width: 100%; resize: vertical; }
    .preuve .total { background: var(--rh-surface-2); }
    .preuve .total .lecture { font-weight: 600; }
    .decide b { text-align: right; font-weight: 500; }
    .vide { margin: var(--rh-space-4) var(--rh-space-5); }
    p.vide.hint { margin: 0; padding: var(--rh-space-4) var(--rh-space-5); }
    @media (max-width: 960px) {
      .grille { grid-template-columns: minmax(0, 1fr); }
    }
    @media (max-width: 560px) {
      .dates { grid-template-columns: minmax(0, 1fr); }
    }
  `,
})
export class DemandeForm {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly moi = inject(Moi);
  private readonly aujourdhui = dateIso(new Date());
  protected readonly jours = jours;

  readonly form = new FormGroup({
    typeCongeId: new FormControl<number | null>(null, Validators.required),
    dateDebut: new FormControl<Date | null>(null, Validators.required),
    dateFin: new FormControl<Date | null>(null, Validators.required),
    commentaire: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
  });

  protected readonly types = toSignal(this.api.typesConge().pipe(catchError(() => of<TypeConge[]>([]))), { initialValue: [] });
  private readonly soldes = toSignal(this.api.mesSoldes().pipe(catchError(() => of<Solde[]>([]))), { initialValue: [] });
  private readonly valeur = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue()), map(() => this.form.getRawValue())), { initialValue: this.form.getRawValue() });

  protected readonly type = computed(() => this.types().find(t => t.id === this.valeur().typeCongeId) ?? null);
  protected readonly solde = computed(() => {
    const t = this.type();
    return t?.deduitDuSolde ? this.soldes().find(s => s.typeCongeId === t.id) ?? null : null;
  });

  /** Period sent to the API, as local calendar days; null while incomplete or inconsistent. */
  private readonly periode = computed(() => {
    const { dateDebut, dateFin } = this.valeur();
    if (!dateDebut || !dateFin) return null;
    const debut = dateIso(dateDebut), fin = dateIso(dateFin);
    if (fin < debut || debut.slice(0, 4) !== fin.slice(0, 4)) return null;
    return `${debut}|${fin}`;
  });

  protected readonly probleme = computed(() => {
    const { dateDebut, dateFin } = this.valeur();
    if (!dateDebut || !dateFin) return '';
    const debut = dateIso(dateDebut), fin = dateIso(dateFin);
    if (fin < debut) return 'La date de fin doit être après la date de début.';
    if (debut.slice(0, 4) !== fin.slice(0, 4)) return 'Le premier et le dernier jour doivent être dans la même année : faites une demande par année.';
    const t = this.type();
    if (t && !t.retroactifAutorise && debut < this.aujourdhui) return `Un « ${t.libelle} » ne peut pas commencer dans le passé.`;
    return '';
  });

  protected readonly calcul = toSignal(
    toObservable(this.periode).pipe(
      switchMap(p => {
        if (!p) return of(null);
        const [debut, fin] = p.split('|');
        return this.api.joursOuvrables(debut, fin).pipe(
          map((j): Calcul => ({ statut: 'ok', j })),
          catchError(e => of<Calcul>({ statut: 'erreur', message: messageErreur(e) })),
          startWith<Calcul>({ statut: 'calcul' }),
        );
      }),
    ),
    { initialValue: null as Calcul | null },
  );

  protected readonly apres = computed(() => {
    const c = this.calcul(), s = this.solde();
    return c?.statut === 'ok' && s ? soldeApres(s, c.j.joursOuvrables) : null;
  });

  protected readonly decideur = computed(() => {
    const f = this.moi.fiche();
    if (!f) return '…';
    return f.managerNomComplet ?? 'Les RH (pas de manager)';
  });

  protected readonly envoi = signal(false);
  protected readonly erreur = signal('');

  protected invalide(champ: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[champ];
    return c.invalid && (c.touched || c.dirty);
  }

  protected async envoyer(): Promise<void> {
    if (this.envoi()) return;
    if (this.form.invalid || this.probleme()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const commentaire = v.commentaire.trim();
    this.envoi.set(true);
    this.erreur.set('');
    try {
      const d = await firstValueFrom(this.api.creerDemande({
        typeCongeId: v.typeCongeId!,
        dateDebut: dateIso(v.dateDebut!),
        dateFin: dateIso(v.dateFin!),
        ...(commentaire ? { commentaire } : {}),
      }));
      await this.router.navigate(['/leave-requests', d.id]);
    } catch (e) {
      if (!appliquerErreursChamps(this.form, e)) this.erreur.set(messageErreur(e));
    } finally {
      this.envoi.set(false);
    }
  }
}
