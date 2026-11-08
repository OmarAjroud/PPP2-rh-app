import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { RhApi } from '../core/api/rh-api';
import { Notification, PageReponse } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { Notifications } from '../core/notifications/notifications';
import { Apparition } from '../core/motion/motion';

type Etat = { statut: 'chargement' } | { statut: 'ok'; page: PageReponse<Notification> } | { statut: 'erreur'; message: string };

const ICONES: Record<string, string> = {
  CONGE_DEMANDE: 'pi-inbox',
  CONGE_APPROUVE: 'pi-check',
  CONGE_REFUSE: 'pi-times',
  CONGE_ANNULE: 'pi-ban',
  CONGE_A_TRAITER: 'pi-clock',
};

/** My notifications (all or unread). Opening one marks it read and follows its link. */
@Component({
  selector: 'app-notifications-page',
  imports: [RouterLink, DatePipe, ButtonModule, PaginatorModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><h1 class="page-titre">Notifications</h1></div>
        <p-button label="Tout marquer comme lu" severity="secondary" [disabled]="notifs.nonLues() === 0 || occupe()" (onClick)="toutLire()" />
      </header>

      <section class="liste">
        <nav class="onglets" aria-label="Filtre">
          <a [routerLink]="[]" [queryParams]="{ vue: null }" [attr.aria-current]="!nonLues() ? 'page' : null">Toutes</a>
          <a [routerLink]="[]" [queryParams]="{ vue: 'non-lues' }" [attr.aria-current]="nonLues() ? 'page' : null">Non lues @if (notifs.nonLues() > 0) { <span class="mono">{{ notifs.nonLues() }}</span> }</a>
        </nav>
        @if (erreur()) { <p class="alerte erreur" role="alert">{{ erreur() }}</p> }

        @switch (etat().statut) {
          @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
          @case ('erreur') { <div class="vide"><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p></div> }
          @case ('ok') {
            @let p = $any(etat()).page;
            <ul>
              @for (n of p.contenu; track n.id) {
                @let nonLue = !n.lue && !luesIci().has(n.id);
                <li class="notif" [class.non-lue]="nonLue">
                  <button type="button" (click)="ouvrir(n)">
                    <i class="pi {{ icone(n) }}" aria-hidden="true"></i>
                    <span class="texte">
                      <b>{{ n.titre }}@if (nonLue) { <span class="sr-only"> (non lue)</span> }</b>
                      <span>{{ n.message }}</span>
                    </span>
                    <time [attr.datetime]="n.dateCreation">{{ n.dateCreation | date: 'd MMM, HH:mm' }}</time>
                  </button>
                </li>
              } @empty {
                <li class="vide">
                  <p><strong>{{ nonLues() ? 'Tout est lu.' : 'Aucune notification pour l\\'instant.' }}</strong></p>
                  <p class="hint">Les décisions sur vos demandes et les demandes à traiter apparaissent ici.</p>
                </li>
              }
            </ul>
            @if (p.totalElements > taille) {
              <p-paginator [first]="page() * taille" [rows]="taille" [totalRecords]="p.totalElements" (onPageChange)="paginer($event)" />
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-4); --page-max: 860px; }
    .onglets { display: flex; gap: var(--rh-space-1); padding-bottom: var(--rh-space-3); border-bottom: 1px solid var(--rh-line); }
    .onglets a { display: inline-flex; gap: 6px; padding: 6px 12px; border-radius: var(--rh-radius-sm); color: var(--rh-muted); font-weight: 500; text-decoration: none; }
    .onglets a:hover { color: var(--rh-ink); background: var(--rh-surface-2); }
    .onglets a[aria-current="page"] { color: var(--rh-ink); background: var(--rh-surface-3); }
    ul { list-style: none; margin: 0; padding: 0; }
    .notif { border-bottom: 1px solid var(--rh-line-row); }
    .notif button {
      width: 100%;
      display: flex;
      align-items: flex-start;
      gap: var(--rh-space-3);
      padding: var(--rh-space-4) var(--rh-space-2);
      border: 0;
      background: none;
      color: var(--rh-ink-2);
      font: inherit;
      text-align: left;
      cursor: pointer;
      transition: background var(--rh-fast) var(--rh-ease);
    }
    .notif button:hover { background: var(--rh-surface-2); }
    .notif i { width: 28px; height: 28px; flex: none; display: grid; place-items: center; border-radius: 50%; background: var(--rh-surface-3); color: var(--rh-muted); font-size: 13px; }
    .texte { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .texte b { font-weight: 500; color: var(--rh-ink-2); }
    .texte > span { font-size: var(--rh-text-sm); color: var(--rh-muted); overflow-wrap: anywhere; }
    time { flex: none; font-size: var(--rh-text-sm); color: var(--rh-muted); white-space: nowrap; }
    .non-lue b { font-weight: 600; color: var(--rh-ink); }
    .non-lue i { background: var(--rh-accent-soft); color: var(--rh-accent-text); }
    .non-lue time::before { content: ""; display: inline-block; width: 7px; height: 7px; margin-right: 8px; border-radius: 50%; background: var(--rh-accent); vertical-align: middle; }
    .vide { display: grid; gap: var(--rh-space-1); padding: var(--rh-space-8) var(--rh-space-2); }
    .alerte { margin-top: var(--rh-space-3); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    @media (max-width: 560px) {
      .notif button { flex-wrap: wrap; }
      time { order: 3; margin-left: 40px; }
    }
  `,
})
export class NotificationsPage {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly notifs = inject(Notifications);
  private readonly recharge$ = new BehaviorSubject<void>(undefined);
  protected readonly taille = 10;

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly nonLues = computed(() => this.params().get('vue') === 'non-lues');
  protected readonly page = computed(() => Math.max(0, Number(this.params().get('page') ?? 0) || 0));

  protected readonly etat = toSignal(
    combineLatest([this.route.queryParamMap, this.recharge$]).pipe(
      switchMap(([q]) => this.api.notifications(q.get('vue') === 'non-lues', Math.max(0, Number(q.get('page') ?? 0) || 0), this.taille).pipe(
        map((page): Etat => ({ statut: 'ok', page })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );

  /** Read here, before the next reload: shown as read at once. */
  protected readonly luesIci = signal(new Set<number>());
  protected readonly occupe = signal(false);
  protected readonly erreur = signal('');

  protected icone(n: Notification): string {
    return ICONES[n.type] ?? 'pi-bell';
  }

  protected paginer(e: PaginatorState): void {
    void this.router.navigate([], { queryParams: { page: e.page || null }, queryParamsHandling: 'merge' });
  }

  protected async ouvrir(n: Notification): Promise<void> {
    this.erreur.set('');
    if (!n.lue && !this.luesIci().has(n.id)) {
      try {
        await firstValueFrom(this.api.marquerLue(n.id));
        this.luesIci.update(s => new Set(s).add(n.id));
        this.notifs.ajuster(this.notifs.nonLues() - 1);
      } catch (e) {
        this.erreur.set(messageErreur(e));
        return;
      }
    }
    if (n.lien) await this.router.navigateByUrl(n.lien);
  }

  protected async toutLire(): Promise<void> {
    if (this.occupe()) return;
    this.occupe.set(true);
    this.erreur.set('');
    try {
      await firstValueFrom(this.api.toutMarquerLu());
      this.notifs.ajuster(0);
      this.luesIci.set(new Set());
      this.recharge$.next();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
