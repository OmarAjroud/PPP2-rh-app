import { Routes } from '@angular/router';
import { authGuard, inviteGuard } from './core/auth/auth.guards';
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
      { path: 'security', title: 'Sécurité du compte · RH App', loadComponent: () => import('./compte/securite').then(m => m.Securite) },
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
