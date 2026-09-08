import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { DoctorMessage, DoctorPrompt } from '../../core/models/insight.model';
import { AnalysisService } from '../../core/services/analysis.service';
import { AnalysisStore } from '../../core/services/analysis-store';
import { buildOpeningAssessment } from '../../core/mock/doctor-responses';
import { IconComponent } from '../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateViewComponent } from '../../shared/ui/state-view.component';
import { DoctorBlocksComponent } from './doctor-blocks.component';

@Component({
  selector: 'sdna-ai-doctor',
  standalone: true,
  imports: [
    IconComponent,
    PageHeaderComponent,
    StateViewComponent,
    DoctorBlocksComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ai-doctor-page.component.html',
  styleUrl: './ai-doctor-page.component.scss',
})
export class AiDoctorPageComponent {
  private readonly store = inject(AnalysisStore);
  private readonly service = inject(AnalysisService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly transcriptRef =
    viewChild<ElementRef<HTMLDivElement>>('transcript');

  protected readonly analysis = this.store.analysis;

  protected readonly draft = signal('');
  protected readonly thinking = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  private readonly conversation = signal<readonly DoctorMessage[]>([]);

  /**
   * The opening assessment is generated from the analysis rather than
   * stored, so the Doctor has already "read" the repository before the
   * user asks anything. It is prepended rather than pushed so it can never
   * be cleared by mistake.
   */
  protected readonly messages = computed<readonly DoctorMessage[]>(() => {
    const data = this.analysis();
    if (!data) return [];
    return [buildOpeningAssessment(data), ...this.conversation()];
  });

  protected readonly prompts = computed<readonly DoctorPrompt[]>(
    () => this.analysis()?.doctorPrompts ?? [],
  );

  /** Prompts already asked are hidden so the suggestions stay useful. */
  protected readonly availablePrompts = computed(() => {
    const asked = new Set(
      this.conversation()
        .filter((message) => message.author === 'user')
        .map((message) => message.text.toLowerCase()),
    );
    return this.prompts().filter(
      (prompt) => !asked.has(prompt.question.toLowerCase()),
    );
  });

  protected readonly canSend = computed(
    () => this.draft().trim().length > 0 && !this.thinking(),
  );

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }

  protected onKeydown(event: KeyboardEvent): void {
    // Enter sends; Shift+Enter breaks the line, as in every editor people
    // already know.
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected usePrompt(prompt: DoctorPrompt): void {
    this.ask(prompt.question);
  }

  protected send(): void {
    const question = this.draft().trim();
    if (question === '' || this.thinking()) return;
    this.draft.set('');
    this.ask(question);
  }

  private ask(question: string): void {
    const analysisId = this.analysis()?.id;
    if (!analysisId || this.thinking()) return;

    this.errorMessage.set(null);
    this.thinking.set(true);
    this.conversation.update((messages) => [
      ...messages,
      {
        id: `user-${Date.now()}`,
        author: 'user' as const,
        text: question,
        timestamp: Date.now(),
      },
    ]);
    this.scrollToEnd();

    this.service
      .askDoctor(analysisId, question)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reply) => {
          this.conversation.update((messages) => [...messages, reply]);
          this.thinking.set(false);
          this.scrollToEnd();
        },
        error: () => {
          this.thinking.set(false);
          this.errorMessage.set(
            'The Doctor could not complete that request. Try asking again.',
          );
        },
      });
  }

  private scrollToEnd(): void {
    // After the next paint, so the new message is already laid out.
    requestAnimationFrame(() => {
      const element = this.transcriptRef()?.nativeElement;
      if (element) element.scrollTop = element.scrollHeight;
    });
  }
}
