import { Routes } from '@angular/router';
import { DEMO_ANALYSIS_ID } from './core/services/mock-analysis.service';

/**
 * Feature routes are lazy-loaded so the landing page ships on its own. The
 * analysis shell owns the `:id` segment and provides the store its children
 * read from, which keeps every child route free of data loading.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Software DNA — Every software system has a DNA',
    loadComponent: () =>
      import('./features/landing/landing-page.component').then(
        (m) => m.LandingPageComponent,
      ),
  },
  {
    path: 'analyse',
    title: 'Analyse a repository — Software DNA',
    loadComponent: () =>
      import('./features/analyse/analyse-page.component').then(
        (m) => m.AnalysePageComponent,
      ),
  },
  {
    path: 'analysing',
    title: 'Analysing — Software DNA',
    loadComponent: () =>
      import('./features/analysis/analysis-run.component').then(
        (m) => m.AnalysisRunComponent,
      ),
  },
  {
    path: 'compare',
    // The comparison lives inside the analysis shell so the navigation
    // stays coherent; the top-level path is kept as an entry point.
    redirectTo: `analysis/${DEMO_ANALYSIS_ID}/compare`,
    pathMatch: 'full',
  },
  {
    path: 'analysis/:id',
    loadComponent: () =>
      import('./layout/app-shell/app-shell.component').then(
        (m) => m.AppShellComponent,
      ),
    loadChildren: () =>
      import('./features/analysis-routes').then((m) => m.ANALYSIS_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
