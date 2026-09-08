import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
  output,
} from '@angular/core';

import { DnaDimension, DnaDimensionKey } from '../../core/models/dna.model';
import { healthRamp } from '../../core/util/health';
import { HelixConfig, helixPoint, strandPath, depthWeight } from './dna-geometry';

interface Rung {
  readonly key: string;
  readonly x1: number;
  readonly y1: number;
  readonly x2: number;
  readonly y2: number;
  readonly r1: number;
  readonly r2: number;
  readonly opacity: number;
  readonly index: number;
}

interface Band {
  readonly key: DnaDimensionKey;
  readonly label: string;
  readonly short: string;
  /** Null when the dimension could not be measured. */
  readonly score: number | null;
  readonly color: string;
  readonly rungs: readonly Rung[];
  readonly labelX: number;
  readonly labelY: number;
  readonly anchorX: number;
  readonly anchorY: number;
  readonly labelAnchor: 'start' | 'end';
  /** Hit area covering the band's vertical extent. */
  readonly hitY: number;
  readonly hitHeight: number;
  readonly index: number;
}

// Proportions matter more here than anywhere else in the product: each
// dimension owns half a turn, so the viewBox must be tall enough that a
// lobe is taller than it is wide. Squat lobes read as a stack of lenses,
// not as a helix.
const VIEW_W = 420;
const VIEW_H = 740;
const RUNGS_PER_BAND = 7;

const CONFIG: HelixConfig = {
  centerX: VIEW_W / 2,
  top: 30,
  height: VIEW_H - 60,
  amplitude: 44,
  turns: 4,
  phase: 0,
};

/** Four-letter labels keep the helix readable without crowding it. */
const SHORT_LABELS: Readonly<Record<DnaDimensionKey, string>> = {
  architecture: 'ARCH',
  maintainability: 'MNTN',
  security: 'SECR',
  performance: 'PERF',
  testing: 'TEST',
  dependencies: 'DEPS',
  documentation: 'DOCS',
  evolution: 'EVOL',
};

/**
 * The Software DNA itself.
 *
 * Each dimension owns exactly one lobe of the helix — the span between two
 * crossovers — so the eight dimensions read as eight genes rather than
 * eight arbitrary slices. Rung colour is the dimension's health, so the
 * shape of the object *is* the health profile: a repository with a weak
 * dimension has a visibly discoloured section.
 *
 * Rendered as SVG rather than canvas because every band is a focusable,
 * labelled control; the element count (~200) is well within what SVG
 * handles smoothly, and the geometry is recomputed only when the data or
 * the highlight changes, never per frame.
 */
@Component({
  selector: 'sdna-dna-helix',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.viewBox]="'0 0 ' + VIEW_W + ' ' + VIEW_H"
      role="group"
      [attr.aria-label]="'Software DNA: ' + dimensions().length + ' dimensions'"
    >
      <defs>
        <filter id="dna-glow" x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation="5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- Backbone. Drawn beneath the rungs so the rungs read as the data. -->
      <g class="backbone" [class.dimmed]="activeKey() !== null">
        <path [attr.d]="strandA()" />
        <path [attr.d]="strandB()" />
      </g>

      @for (band of bands(); track band.key) {
        <g
          class="band"
          [class.is-active]="activeKey() === band.key"
          [class.is-muted]="activeKey() !== null && activeKey() !== band.key"
          [style.--band-index]="band.index"
        >
          @for (rung of band.rungs; track rung.key) {
            <g class="rung" [style.--rung-index]="rung.index">
              <line
                [attr.x1]="rung.x1"
                [attr.y1]="rung.y1"
                [attr.x2]="rung.x2"
                [attr.y2]="rung.y2"
                [attr.stroke]="band.color"
                [attr.stroke-opacity]="rung.opacity * 0.5"
                stroke-width="1.5"
                stroke-linecap="round"
              />
              <circle
                [attr.cx]="rung.x1"
                [attr.cy]="rung.y1"
                [attr.r]="rung.r1"
                [attr.fill]="band.color"
                [attr.fill-opacity]="rung.opacity"
              />
              <circle
                [attr.cx]="rung.x2"
                [attr.cy]="rung.y2"
                [attr.r]="rung.r2"
                [attr.fill]="band.color"
                [attr.fill-opacity]="rung.opacity"
              />
            </g>
          }

          <!-- Leader line and label -->
          <line
            class="leader"
            [attr.x1]="band.anchorX"
            [attr.y1]="band.anchorY"
            [attr.x2]="band.labelX + (band.labelAnchor === 'start' ? -8 : 8)"
            [attr.y2]="band.labelY"
            [attr.stroke]="band.color"
          />
          <text
            class="band-label"
            [attr.x]="band.labelX"
            [attr.y]="band.labelY - 4"
            [attr.text-anchor]="band.labelAnchor"
          >
            {{ band.short }}
          </text>
          <text
            class="band-score"
            [attr.x]="band.labelX"
            [attr.y]="band.labelY + 13"
            [attr.text-anchor]="band.labelAnchor"
            [attr.fill]="band.color"
          >
            {{ band.score === null ? '—' : band.score }}
          </text>

          <!-- Hit target. Sits last so it captures the pointer, and carries
               the accessible name and keyboard behaviour for the band. -->
          <rect
            class="hit"
            x="0"
            [attr.y]="band.hitY"
            [attr.width]="VIEW_W"
            [attr.height]="band.hitHeight"
            tabindex="0"
            role="button"
            [attr.aria-label]="
              band.label + ', score ' + band.score + ' out of 100'
            "
            [attr.aria-pressed]="activeKey() === band.key"
            (pointerenter)="activeKey.set(band.key)"
            (pointerleave)="activeKey.set(null)"
            (focus)="activeKey.set(band.key)"
            (blur)="activeKey.set(null)"
            (click)="selected.emit(band.key)"
            (keydown.enter)="selected.emit(band.key)"
            (keydown.space)="$event.preventDefault(); selected.emit(band.key)"
          />
        </g>
      }
    </svg>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }

      svg {
        display: block;
        width: 100%;
        height: auto;
        overflow: visible;
      }

      .backbone path {
        fill: none;
        stroke: var(--border-strong);
        stroke-width: 1.25;
        stroke-linecap: round;
        transition: stroke-opacity var(--dur-normal) var(--ease-out);
        stroke-dasharray: 2400;
        stroke-dashoffset: 2400;
        animation: draw-strand 1400ms var(--ease-out) forwards;
      }

      .backbone path:nth-child(2) {
        animation-delay: 120ms;
      }

      .backbone.dimmed path {
        stroke-opacity: 0.5;
      }

      @keyframes draw-strand {
        to {
          stroke-dashoffset: 0;
        }
      }

      .band {
        transition: opacity var(--dur-normal) var(--ease-out);
      }

      .band.is-muted {
        opacity: 0.22;
      }

      .rung {
        opacity: 0;
        animation: rung-in 480ms var(--ease-out) forwards;
        animation-delay: calc(
          260ms + (var(--band-index) * 8 + var(--rung-index)) * 14ms
        );
        transform-box: fill-box;
        transform-origin: center;
      }

      @keyframes rung-in {
        from {
          opacity: 0;
          transform: scaleX(0.4);
        }
        to {
          opacity: 1;
          transform: none;
        }
      }

      .band.is-active .rung circle {
        filter: url(#dna-glow);
      }

      .leader {
        stroke-width: 1;
        stroke-opacity: 0;
        transition: stroke-opacity var(--dur-normal) var(--ease-out);
      }

      .band.is-active .leader {
        stroke-opacity: 0.45;
      }

      .band-label {
        font-family: var(--font-mono);
        font-size: 10px;
        letter-spacing: 0.16em;
        fill: var(--text-muted);
        transition: fill var(--dur-fast) var(--ease-out);
      }

      .band.is-active .band-label {
        fill: var(--text-primary);
      }

      .band-score {
        font-family: var(--font-mono);
        font-size: 15px;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
        letter-spacing: -0.02em;
      }

      .hit {
        fill: transparent;
        cursor: pointer;
        outline: none;
      }

      .hit:focus-visible {
        fill: var(--accent-softer);
        stroke: var(--accent);
        stroke-width: 1;
        stroke-dasharray: 3 3;
      }

      @media (prefers-reduced-motion: reduce) {
        .backbone path {
          animation: none;
          stroke-dashoffset: 0;
        }
        .rung {
          animation: none;
          opacity: 1;
        }
      }

      /* Below the desktop breakpoint the labels crowd the helix; the
         dimension list beside it carries the same information. */
      @media (max-width: 720px) {
        .band-label,
        .band-score,
        .leader {
          display: none;
        }
      }
    `,
  ],
})
export class DnaHelixComponent {
  protected readonly VIEW_W = VIEW_W;
  protected readonly VIEW_H = VIEW_H;

  readonly dimensions = input.required<readonly DnaDimension[]>();

  /** Two-way so a hovered dimension card and the helix stay in step. */
  readonly activeKey = model<DnaDimensionKey | null>(null);

  readonly selected = output<DnaDimensionKey>();

  readonly strandA = computed(() => strandPath(CONFIG, 0));
  readonly strandB = computed(() => strandPath(CONFIG, 1));

  readonly bands = computed<readonly Band[]>(() => {
    const dimensions = this.dimensions();
    const count = dimensions.length;
    if (count === 0) return [];

    const bandSpan = 1 / count;

    return dimensions.map((dimension, index) => {
      const bandStart = index * bandSpan;
      // An unmeasured dimension is neutral, not critical. Colouring it red
      // would report an absence of evidence as bad news.
      const color =
        dimension.score === null
          ? 'var(--text-faint)'
          : healthRamp(dimension.score);

      const rungs: Rung[] = [];
      for (let j = 1; j <= RUNGS_PER_BAND; j++) {
        const t = bandStart + (j / (RUNGS_PER_BAND + 1)) * bandSpan;
        const a = helixPoint(CONFIG, t, 0);
        const b = helixPoint(CONFIG, t, 1);
        const wa = depthWeight(a.depth);
        const wb = depthWeight(b.depth);
        rungs.push({
          key: `${dimension.key}-${j}`,
          x1: a.x,
          y1: a.y,
          x2: b.x,
          y2: b.y,
          r1: 1.6 + wa * 2.4,
          r2: 1.6 + wb * 2.4,
          // Rungs at the crossovers are edge-on, so they fade slightly.
          opacity: 0.45 + Math.abs(a.x - b.x) / (CONFIG.amplitude * 2) * 0.55,
          index: j - 1,
        });
      }

      // The band's widest point is where its label belongs.
      const midT = bandStart + bandSpan / 2;
      const anchor = helixPoint(CONFIG, midT, 0);
      const onRight = anchor.x > CONFIG.centerX;
      const labelX = onRight
        ? CONFIG.centerX + CONFIG.amplitude + 40
        : CONFIG.centerX - CONFIG.amplitude - 40;

      const top = helixPoint(CONFIG, bandStart, 0).y;
      const bottom = helixPoint(CONFIG, bandStart + bandSpan, 0).y;

      return {
        key: dimension.key,
        label: dimension.label,
        short: SHORT_LABELS[dimension.key] ?? dimension.label.slice(0, 4).toUpperCase(),
        score: dimension.score,
        color,
        rungs,
        labelX,
        labelY: anchor.y,
        anchorX: anchor.x,
        anchorY: anchor.y,
        labelAnchor: onRight ? 'start' : 'end',
        hitY: top,
        hitHeight: bottom - top,
        index,
      };
    });
  });
}
