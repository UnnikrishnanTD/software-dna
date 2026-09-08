import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AnalysisSummary } from '../../core/models';
import { AnalysisService } from '../../core/services/analysis.service';
import {
  isSameRepository,
  parseRepositoryRef,
} from '../../core/util/repository-ref';
import { IconComponent } from '../../shared/ui/icon.component';
import { DEMO_ANALYSIS_ID } from '../../core/services/mock-analysis.service';
import { environment } from '../../../environments/environment';

interface SuggestedRepository {
  readonly url: string;
  readonly label: string;
  readonly detail: string;
}

/**
 * Offered when there is no backend. With one connected, the suggestions come
 * from repositories that have actually been analysed.
 */
const SAMPLE_SUGGESTIONS: readonly SuggestedRepository[] = [
  {
    url: 'github.com/atlas-commerce/atlas-platform',
    label: 'Atlas Commerce Platform',
    detail: 'Angular · Spring Boot · 90,980 lines',
  },
  {
    url: 'github.com/nova-retail/nova-platform',
    label: 'Nova Platform',
    detail: 'Next.js · Go · 53,000 lines',
  },
];

/** Suggested when the backend is connected but nothing has been analysed yet. */
const STARTER_SUGGESTIONS: readonly SuggestedRepository[] = [
  {
    url: 'github.com/spring-projects/spring-petclinic',
    label: 'Spring PetClinic',
    detail: 'Java · Spring Boot · a well-structured reference app',
  },
  {
    url: 'github.com/octocat/Hello-World',
    label: 'Hello-World',
    detail: 'The smallest real repository on GitHub',
  },
];

@Component({
  selector: 'sdna-analyse-page',
  standalone: true,
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analyse-page.component.html',
  styleUrl: './analyse-page.component.scss',
})
export class AnalysePageComponent {
  private readonly router = inject(Router);
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  /** True when a real analysis service is connected. */
  protected readonly liveBackend = environment.useBackend;

  /** The analysis to offer as a shortcut, or null when there is none yet. */
  protected readonly latestAnalysisId = computed<string | null>(() =>
    this.liveBackend
      ? (this.available()[0]?.id ?? null)
      : DEMO_ANALYSIS_ID,
  );

  protected readonly value = signal('');
  protected readonly touched = signal(false);

  /** Repositories this build can actually analyse. */
  private readonly available = signal<readonly AnalysisSummary[]>([]);

  constructor() {
    this.service
      .listAnalyses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((summaries) => this.available.set(summaries));
  }

  protected readonly parsed = computed(() => parseRepositoryRef(this.value()));

  protected readonly isValid = computed(() => this.parsed() !== null);

  /** `owner/repo` once the input parses, empty otherwise. */
  protected readonly parsedLabel = computed(() => {
    const parsed = this.parsed();
    return parsed ? `${parsed.owner}/${parsed.name}` : '';
  });

  protected readonly errorMessage = computed(() => {
    if (!this.touched() || this.value().trim() === '' || this.isValid()) return '';
    return 'That does not look like a repository reference. Try github.com/owner/repository.';
  });

  /** The analysis this build holds for the entered reference, if any. */
  protected readonly matchedAnalysis = computed<AnalysisSummary | null>(() => {
    const parsed = this.parsed();
    if (!parsed) return null;
    return (
      this.available().find((summary) => isSameRepository(summary, parsed)) ??
      null
    );
  });

  /**
   * True only when there is no backend and a well-formed reference cannot be
   * reached. With a backend connected any repository can be analysed for real,
   * so the substitution notice must not appear.
   */
  protected readonly willUseSampleData = computed(
    () => !this.liveBackend && this.isValid() && this.matchedAnalysis() === null,
  );

  /**
   * What to offer as a starting point: repositories already analysed when
   * there are any, otherwise a couple worth trying.
   */
  protected readonly suggestions = computed<readonly SuggestedRepository[]>(() => {
    if (!this.liveBackend) {
      return SAMPLE_SUGGESTIONS;
    }
    const analysed = this.available();
    if (analysed.length === 0) {
      return STARTER_SUGGESTIONS;
    }
    return analysed.map((summary) => ({
      url: summary.url,
      label: summary.displayName,
      detail: `${summary.primaryLanguage ?? 'Mixed'} · health ${Math.round(summary.overall)}`,
    }));
  });

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value);
  }

  protected use(url: string): void {
    this.value.set(url);
    this.touched.set(true);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.touched.set(true);
    if (!this.isValid()) return;

    void this.router.navigate(['/analysing'], {
      queryParams: { repo: this.value().trim() },
    });
  }
}
