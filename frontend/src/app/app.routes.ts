import { Routes } from '@angular/router';
import { authGuard, inviteGuard, listeEmployesGuard, roleGuard } from './core/auth/auth.guards';
import { AuthLayout } from './layout/auth-layout';
import { Shell } from './layout/shell';

export const routes: Routes = [
  // Signed-in pages. This group must come first: it owns the empty path "/", and a component route
  // with path '' would otherwise match "/" in the public group below and render an empty layout.
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Accueil · RH App', loadComponent: () => import('./accueil/accueil').then(m => m.Accueil) },
      { path: 'dashboard', title: 'Tableau de bord · RH App', canActivate: [roleGuard('ADMIN', 'RH', 'MANAGER')], loadComponent: () => import('./pilotage/tableau-de-bord').then(m => m.TableauDeBord) },
      { path: 'audit', title: 'Journal d’audit · RH App', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pilotage/journal-audit').then(m => m.JournalAudit) },
      { path: 'assistant', title: 'Assistant RH · RH App', loadComponent: () => import('./assistant/assistant-page').then(m => m.AssistantPage) },
      { path: 'notifications', title: 'Notifications · RH App', loadComponent: () => import('./notifications/notifications-page').then(m => m.NotificationsPage) },
      { path: 'leave-requests', title: 'Mes congés · RH App', loadComponent: () => import('./conges/mes-conges').then(m => m.MesConges) },
      { path: 'leave-requests/new', title: 'Nouvelle demande · RH App', loadComponent: () => import('./conges/demande-form').then(m => m.DemandeForm) },
      { path: 'leave-requests/:id', title: 'Demande de congé · RH App', loadComponent: () => import('./conges/demande-detail').then(m => m.DemandeDetail) },
      { path: 'approvals', title: 'À décider · RH App', canActivate: [roleGuard('MANAGER', 'RH', 'ADMIN')], loadComponent: () => import('./conges/a-decider').then(m => m.ADecider) },
      { path: 'security', title: 'Sécurité du compte · RH App', loadComponent: () => import('./compte/securite').then(m => m.Securite) },
      { path: 'profile', title: 'Mon profil · RH App', data: { moi: true }, loadComponent: () => import('./employes/employe-detail').then(m => m.EmployeDetailPage) },

      { path: 'employees', title: 'Employés · RH App', canActivate: [listeEmployesGuard], loadComponent: () => import('./employes/employes-liste').then(m => m.EmployesListe) },
      { path: 'employees/new', title: 'Ajouter un employé · RH App', canActivate: [roleGuard('ADMIN', 'RH')], loadComponent: () => import('./employes/employe-form').then(m => m.EmployeForm) },
      { path: 'employees/:id', title: 'Employé · RH App', loadComponent: () => import('./employes/employe-detail').then(m => m.EmployeDetailPage) },
      { path: 'employees/:id/edit', title: 'Modifier l’employé · RH App', canActivate: [roleGuard('ADMIN', 'RH')], loadComponent: () => import('./employes/employe-form').then(m => m.EmployeForm) },

      { path: 'departments', title: 'Départements · RH App', loadComponent: () => import('./departements/departements-liste').then(m => m.DepartementsListe) },
      { path: 'departments/new', title: 'Ajouter un département · RH App', canActivate: [roleGuard('ADMIN', 'RH')], data: { entite: 'departement' }, loadComponent: () => import('./shared/nom-description-form').then(m => m.NomDescriptionForm) },
      { path: 'departments/:id', title: 'Département · RH App', loadComponent: () => import('./departements/departement-detail').then(m => m.DepartementDetailPage) },
      { path: 'departments/:id/edit', title: 'Modifier le département · RH App', canActivate: [roleGuard('ADMIN', 'RH')], data: { entite: 'departement' }, loadComponent: () => import('./shared/nom-description-form').then(m => m.NomDescriptionForm) },

      { path: 'roles', title: 'Rôles · RH App', loadComponent: () => import('./roles/roles-liste').then(m => m.RolesListe) },
      { path: 'roles/new', title: 'Ajouter un rôle · RH App', canActivate: [roleGuard('ADMIN')], data: { entite: 'role' }, loadComponent: () => import('./shared/nom-description-form').then(m => m.NomDescriptionForm) },
      { path: 'roles/:id', title: 'Rôle · RH App', loadComponent: () => import('./roles/role-detail').then(m => m.RoleDetailPage) },
      { path: 'roles/:id/edit', title: 'Modifier le rôle · RH App', canActivate: [roleGuard('ADMIN')], data: { entite: 'role' }, loadComponent: () => import('./shared/nom-description-form').then(m => m.NomDescriptionForm) },
    ],
  },
  // Public pages. activate-account and reset-password are the links the backend puts in its emails.
  {
    path: '',
    component: AuthLayout,
    children: [
      { path: 'login', title: 'Connexion · RH App', canActivate: [inviteGuard], loadComponent: () => import('./auth/connexion').then(m => m.Connexion) },
      { path: 'forgot-password', title: 'Mot de passe oublié · RH App', canActivate: [inviteGuard], loadComponent: () => import('./auth/mot-de-passe-oublie').then(m => m.MotDePasseOublie) },
      { path: 'activate-account', title: 'Activer votre compte · RH App', data: { variante: 'activation' }, loadComponent: () => import('./auth/lien-mot-de-passe').then(m => m.LienMotDePasse) },
      { path: 'reset-password', title: 'Nouveau mot de passe · RH App', data: { variante: 'reinitialisation' }, loadComponent: () => import('./auth/lien-mot-de-passe').then(m => m.LienMotDePasse) },
    ],
  },
  { path: '**', redirectTo: '' },
];
