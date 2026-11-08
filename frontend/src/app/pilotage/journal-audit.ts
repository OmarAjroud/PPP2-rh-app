import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { AuditEntree, PageReponse } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { Apparition } from '../core/motion/motion';
import { dateIso } from '../conges/regles';
import { Difference, differences, libelleAction } from './audit-regles';

type Etat = { statut: 'chargement' } | { statut: 'ok'; page: PageReponse<AuditEntree> } | { statut: 'erreur'; message: string };
const TAILLE = 20;

/** "2026-11-08" → local Date (never new Date(iso): it would be read as UTC). */
function versDate(iso: string | null): Date | null {
  if (!iso) return null;
  const [a, m, j] = iso.split('-').map(Number);
  return a && m && j ? new Date(a, m - 1, j) : null;
}

/**
 * Audit log (ADMIN): who did what, when, on which record. Filters live in the URL.
 * Opening a row shows the fields that changed (before → after), the details and the IP address.
 */
@Component({
  selector: 'app-journal-audit',
  imports: [DatePipe, FormsModule, ReactiveFormsModule, ButtonModule, DatePickerModule, IconFieldModule, InputIconModule, InputTextModule, PaginatorModule, SelectModule, Apparition],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Pilotage</p><h1 class="page-titre">Journal d'audit</h1></div>
      </header>

      <section class="liste">
        <div class="filtres">
          <p-iconfield class="utilisateur">
            <p-inputicon class="pi pi-search" />
            <input pInputText type="search" [formControl]="utilisateur" placeholder="Utilisateur" aria-label="Filtrer par utilisateur">
          </p-iconfield>
          <p-select [options]="optionsActions()" [ngModel]="filtre('action')" (ngModelChange)="filtrer({ action: $event })" placeholder="Toutes les actions" ariaLabel="Filtrer par action" [showClear]="true" />
          <p-select [options]="optionsEntites()" [ngModel]="filtre('entite')" (ngModelChange)="filtrer({ entite: $event })" placeholder="Tous les objets" ariaLabel="Filtrer par objet" [showClear]="true" />
          <p-datepicker [ngModel]="date('du')" (ngModelChange)="filtrer({ du: $event ? dateIso($event) : null })" dateFormat="dd/mm/yy" placeholder="Du" ariaLabel="Du" [showClear]="true" />
          <p-datepicker [ngModel]="date('au')" (ngModelChange)="filtrer({ au: $event ? dateIso($event) : null })" dateFormat="dd/mm/yy" placeholder="Au" ariaLabel="Au" [showClear]="true" />
          @if (filtresActifs()) { <p-button label="Effacer les filtres" severity="secondary" [text]="true" (onClick)="effacer()" /> }
          @if (etat().statut === 'ok') {
            @let t = $any(etat()).page.totalElements;
            <span class="total hint" aria-live="polite">{{ t }} {{ t > 1 ? 'actions' : 'action' }}</span>
          }
        </div>

        @switch (etat().statut) {
          @case ('chargement') { <p class="vide hint" aria-busy="true">Chargement…</p> }
          @case ('erreur') { <div class="vide"><p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p></div> }
          @case ('ok') {
            @let p = $any(etat()).page;
            <ul class="entrees">
              @for (e of p.contenu; track e.id) {
                @let ouvert = ouverte() === e.id;
                <li class="entree" [class.ouverte]="ouvert">
                  <button type="button" class="resume" [attr.aria-expanded]="ouvert" [attr.aria-controls]="'audit-' + e.id" (click)="basculer(e.id)">
                    <time [attr.datetime]="e.dateAction">{{ e.dateAction | date: 'd MMM y, HH:mm:ss' }}</time>
                    <span class="qui">{{ e.nomUtilisateur ?? 'système' }}</span>
                    <span class="action" [class.echec]="e.action === 'ECHEC_CONNEXION'">{{ libelleAction(e.action) }}</span>
                    <span class="objet">@if (e.entite) { {{ e.entite }}@if (e.entiteId) { <span class="mono"> #{{ e.entiteId }}</span> } } @else { <span class="hint">—</span> }</span>
                    <i class="pi pi-chevron-down" aria-hidden="true"></i>
                  </button>
                  @if (ouvert) {
                    @let diffs = changements(e);
                    <div class="detail" [id]="'audit-' + e.id">
                      @if (diffs.length) {
                        <table>
                          <caption class="hint">{{ diffs.length }} {{ diffs.length > 1 ? 'champs modifiés' : 'champ modifié' }}</caption>
                          <thead><tr><th scope="col">Champ</th><th scope="col">Avant</th><th scope="col">Après</th></tr></thead>
                          <tbody>
                            @for (d of diffs; track d.champ) {
                              <tr><th scope="row">{{ d.champ }}</th><td class="mono avant">{{ d.avant ?? '—' }}</td><td class="mono apres">{{ d.apres ?? '—' }}</td></tr>
                            }
                          </tbody>
                        </table>
                      }
                      <dl>
                        @if (e.details) { <dt>Détail</dt><dd>{{ e.details }}</dd> }
                        <dt>Adresse IP</dt><dd class="mono">{{ e.adresseIp ?? '—' }}</dd>
                      </dl>
                    </div>
                  }
                </li>
              } @empty {
                <li class="vide"><p><strong>Aucune action{{ filtresActifs() ? ' pour ces filtres' : '' }}.</strong></p></li>
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
    .page { display: grid; gap: var(--rh-space-4); --page-max: var(--rh-content-max); }
    .filtres { display: flex; flex-wrap: wrap; align-items: center; gap: var(--rh-space-2); padding-bottom: var(--rh-space-3); border-bottom: 1px solid var(--rh-line); }
    .utilisateur { flex: 1 1 200px; max-width: 240px; }
    .utilisateur input { width: 100%; }
    .filtres p-select { flex: 0 1 200px; }
    .filtres p-datepicker { flex: 0 1 150px; }
    .total { margin-left: auto; white-space: nowrap; }
    .entrees { list-style: none; margin: 0; padding: 0; }
    .entree { border-bottom: 1px solid var(--rh-line-row); }
    .resume {
      width: 100%;
      display: grid;
      grid-template-columns: 170px 140px 220px minmax(0, 1fr) 16px;
      align-items: center;
      gap: var(--rh-space-3);
      min-height: var(--rh-row-height);
      padding: 8px var(--rh-space-2);
      border: 0;
      background: none;
      color: var(--rh-ink);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .resume:hover { background: var(--rh-surface-2); }
    time { color: var(--rh-muted); font-size: var(--rh-text-sm); font-variant-numeric: tabular-nums; }
    .qui { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .action.echec { color: var(--rh-danger); }
    .objet { color: var(--rh-ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .resume i { color: var(--rh-muted); font-size: 12px; transition: transform var(--rh-fast) var(--rh-ease); }
    .ouverte .resume i { transform: rotate(180deg); }
    .detail { display: grid; gap: var(--rh-space-3); padding: var(--rh-space-2) var(--rh-space-2) var(--rh-space-4) calc(170px + var(--rh-space-5)); }
    table { width: 100%; max-width: 760px; border-collapse: collapse; font-size: var(--rh-text-sm); }
    caption { text-align: left; padding-bottom: var(--rh-space-1); }
    th, td { padding: 6px 8px; border-bottom: 1px solid var(--rh-line-row); text-align: left; font-weight: 400; vertical-align: top; overflow-wrap: anywhere; }
    thead th { color: var(--rh-muted); }
    tbody th { color: var(--rh-ink-2); }
    .avant { color: var(--rh-muted); text-decoration: line-through; text-decoration-color: var(--rh-line-strong); }
    dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 4px var(--rh-space-4); margin: 0; font-size: var(--rh-text-sm); }
    dt { color: var(--rh-muted); }
    dd { margin: 0; overflow-wrap: anywhere; }
    .vide { display: grid; gap: var(--rh-space-1); padding: var(--rh-space-8) var(--rh-space-2); }
    @media (max-width: 900px) {
      .resume { grid-template-columns: minmax(0, 1fr) auto 16px; grid-template-areas: "qui action i" "time objet i"; row-gap: 2px; }
      .qui { grid-area: qui; } .action { grid-area: action; } time { grid-area: time; } .objet { grid-area: objet; text-align: right; } .resume i { grid-area: i; }
      .detail { padding-left: var(--rh-space-2); }
    }
  `,
})
export class JournalAudit {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly taille = TAILLE;
  protected readonly libelleAction = libelleAction;
  protected readonly dateIso = dateIso;

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly page = computed(() => Math.max(0, Number(this.params().get('page') ?? 0) || 0));
  protected readonly filtresActifs = computed(() => ['utilisateur', 'action', 'entite', 'du', 'au'].some(c => !!this.params().get(c)));

  protected readonly utilisateur = new FormControl(this.params().get('utilisateur') ?? '', { nonNullable: true });

  protected readonly optionsActions = toSignal(
    this.api.actionsAudit().pipe(map(l => l.map(a => ({ label: libelleAction(a), value: a }))), catchError(() => of([]))),
    { initialValue: [] as { label: string; value: string }[] },
  );
  protected readonly optionsEntites = toSignal(
    this.api.entitesAudit().pipe(map(l => l.map(e => ({ label: e, value: e }))), catchError(() => of([]))),
    { initialValue: [] as { label: string; value: string }[] },
  );

  protected readonly etat = toSignal(
    this.route.queryParamMap.pipe(
      switchMap(q => this.api.audit({
        nomUtilisateur: q.get('utilisateur'), action: q.get('action'), entite: q.get('entite'),
        du: q.get('du'), au: q.get('au'), page: Math.max(0, Number(q.get('page') ?? 0) || 0), size: TAILLE,
      }).pipe(
        map((page): Etat => ({ statut: 'ok', page })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );

  protected readonly ouverte = signal<number | null>(null);

  constructor() {
    this.utilisateur.valueChanges
      .pipe(debounceTime(300), map(v => v.trim()), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(v => this.filtrer({ utilisateur: v || null }));
  }

  protected filtre(cle: 'action' | 'entite'): string | null {
    return this.params().get(cle);
  }

  protected date(cle: 'du' | 'au'): Date | null {
    return versDate(this.params().get(cle));
  }

  protected filtrer(f: Record<string, string | null>): void {
    this.ouverte.set(null);
    void this.router.navigate([], { queryParams: { ...f, page: null }, queryParamsHandling: 'merge' });
  }

  protected effacer(): void {
    this.utilisateur.setValue('', { emitEvent: false });
    this.ouverte.set(null);
    void this.router.navigate([], { queryParams: {} });
  }

  protected paginer(e: PaginatorState): void {
    this.ouverte.set(null);
    void this.router.navigate([], { queryParams: { page: e.page || null }, queryParamsHandling: 'merge' });
  }

  protected basculer(id: number): void {
    this.ouverte.update(o => (o === id ? null : id));
  }

  protected changements(e: AuditEntree): Difference[] {
    return differences(e.anciennesValeurs, e.nouvellesValeurs);
  }
}
