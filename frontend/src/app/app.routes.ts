import { Routes } from '@angular/router';
import { authGuard } from './auth';
import { Shell } from './shell';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login').then((m) => m.LoginPage), title: 'Sign in | RelayBot' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'activity' },
      { path: 'activity', loadComponent: () => import('./pages/activity').then((m) => m.ActivityPage), title: 'Live log | RelayBot' },
      { path: 'reports', loadComponent: () => import('./pages/reports').then((m) => m.ReportsPage), title: 'Reports | RelayBot' },
      { path: 'jobs', loadComponent: () => import('./pages/jobs').then((m) => m.JobsPage), title: 'Deliveries | RelayBot' },
      { path: 'servers', loadComponent: () => import('./pages/servers').then((m) => m.ServersPage), title: 'Servers | RelayBot' },
      { path: 'servers/:id', loadComponent: () => import('./pages/server-detail').then((m) => m.ServerDetailPage), title: 'Server settings | RelayBot' },
    ],
  },
  { path: '**', redirectTo: '' },
];
