import { RiskLevel } from './architecture.model';
import { DnaDimensionKey } from './dna.model';

export type InsightKind = 'strength' | 'risk' | 'opportunity' | 'observation';

/** A single finding produced by the analysis. */
export interface AiInsight {
  readonly id: string;
  readonly kind: InsightKind;
  readonly severity: RiskLevel;
  readonly title: string;
  readonly body: string;
  readonly dimension: DnaDimensionKey;
  /** Architecture node ids this insight refers to, for cross-linking. */
  readonly relatedNodeIds: readonly string[];
  readonly confidence: number;
}

/** A prioritised remediation step surfaced by the AI Doctor. */
export interface RemediationStep {
  readonly rank: number;
  readonly title: string;
  readonly detail: string;
  readonly effort: 'low' | 'medium' | 'high';
  readonly expectedGain: number;
  readonly dimension: DnaDimensionKey;
}

export type DoctorAuthor = 'user' | 'doctor';

/** A block within a Doctor answer. Blocks let the answer render as a
 *  structured diagnostic rather than a wall of chat text. */
export type DoctorBlock =
  | { readonly type: 'text'; readonly text: string }
  | { readonly type: 'metric'; readonly label: string; readonly value: number; readonly verdictScore: number }
  | { readonly type: 'list'; readonly ordered: boolean; readonly items: readonly string[] }
  | { readonly type: 'nodes'; readonly caption: string; readonly nodeIds: readonly string[] }
  | { readonly type: 'plan'; readonly steps: readonly RemediationStep[] };

export interface DoctorMessage {
  readonly id: string;
  readonly author: DoctorAuthor;
  readonly text: string;
  readonly blocks?: readonly DoctorBlock[];
  readonly timestamp: number;
}

/** A canned question offered to the user as a starting point. */
export interface DoctorPrompt {
  readonly id: string;
  readonly question: string;
  readonly category: 'diagnose' | 'prioritise' | 'explain' | 'history';
}
