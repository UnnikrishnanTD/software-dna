import { Routes } from '@angular/router';

/** Child routes rendered inside the analysis shell. */
export const ANALYSIS_ROUTES: Routes = [
  {
    path: '',
    title: 'Overview — Software DNA',
    loadComponent: () =>
      import('./dna-overview/dna-overview.component').then(
        (m) => m.DnaOverviewComponent,
      ),
  },
  {
    path: 'architecture',
    title: 'Architecture — Software DNA',
    loadComponent: () =>
      import('./architecture/architecture-page.component').then(
        (m) => m.ArchitecturePageComponent,
      ),
  },
  {
    path: 'codebase',
    title: 'Codebase — Software DNA',
    loadComponent: () =>
      import('./codebase/codebase-page.component').then(
        (m) => m.CodebasePageComponent,
      ),
  },
  {
    path: 'hotspots',
    title: 'Hotspots — Software DNA',
    loadComponent: () =>
      import('./hotspots/hotspots-page.component').then(
        (m) => m.HotspotsPageComponent,
      ),
  },
  {
    path: 'dependencies',
    title: 'Dependencies — Software DNA',
    loadComponent: () =>
      import('./dependencies/dependencies-page.component').then(
        (m) => m.DependenciesPageComponent,
      ),
  },
  {
    path: 'evolution',
    title: 'Evolution — Software DNA',
    loadComponent: () =>
      import('./evolution/evolution-page.component').then(
        (m) => m.EvolutionPageComponent,
      ),
  },
  {
    path: 'ai-doctor',
    title: 'AI Doctor — Software DNA',
    loadComponent: () =>
      import('./ai-doctor/ai-doctor-page.component').then(
        (m) => m.AiDoctorPageComponent,
      ),
  },
  {
    path: 'compare',
    title: 'Compare — Software DNA',
    loadComponent: () =>
      import('./comparison/comparison-page.component').then(
        (m) => m.ComparisonPageComponent,
      ),
  },
];
