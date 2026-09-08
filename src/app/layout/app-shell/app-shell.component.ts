import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import {
  ActivatedRoute,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, map } from 'rxjs/operators';

import { AnalysisStore } from '../../core/services/analysis-store';
import { IconComponent } from '../../shared/ui/icon.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { VerdictBadgeComponent } from '../../shared/ui/verdict-badge.component';
import { NAV_ITEMS } from '../side-navigation/nav-items';
import { environment } from '../../../environments/environment';

/**
 * The frame every analysis screen lives inside.
 *
 * It owns the `:id` route parameter and the store, so no child route loads
 * data — they read the analysis from the store and render. That is what
 * keeps the feature components free of any knowledge of where data comes
 * from.
 */
@Component({
  selector: 'sdna-app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    IconComponent,
    StateViewComponent,
    VerdictBadgeComponent,
  ],
  providers: [AnalysisStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly store = inject(AnalysisStore);

  protected readonly navItems = NAV_ITEMS;
  protected readonly liveBackend = environment.useBackend;
  protected readonly mobileNavOpen = signal(false);

  private readonly destroyRef = inject(DestroyRef);

  protected readonly analysis = this.store.analysis;
  protected readonly repositoryName = computed(
    () => this.analysis()?.repository.displayName ?? '',
  );

  constructor() {
    // Driven from the route stream rather than an effect: loading is a
    // reaction to navigation, not to a signal read, and this keeps the
    // store write out of the reactive graph entirely.
    this.route.paramMap
      .pipe(
        map((params) => params.get('id') ?? ''),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((id) => {
        if (id) this.store.load(id);
      });
  }

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  protected goHome(): void {
    void this.router.navigate(['/']);
  }
}
