import { Component, ElementRef, HostListener, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { filter } from 'rxjs';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { LIBELLES_ROLE, Role } from '../core/auth/auth.models';
import { Moi } from '../core/auth/moi';
import { ChoixTheme, ThemeService } from '../core/theme/theme.service';
import { Notifications } from '../core/notifications/notifications';
import { ToastNotification } from './toast-notification';

interface Lien { chemin: string; libelle: string; icone: string; exact?: boolean }
interface Groupe { titre: string | null; liens: Lien[] }

/** Navigation per role. The API enforces the same scopes; this only hides what a role cannot use. */
function navigation(role: Role): Groupe[] {
  const gestion = role === 'ADMIN' || role === 'RH';
  return [
    {
      titre: null,
      liens: [
        { chemin: '/', libelle: 'Accueil', icone: 'pi-home', exact: true },
        { chemin: '/notifications', libelle: 'Notifications', icone: 'pi-bell' },
        { chemin: '/assistant', libelle: 'Assistant', icone: 'pi-comments' },
      ],
    },
    {
      titre: 'Congés',
      liens: [
        { chemin: '/leave-requests', libelle: 'Mes congés', icone: 'pi-calendar' },
        ...(role === 'EMPLOYE' ? [] : [{ chemin: '/approvals', libelle: 'À décider', icone: 'pi-check-square' }]),
      ],
    },
    ...(role === 'EMPLOYE' ? [] : [{
      titre: 'Pilotage',
      liens: [
        { chemin: '/dashboard', libelle: 'Tableau de bord', icone: 'pi-chart-bar' },
        ...(role === 'ADMIN' ? [{ chemin: '/audit', libelle: 'Journal d\'audit', icone: 'pi-history' }] : []),
      ],
    }]),
    {
      titre: 'Entreprise',
      liens: [
        ...(role === 'EMPLOYE' ? [] : [{ chemin: '/employees', libelle: role === 'MANAGER' ? 'Mon équipe' : 'Employés', icone: 'pi-users' }]),
        { chemin: '/departments', libelle: 'Départements', icone: 'pi-building' },
        ...(gestion ? [{ chemin: '/roles', libelle: 'Rôles', icone: 'pi-id-card' }] : []),
      ],
    },
    {
      titre: 'Mon compte',
      liens: [
        { chemin: '/profile', libelle: 'Mon profil', icone: 'pi-user' },
        { chemin: '/security', libelle: 'Sécurité', icone: 'pi-lock' },
      ],
    },
  ];
}

/** Application shell: Linear-style rail + routed work area; under 900px the rail is a drawer (DESIGN.md, Layout). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastNotification],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  /** Opens the real-time stream for the session (the service follows login and logout). */
  protected readonly notifs = inject(Notifications);
  private readonly router = inject(Router);
  private readonly moi = inject(Moi);
  private readonly champRecherche = viewChild<ElementRef<HTMLInputElement>>('recherche');

  protected readonly role = computed(() => {
    const p = this.auth.profil();
    return p ? LIBELLES_ROLE[p.role] : '';
  });
  protected readonly nomComplet = computed(() => {
    const f = this.moi.fiche();
    return f ? `${f.prenom} ${f.nom}` : this.auth.profil()?.nomUtilisateur ?? '';
  });
  protected readonly initiales = computed(() => {
    const f = this.moi.fiche();
    return f ? `${f.prenom[0] ?? ''}${f.nom[0] ?? ''}`.toUpperCase() : (this.auth.profil()?.nomUtilisateur.slice(0, 2).toUpperCase() ?? '');
  });
  /** The department, unless it just repeats the role (an HR person in the HR department). */
  protected readonly departement = computed(() => {
    const d = this.moi.fiche()?.departementNom ?? '';
    return d.toLowerCase() === this.role().toLowerCase() ? '' : d;
  });
  protected readonly groupes = computed(() => {
    const p = this.auth.profil();
    return p ? navigation(p.role) : [];
  });
  /** The search opens the employee list, which employees cannot use. */
  protected readonly rechercheVisible = computed(() => this.auth.profil()?.role !== 'EMPLOYE');
  protected readonly themes: { valeur: ChoixTheme; libelle: string; icone: string }[] = [
    { valeur: 'systeme', libelle: 'Thème du système', icone: 'pi-desktop' },
    { valeur: 'clair', libelle: 'Thème clair', icone: 'pi-sun' },
    { valeur: 'sombre', libelle: 'Thème sombre', icone: 'pi-moon' },
  ];

  /** Drawer state on small screens (the rail is always visible from 900px). */
  readonly menuOuvert = signal(false);
  private readonly boutonMenu = viewChild<ElementRef<HTMLButtonElement>>('boutonMenu');
  private readonly rail = viewChild<ElementRef<HTMLElement>>('rail');
  private readonly injector = inject(Injector);

  constructor() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.fermerMenu());
  }

  /** Opens the drawer and moves focus into it; the page behind is inert meanwhile (see template). */
  protected basculerMenu(): void {
    if (this.menuOuvert()) {
      this.fermerMenu();
      return;
    }
    this.menuOuvert.set(true);
    afterNextRender(() => {
      if (this.menuOuvert()) this.rail()?.nativeElement.querySelector<HTMLElement>('a, button, input')?.focus();
    }, { injector: this.injector });
  }

  /** Closes the drawer and gives focus back to the menu button (the rail is about to be hidden). */
  protected fermerMenu(): void {
    if (!this.menuOuvert()) return;
    this.menuOuvert.set(false);
    this.boutonMenu()?.nativeElement.focus();
  }

  /** Ctrl K (or ⌘K) focuses the search field; Escape closes the drawer. */
  @HostListener('document:keydown', ['$event'])
  protected raccourci(e: KeyboardEvent): void {
    if (e.key === 'Escape' && this.menuOuvert()) {
      this.fermerMenu();
      return;
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && this.rechercheVisible()) {
      e.preventDefault();
      this.champRecherche()?.nativeElement.focus();
    }
  }

  protected rechercher(champ: HTMLInputElement): void {
    const q = champ.value.trim();
    void this.router.navigate(['/employees'], { queryParams: q ? { q } : {} });
    champ.value = '';
    champ.blur();
  }
}
