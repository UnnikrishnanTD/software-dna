import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { DnaCanvasComponent } from '../../shared/dna/dna-canvas.component';
import { IconComponent } from '../../shared/ui/icon.component';
import { AnalysisService } from '../../core/services/analysis.service';
import { DEMO_ANALYSIS_ID } from '../../core/services/mock-analysis.service';
import { environment } from '../../../environments/environment';

interface DimensionBlurb {
  readonly index: string;
  readonly label: string;
  readonly question: string;
}

/** What the DNA actually measures, stated as the question each answers. */
const DIMENSIONS: readonly DimensionBlurb[] = [
  { index: '01', label: 'Architecture', question: 'How is the system put together?' },
  { index: '02', label: 'Maintainability', question: 'How hard is it to change?' },
  { index: '03', label: 'Security', question: 'Where is it exposed?' },
  { index: '04', label: 'Performance', question: 'Where does it spend its time?' },
  { index: '05', label: 'Testing', question: 'What is actually verified?' },
  { index: '06', label: 'Dependencies', question: 'What does it rely on?' },
  { index: '07', label: 'Documentation', question: 'What is written down?' },
  { index: '08', label: 'Evolution', question: 'How did it get this way?' },
];

@Component({
  selector: 'sdna-landing-page',
  standalone: true,
  imports: [RouterLink, DnaCanvasComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dimensions = DIMENSIONS;
  protected readonly liveBackend = environment.useBackend;

  /**
   * Where "Explore demo" leads.
   *
   * With a backend connected the sample id does not exist, so the link points
   * at the most recent real analysis instead — and is hidden entirely until
   * there is one, rather than leading somewhere that will 404.
   */
  protected readonly demoId = signal<string | null>(
    environment.useBackend ? null : DEMO_ANALYSIS_ID,
  );

  constructor() {
    if (!environment.useBackend) {
      return;
    }
    this.service
      .listAnalyses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summaries) => this.demoId.set(summaries[0]?.id ?? null),
        // A landing page must render even when the API is down.
        error: () => this.demoId.set(null),
      });
  }
}
