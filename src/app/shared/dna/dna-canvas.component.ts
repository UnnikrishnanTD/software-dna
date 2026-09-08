import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  NgZone,
  viewChild,
  afterNextRender,
} from '@angular/core';

import { depthWeight, HelixConfig, helixPoint } from './dna-geometry';
import { prefersReducedMotion } from '../util/motion';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  alpha: number;
}

interface BandDefinition {
  readonly label: string;
  readonly color: string;
}

/** The six dimensions surfaced on the landing helix, top to bottom. */
const BANDS: readonly BandDefinition[] = [
  { label: 'ARCHITECTURE', color: '#5b8cff' },
  { label: 'SECURITY', color: '#3ecf8e' },
  { label: 'TESTING', color: '#e9b44c' },
  { label: 'PERFORMANCE', color: '#3ecfb2' },
  { label: 'MAINTAINABILITY', color: '#7ea6ff' },
  { label: 'DEPENDENCIES', color: '#5fe3c8' },
];

const SAMPLES_PER_STRAND = 88;
const PARTICLE_COUNT = 54;

/**
 * The landing hero's living DNA.
 *
 * Canvas rather than SVG: this runs a continuous animation over roughly two
 * hundred moving primitives with per-frame glow and cursor falloff. As SVG
 * that would mean mutating two hundred DOM nodes every frame; on canvas it
 * is a single element and the whole loop lives outside Angular's zone, so
 * it costs zero change-detection cycles.
 *
 * The helix eases toward the pointer rather than tracking it directly,
 * which reads as the object noticing you rather than following you.
 */
@Component({
  selector: 'sdna-dna-canvas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <canvas #canvas aria-hidden="true"></canvas>
    <p class="sr-only">
      An animated double helix representing a software DNA profile, with
      strands labelled architecture, security, testing, performance,
      maintainability and dependencies.
    </p>
  `,
  styles: [
    `
      :host {
        display: block;
        position: relative;
        width: 100%;
        height: 100%;
      }

      canvas {
        display: block;
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class DnaCanvasComponent {
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject(ElementRef<HTMLElement>);

  private readonly canvasRef =
    viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  /** Scales the helix within its container. */
  readonly amplitudeRatio = input(0.2);

  private context: CanvasRenderingContext2D | null = null;
  private frameId = 0;
  private width = 0;
  private height = 0;
  private dpr = 1;

  private phase = 0;
  private targetPhase = 0;
  private pointerX: number | null = null;
  private pointerY: number | null = null;
  private glowX = -1;
  private glowY = -1;
  private activeBand = -1;
  private bandFade = 0;
  private particles: Particle[] = [];
  private running = false;
  private observer: ResizeObserver | undefined;
  private visibility: IntersectionObserver | undefined;

  constructor() {
    afterNextRender(() => this.initialise());

    this.destroyRef.onDestroy(() => {
      this.stop();
      this.observer?.disconnect();
      this.visibility?.disconnect();
      const element = this.host.nativeElement;
      element.removeEventListener('pointermove', this.onPointerMove);
      element.removeEventListener('pointerleave', this.onPointerLeave);
    });
  }

  private initialise(): void {
    const canvas = this.canvasRef().nativeElement;
    this.context = canvas.getContext('2d');
    if (!this.context) return;

    this.resize();
    this.seedParticles();

    this.observer = new ResizeObserver(() => this.resize());
    this.observer.observe(this.host.nativeElement);

    // Nothing animates while the hero is scrolled out of view.
    this.visibility = new IntersectionObserver(
      ([entry]) => (entry.isIntersecting ? this.start() : this.stop()),
      { threshold: 0 },
    );
    this.visibility.observe(this.host.nativeElement);

    this.zone.runOutsideAngular(() => {
      const element = this.host.nativeElement;
      element.addEventListener('pointermove', this.onPointerMove, {
        passive: true,
      });
      element.addEventListener('pointerleave', this.onPointerLeave, {
        passive: true,
      });
    });

    if (prefersReducedMotion()) {
      // A single composed frame: the object still reads as a helix, it
      // simply does not move.
      this.draw(0);
    } else {
      this.start();
    }
  }

  private readonly onPointerMove = (event: PointerEvent): void => {
    const rect = this.host.nativeElement.getBoundingClientRect();
    this.pointerX = event.clientX - rect.left;
    this.pointerY = event.clientY - rect.top;
  };

  private readonly onPointerLeave = (): void => {
    this.pointerX = null;
    this.pointerY = null;
  };

  private start(): void {
    if (this.running || prefersReducedMotion()) return;
    this.running = true;
    this.zone.runOutsideAngular(() => {
      const loop = (time: number): void => {
        if (!this.running) return;
        this.draw(time);
        this.frameId = requestAnimationFrame(loop);
      };
      this.frameId = requestAnimationFrame(loop);
    });
  }

  private stop(): void {
    this.running = false;
    if (this.frameId) cancelAnimationFrame(this.frameId);
    this.frameId = 0;
  }

  private resize(): void {
    const canvas = this.canvasRef().nativeElement;
    const rect = this.host.nativeElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    // Cap the pixel ratio: beyond 2× the extra fill cost buys nothing
    // visible for soft glows.
    this.dpr = Math.min(devicePixelRatio || 1, 2);
    this.width = rect.width;
    this.height = rect.height;
    canvas.width = Math.round(rect.width * this.dpr);
    canvas.height = Math.round(rect.height * this.dpr);
    this.context?.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);

    this.seedParticles();
    if (prefersReducedMotion()) this.draw(0);
  }

  private seedParticles(): void {
    this.particles = Array.from({ length: PARTICLE_COUNT }, () => ({
      x: Math.random() * this.width,
      y: Math.random() * this.height,
      vx: (Math.random() - 0.5) * 0.12,
      vy: (Math.random() - 0.5) * 0.12 - 0.04,
      radius: 0.5 + Math.random() * 1.1,
      alpha: 0.08 + Math.random() * 0.22,
    }));
  }

  private config(): HelixConfig {
    return {
      centerX: this.width / 2,
      top: this.height * 0.06,
      height: this.height * 0.88,
      // Fewer, taller turns give the helix more presence in the hero while
      // keeping each turn taller than it is wide, which is what sells depth.
      amplitude: Math.min(this.width * this.amplitudeRatio(), this.height * 0.135),
      turns: 2.6,
      phase: this.phase,
    };
  }

  private draw(time: number): void {
    const ctx = this.context;
    if (!ctx || this.width === 0) return;

    ctx.clearRect(0, 0, this.width, this.height);

    // The helix drifts on its own; the pointer adds an offset it eases into.
    const drift = time * 0.00022;
    const pointerInfluence =
      this.pointerX === null
        ? 0
        : ((this.pointerX - this.width / 2) / this.width) * 1.6;
    this.targetPhase = drift + pointerInfluence;
    this.phase += (this.targetPhase - this.phase) * 0.06;

    const glowTargetX = this.pointerX ?? this.width / 2;
    const glowTargetY = this.pointerY ?? this.height / 2;
    this.glowX += (glowTargetX - this.glowX) * 0.1;
    this.glowY += (glowTargetY - this.glowY) * 0.1;

    this.drawParticles(ctx);

    const config = this.config();
    this.drawStrand(ctx, config, 0);
    this.drawStrand(ctx, config, 1);
    this.drawRungs(ctx, config);
    this.drawNodes(ctx, config);
    this.drawBandLabel(ctx, config);
  }

  private drawParticles(ctx: CanvasRenderingContext2D): void {
    for (const particle of this.particles) {
      particle.x += particle.vx;
      particle.y += particle.vy;
      if (particle.y < -4) particle.y = this.height + 4;
      if (particle.y > this.height + 4) particle.y = -4;
      if (particle.x < -4) particle.x = this.width + 4;
      if (particle.x > this.width + 4) particle.x = -4;

      ctx.beginPath();
      ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(151, 162, 178, ${particle.alpha})`;
      ctx.fill();
    }
  }

  /**
   * Draws one strand as depth-shaded segments rather than a single path.
   *
   * A uniform stroke makes both strands equally present, which reads as a
   * chain of flat lenses. Fading and thinning each segment by its depth is
   * what makes one strand pass visibly behind the other.
   */
  private drawStrand(
    ctx: CanvasRenderingContext2D,
    config: HelixConfig,
    strand: 0 | 1,
  ): void {
    let previous = helixPoint(config, 0, strand);
    for (let i = 1; i <= SAMPLES_PER_STRAND; i++) {
      const point = helixPoint(config, i / SAMPLES_PER_STRAND, strand);
      const weight = depthWeight((previous.depth + point.depth) / 2);

      ctx.beginPath();
      ctx.moveTo(previous.x, previous.y);
      ctx.lineTo(point.x, point.y);
      ctx.strokeStyle = `rgba(104, 120, 145, ${0.1 + weight * 0.5})`;
      ctx.lineWidth = 0.6 + weight * 1.3;
      ctx.stroke();

      previous = point;
    }
  }

  private drawRungs(
    ctx: CanvasRenderingContext2D,
    config: HelixConfig,
  ): void {
    const count = 34;
    for (let i = 0; i <= count; i++) {
      const t = i / count;
      const a = helixPoint(config, t, 0);
      const b = helixPoint(config, t, 1);
      const band = BANDS[Math.min(Math.floor(t * BANDS.length), BANDS.length - 1)];
      const spread = Math.abs(a.x - b.x) / (config.amplitude * 2);
      const proximity = this.proximity((a.x + b.x) / 2, a.y);
      // A rung is most visible when it faces the viewer, which is exactly
      // when the two strands are furthest apart on screen.
      const facing = depthWeight(Math.max(a.depth, b.depth));

      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.strokeStyle = withAlpha(
        band.color,
        0.05 + spread * 0.3 * facing + proximity * 0.35,
      );
      ctx.lineWidth = 0.8 + proximity * 0.7;
      ctx.stroke();
    }
  }

  private drawNodes(
    ctx: CanvasRenderingContext2D,
    config: HelixConfig,
  ): void {
    // Collect both strands, then paint back-to-front. Drawing one strand
    // fully and then the other makes the second always sit on top, which
    // is what flattens a helix into a zig-zag.
    interface Node {
      readonly x: number;
      readonly y: number;
      readonly depth: number;
      readonly color: string;
      readonly proximity: number;
    }

    const nodes: Node[] = [];
    for (const strand of [0, 1] as const) {
      for (let i = 0; i <= SAMPLES_PER_STRAND; i += 2) {
        const t = i / SAMPLES_PER_STRAND;
        const point = helixPoint(config, t, strand);
        nodes.push({
          x: point.x,
          y: point.y,
          depth: point.depth,
          color:
            BANDS[Math.min(Math.floor(t * BANDS.length), BANDS.length - 1)].color,
          proximity: this.proximity(point.x, point.y),
        });
      }
    }
    nodes.sort((a, b) => a.depth - b.depth);

    for (const node of nodes) {
      const weight = depthWeight(node.depth);
      const radius = (0.9 + weight * 2.4) * (1 + node.proximity * 0.9);
      const alpha = 0.14 + weight * 0.62 + node.proximity * 0.4;

      if (node.proximity > 0.05) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, radius * 3.6, 0, Math.PI * 2);
        ctx.fillStyle = withAlpha(node.color, node.proximity * 0.11);
        ctx.fill();
      }

      ctx.beginPath();
      ctx.arc(node.x, node.y, radius, 0, Math.PI * 2);
      ctx.fillStyle = withAlpha(node.color, Math.min(alpha, 1));
      ctx.fill();
    }
  }

  /** 0 … 1 falloff around the eased pointer position. */
  private proximity(x: number, y: number): number {
    if (this.pointerX === null) return 0;
    const radius = Math.min(this.width, this.height) * 0.28;
    const distance = Math.hypot(x - this.glowX, y - this.glowY);
    return Math.max(0, 1 - distance / radius) ** 2;
  }

  /** Names the dimension nearest the pointer, fading in and out. */
  private drawBandLabel(
    ctx: CanvasRenderingContext2D,
    config: HelixConfig,
  ): void {
    const hovering = this.pointerY !== null;
    const index = hovering
      ? Math.min(
          Math.floor(((this.glowY - config.top) / config.height) * BANDS.length),
          BANDS.length - 1,
        )
      : -1;

    if (index !== this.activeBand && index >= 0) this.activeBand = index;

    const target = hovering && index >= 0 ? 1 : 0;
    this.bandFade += (target - this.bandFade) * 0.12;
    if (this.bandFade < 0.01 || this.activeBand < 0) return;

    const band = BANDS[this.activeBand];
    const bandCenterT = (this.activeBand + 0.5) / BANDS.length;
    const y = config.top + bandCenterT * config.height;
    const x = config.centerX + config.amplitude + 28;

    ctx.save();
    ctx.globalAlpha = this.bandFade;

    ctx.beginPath();
    ctx.moveTo(config.centerX + config.amplitude * 0.4, y);
    ctx.lineTo(x - 10, y);
    ctx.strokeStyle = withAlpha(band.color, 0.4);
    ctx.lineWidth = 1;
    ctx.stroke();

    ctx.font =
      '500 10px "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace';
    ctx.letterSpacing = '0.18em';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = withAlpha(band.color, 0.9);
    ctx.fillText(band.label, x, y);
    ctx.restore();
  }
}

/** Applies an alpha to a six-digit hex colour. */
function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '');
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${Math.max(0, Math.min(alpha, 1))})`;
}
