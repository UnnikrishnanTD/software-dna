/**
 * Helix geometry shared by the canvas hero and the interactive SVG.
 *
 * The helix runs vertically. `t` travels 0 → 1 from top to bottom, and each
 * strand is a sine wave offset by half a period from the other. The cosine
 * of the same angle gives a depth value in −1…1, which both renderers use
 * to fake perspective: points at the front are larger and brighter than
 * points curving away behind the axis.
 */

export interface HelixPoint {
  readonly x: number;
  readonly y: number;
  /** −1 (far) … 1 (near). */
  readonly depth: number;
}

export interface HelixConfig {
  /** Horizontal centre of the helix axis. */
  readonly centerX: number;
  readonly top: number;
  readonly height: number;
  /** Half the horizontal span of a strand. */
  readonly amplitude: number;
  /** Full rotations over the length of the helix. */
  readonly turns: number;
  /** Rotation offset in radians; animating this spins the helix. */
  readonly phase: number;
}

/** Position of one strand at parameter `t`. `strand` is 0 or 1. */
export function helixPoint(
  config: HelixConfig,
  t: number,
  strand: 0 | 1,
): HelixPoint {
  const angle =
    t * config.turns * Math.PI * 2 + config.phase + (strand === 1 ? Math.PI : 0);
  return {
    x: config.centerX + Math.sin(angle) * config.amplitude,
    y: config.top + t * config.height,
    depth: Math.cos(angle),
  };
}

/** Samples one strand into an SVG path string. */
export function strandPath(
  config: HelixConfig,
  strand: 0 | 1,
  samples = 160,
): string {
  let path = '';
  for (let i = 0; i <= samples; i++) {
    const point = helixPoint(config, i / samples, strand);
    path += `${i === 0 ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)}`;
  }
  return path;
}

/**
 * Maps depth to a rendering weight in 0…1. Front-facing points get close to
 * 1, points behind the axis fall away — this is what stops the helix from
 * reading as a flat zig-zag.
 */
export function depthWeight(depth: number): number {
  return (depth + 1) / 2;
}
