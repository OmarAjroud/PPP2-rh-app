import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { SelectModule } from 'primeng/select';
import { RhApi } from '../core/api/rh-api';
import { Departement, TableauDeBord as Donnees } from '../core/api/models';
import { messageErreur } from '../core/api/erreurs';
import { AuthService } from '../core/auth/auth.service';
import { Apparition, Compteur } from '../core/motion/motion';
import { Barres, Colonnes, Donnee } from './graphiques';

type Etat = { statut: 'chargement' } | { statut: 'ok'; d: Donnees } | { statut: 'erreur'; message: string };

const INITIALES = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];
const MOIS = ['Janv.', 'Févr.', 'Mars', 'Avr.', 'Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.', 'Nov.', 'Déc.'];
const pluriel = (v: number, un: string, plusieurs: string) => `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(v)} ${v > 1 ? plusieurs : un}`;

/**
 * Dashboard (ADMIN, RH, MANAGER): key figures, headcount, hires and leave for a year.
 * ADMIN and RH can narrow to a department and see the payroll; a manager always sees
 * their own department (enforced by the API). Year and department live in the URL.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [FormsModule, CurrencyPipe, SelectModule, Apparition, Compteur, Barres, Colonnes],
  template: `
    <div class="page" rhApparition>
      <header class="entete">
        <div><p class="crumb">Pilotage</p><h1 class="page-titre">Tableau de bord</h1></div>
        <div class="actions filtres">
          @if (gestion()) {
            <p-select [options]="optionsDepartements()" [ngModel]="departement()" (ngModelChange)="filtrer('dept', $event)" ariaLabel="Filtrer par département" />
          }
          <p-select [options]="annees()" [ngModel]="annee()" (ngModelChange)="filtrer('annee', $event)" ariaLabel="Année" />
        </div>
      </header>

      @switch (etat().statut) {
        @case ('chargement') { <p class="hint" aria-busy="true">Chargement…</p> }
        @case ('erreur') { <p class="alerte erreur" role="alert">{{ $any(etat()).message }}</p> }
        @case ('ok') {
          @let d = $any(etat()).d;
          <section class="tuiles" aria-label="Indicateurs">
            <div class="tuile"><span class="etiquette">Effectif</span><span class="chiffre" [rhCompteur]="d.indicateurs.nombreEmployes"></span><span class="note">{{ perimetre(d) }} · à ce jour</span></div>
            <div class="tuile"><span class="etiquette">Demandes en attente</span><span class="chiffre" [rhCompteur]="d.indicateurs.demandesEnAttente"></span><span class="note">à décider, à ce jour</span></div>
            <div class="tuile"><span class="etiquette">Absents aujourd'hui</span><span class="chiffre" [rhCompteur]="d.indicateurs.absentsAujourdhui"></span><span class="note">en congé approuvé, à ce jour</span></div>
            <div class="tuile"><span class="etiquette">Embauches du mois</span><span class="chiffre" [rhCompteur]="d.indicateurs.embauchesDuMois"></span><span class="note">{{ moisCourant }}</span></div>
          </section>

          <div class="grille">
            <section class="card" aria-labelledby="effectif-titre">
              <h2 id="effectif-titre" class="card-h">Effectif par département</h2>
              <div class="card-b"><app-barres titre="Effectif par département" [donnees]="effectif(d)" [format]="employes" colonneLibelle="Département" colonneValeur="Effectif" /></div>
            </section>
            <section class="card" aria-labelledby="embauches-titre">
              <h2 id="embauches-titre" class="card-h">Embauches par mois · {{ d.annee }}</h2>
              <div class="card-b"><app-colonnes [titre]="'Embauches par mois en ' + d.annee" [donnees]="embauches(d)" colonneLibelle="Mois" colonneValeur="Embauches" /></div>
            </section>
            <section class="card" aria-labelledby="conges-titre">
              <h2 id="conges-titre" class="card-h">Congés approuvés par type · {{ d.annee }}</h2>
              <div class="card-b"><app-barres [titre]="'Jours de congés approuvés par type en ' + d.annee" [donnees]="conges(d)" [format]="jours" colonneLibelle="Type" colonneValeur="Jours" /></div>
            </section>
            @if (d.masseSalariale) {
              <section class="card" aria-labelledby="masse-titre">
                <h2 id="masse-titre" class="card-h">Masse salariale mensuelle</h2>
                <div class="defile" tabindex="0" role="region" aria-label="Masse salariale par département">
                <table class="masse">
                  <thead><tr><th scope="col">Département</th><th scope="col" class="num">Effectif</th><th scope="col" class="num">Total</th><th scope="col" class="num">Moyenne</th></tr></thead>
                  <tbody>
                    @for (m of d.masseSalariale; track m.departementId) {
                      <tr><th scope="row">{{ m.departementNom }}</th><td class="num mono">{{ m.nombreEmployes }}</td><td class="num mono">{{ m.total | currency: 'EUR' }}</td><td class="num mono">{{ m.moyenne | currency: 'EUR' }}</td></tr>
                    } @empty {
                      <tr><td colspan="4" class="hint">Aucun salaire renseigné.</td></tr>
                    }
                  </tbody>
                </table>
                </div>
              </section>
            }
          </div>
        }
      }
    </div>
  `,
  styles: `
    .page { display: grid; gap: var(--rh-space-5); --page-max: var(--rh-content-max); }
    .filtres { align-items: center; }
    .filtres p-select { min-width: 140px; }
    .tuiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--rh-space-4); }
    .tuile { display: grid; gap: 4px; padding: var(--rh-space-4) var(--rh-space-5); border: 1px solid var(--rh-line); border-radius: var(--rh-radius); }
    .etiquette { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .chiffre { font-size: 30px; }
    .note { color: var(--rh-muted); font-size: var(--rh-text-sm); }
    .grille { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--rh-space-5); align-items: start; }
    .card-h { margin: 0; }
    .defile { overflow-x: auto; }
    .masse { width: 100%; min-width: 440px; border-collapse: collapse; }
    .masse th, .masse td { padding: 12px var(--rh-space-5); border-bottom: 1px solid var(--rh-line-row); text-align: left; font-weight: 400; }
    .masse thead th { color: var(--rh-muted); font-size: var(--rh-text-sm); font-weight: 500; }
    .masse tbody tr:last-child > * { border-bottom: 0; }
    .masse .num { text-align: right; }
    @media (max-width: 1100px) { .tuiles { grid-template-columns: repeat(2, minmax(0, 1fr)); } .grille { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 480px) { .tuiles { grid-template-columns: minmax(0, 1fr); } .masse th, .masse td { padding-inline: var(--rh-space-3); } }
  `,
})
export class TableauDeBord {
  private readonly api = inject(RhApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly anneeCourante = new Date().getFullYear();

  protected readonly moisCourant = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(new Date());
  protected readonly gestion = computed(() => ['ADMIN', 'RH'].includes(this.auth.profil()?.role ?? ''));
  protected readonly employes = (v: number) => pluriel(v, 'employé', 'employés');
  protected readonly jours = (v: number) => pluriel(v, 'jour', 'jours');

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly annee = computed(() => Number(this.params().get('annee')) || this.anneeCourante);
  /** The current year and the two before, plus a year asked in the address (so the select never shows empty). */
  protected readonly annees = computed(() => [...new Set([this.anneeCourante, this.anneeCourante - 1, this.anneeCourante - 2, this.annee()])]
    .sort((a, b) => b - a).map(a => ({ label: String(a), value: a })));
  protected readonly departement = computed(() => (this.gestion() ? Number(this.params().get('dept')) || null : null));

  private readonly departements = toSignal(
    this.gestion() ? this.api.departements().pipe(catchError(() => of<Departement[]>([]))) : of<Departement[]>([]),
    { initialValue: [] as Departement[] },
  );
  protected readonly optionsDepartements = computed(() => [
    { label: 'Toute l’entreprise', value: null as number | null },
    ...this.departements().map(d => ({ label: d.nom, value: d.id as number | null })),
  ]);

  protected readonly etat = toSignal(
    this.route.queryParamMap.pipe(
      map(q => ({ annee: Number(q.get('annee')) || this.anneeCourante, dept: this.gestion() ? Number(q.get('dept')) || null : null })),
      switchMap(({ annee, dept }) => this.api.tableauDeBord(annee === this.anneeCourante ? undefined : annee, dept).pipe(
        map((d): Etat => ({ statut: 'ok', d })),
        catchError(e => of<Etat>({ statut: 'erreur', message: messageErreur(e) })),
        startWith<Etat>({ statut: 'chargement' }),
      )),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );

  protected filtrer(cle: 'annee' | 'dept', valeur: number | null): void {
    const v = cle === 'annee' && valeur === this.anneeCourante ? null : valeur;
    void this.router.navigate([], { queryParams: { [cle]: v || null }, queryParamsHandling: 'merge' });
  }

  protected perimetre(d: Donnees): string {
    if (!d.departementId) return 'toute l’entreprise';
    return this.departements().find(x => x.id === d.departementId)?.nom ?? d.effectifParDepartement.find(x => x.departementId === d.departementId)?.departementNom ?? 'mon département';
  }

  protected effectif(d: Donnees): Donnee[] {
    return d.effectifParDepartement.map(e => ({ libelle: e.departementNom, valeur: e.nombreEmployes }));
  }

  protected embauches(d: Donnees): Donnee[] {
    return d.embauchesParMois.map(e => ({ libelle: MOIS[e.mois - 1], court: INITIALES[e.mois - 1], valeur: e.nombre }));
  }

  protected conges(d: Donnees): Donnee[] {
    return d.repartitionConges.map(c => ({ libelle: c.typeCongeLibelle, valeur: c.nombreJours }));
  }
}
