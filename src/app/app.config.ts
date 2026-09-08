import {
  ApplicationConfig,
  provideZoneChangeDetection,
} from '@angular/core';
import {
  provideRouter,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';

import { routes } from './app.routes';
import { environment } from '../environments/environment';
import { AnalysisService } from './core/services/analysis.service';
import { HttpAnalysisService } from './core/services/http-analysis.service';
import { MockAnalysisService } from './core/services/mock-analysis.service';

export const appConfig: ApplicationConfig = {
  providers: [
    // Components are OnPush and signal-driven throughout, so change
    // detection only runs where state actually changed.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withInMemoryScrolling({
        scrollPositionRestoration: 'enabled',
        anchorScrolling: 'enabled',
      }),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
    ),

    provideHttpClient(withFetch()),

    // The single seam between the UI and its data source.
    //
    // Both implementations satisfy the same abstract `AnalysisService`, so
    // this flag is the entire difference between running against the sample
    // dataset and running against the Spring Boot API. No component knows
    // which one it got, and none had to change when the backend arrived.
    {
      provide: AnalysisService,
      useClass: environment.useBackend ? HttpAnalysisService : MockAnalysisService,
    },
  ],
};
