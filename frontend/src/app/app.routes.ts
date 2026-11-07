import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', title: 'Accueil · RH App', loadComponent: () => import('./accueil/accueil').then(m => m.Accueil) },
  { path: '**', redirectTo: '' },
];
