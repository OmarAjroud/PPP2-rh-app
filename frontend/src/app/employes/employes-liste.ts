import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { SortEvent } from 'primeng/api';
import { RhApi } from '../core/api/rh-api';
import { Employe, PageReponse } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { initiales, libelleRole } from '../core/api/libelles';
import { AuthService } from '../core/auth/auth.service';
import { Apparition } from '../core/motion/motion';

type Etat = { statut: 'chargement' } | { statut: 'ok'; page: PageReponse<Employe> } | { statut: 'erreur'; message: string };

/** Table column -> sort field accepted by the API (EmployeService.TRIS_AUTORISES). */
const TRIS: Record<string, string> = { id: 'id', nom: 'nom', email: 'email', departement: 'departement.nom', role: 'role.nom' };

/**
 * Employee list. Search, filters, sort and page live in the URL (?q=&dept=&role=&sort=&dir=&page=&size=),
 * so the back button and shared links keep the view. The API scopes results to the user's role.
 */
@Component({
  selector: 'app-employes-liste',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ButtonModule, IconFieldModule, InputIconModule, InputTextModule, PaginatorModule, SelectModule, TableModule, Apparition],
  templateUrl: './employes-liste.html',
  styleUrl: './employes-liste.scss',
})
export class EmployesListe {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  /** ADMIN and RH manage employees and see the whole company (hence the filters). */
  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  /** Same wording as the navigation: a manager sees his team. */
  protected readonly titre = computed(() => (this.auth.profil()?.role === 'MANAGER' ? 'Mon équipe' : 'Employés'));
  protected readonly initiales = initiales;
  protected readonly libelleRole = libelleRole;

  private readonly params = toSignal(this.route.queryParams, { initialValue: {} as Params });
  protected readonly q = computed(() => (this.params()['q'] as string) ?? '');
  protected readonly dept = computed(() => numero(this.params()['dept']));
  protected readonly roleFiltre = computed(() => numero(this.params()['role']));
  protected readonly tri = computed(() => (this.params()['sort'] as string) ?? 'nom');
  protected readonly sens = computed(() => (this.params()['dir'] === 'desc' ? -1 : 1));
  protected readonly page = computed(() => numero(this.params()['page']) ?? 0);
  protected readonly taille = computed(() => numero(this.params()['size']) ?? 10);

  protected readonly etat = toSignal(
    this.route.queryParams.pipe(
      switchMap(p => this.api.employes({
        recherche: p['q'],
        departementId: numero(p['dept']),
        roleId: numero(p['role']),
        page: numero(p['page']) ?? 0,
        size: numero(p['size']) ?? 10,
        sort: `${TRIS[p['sort'] as string] ?? 'nom'},${p['dir'] === 'desc' ? 'desc' : 'asc'}`,
      }).pipe(
        map((page): Etat => ({ statut: 'ok', page })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
  /** Keep the previous rows on screen while the next page loads (no flash of empty table). */
  private dernierePage: PageReponse<Employe> | null = null;
  protected readonly lignes = computed(() => {
    const e = this.etat();
    if (e.statut === 'ok') this.dernierePage = e.page;
    return this.dernierePage;
  });
  protected readonly vide = computed(() => { const e = this.etat(); return e.statut === 'ok' && e.page.totalElements === 0; });

  protected readonly departements = toSignal(
    this.api.departements().pipe(map(l => [{ label: 'Tous les départements', value: null as number | null }, ...l.map(d => ({ label: d.nom, value: d.id as number | null }))]), catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly roles = toSignal(
    this.api.roles().pipe(map(l => [{ label: 'Tous les rôles', value: null as number | null }, ...l.map(r => ({ label: r.nom, value: r.id as number | null }))]), catchError(() => of([]))),
    { initialValue: [] },
  );

  protected readonly recherche = new FormControl(this.route.snapshot.queryParamMap.get('q') ?? '', { nonNullable: true });

  constructor() {
    this.recherche.valueChanges.pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(q => this.naviguer({ q: q.trim() || null, page: null }));
    // The rail search navigates here with ?q=: keep the field in sync
    this.route.queryParamMap.subscribe(p => {
      const q = p.get('q') ?? '';
      if (q !== this.recherche.value) this.recherche.setValue(q, { emitEvent: false });
    });
  }

  protected filtrer(cle: 'dept' | 'role', valeur: number | null): void {
    this.naviguer({ [cle]: valeur, page: null });
  }

  protected trier(e: SortEvent): void {
    if (!e.field || (e.field === this.tri() && e.order === this.sens())) return;
    this.naviguer({ sort: e.field === 'nom' ? null : e.field, dir: e.order === -1 ? 'desc' : null, page: null });
  }

  protected paginer(e: PaginatorState): void {
    const page = Math.floor((e.first ?? 0) / (e.rows ?? 10));
    this.naviguer({ page: page || null, size: e.rows === 10 ? null : e.rows });
  }

  private naviguer(queryParams: Params): void {
    void this.router.navigate([], { queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}

function numero(v: unknown): number | null {
  const n = Number(v);
  return v !== undefined && v !== null && v !== '' && Number.isFinite(n) ? n : null;
}
