import {
  ArchitectureEdge,
  ArchitectureGraph,
  ArchitectureNode,
  ComplexityBand,
  RiskLevel,
} from '../models/architecture.model';

/**
 * The authored half of an architecture node. Coupling counts and risk are
 * derived from the edge list rather than typed by hand, so the mock data
 * can never contradict its own graph.
 */
export type ArchitectureNodeSpec = Omit<
  ArchitectureNode,
  'complexity' | 'fanIn' | 'fanOut' | 'risk'
>;

export function complexityBandFor(score: number): ComplexityBand {
  if (score >= 28) return 'very-high';
  if (score >= 16) return 'high';
  if (score >= 8) return 'medium';
  return 'low';
}

/**
 * Risk is a weighted blend of the three things that actually make a unit
 * dangerous to change: how tangled it is, how exposed it is (fan-out), and
 * how little of it is covered by tests.
 */
function riskFor(
  complexityScore: number,
  fanOut: number,
  coverage: number,
): RiskLevel {
  const complexityPressure = Math.min(complexityScore / 34, 1);
  const couplingPressure = Math.min(fanOut / 18, 1);
  const coverageGap = (100 - coverage) / 100;
  const score =
    complexityPressure * 0.4 + couplingPressure * 0.3 + coverageGap * 0.3;

  if (score >= 0.68) return 'critical';
  if (score >= 0.5) return 'high';
  if (score >= 0.3) return 'medium';
  return 'low';
}

/** Detects cycles between nodes using an iterative depth-first search. */
function findCycles(
  nodes: readonly ArchitectureNodeSpec[],
  edges: readonly ArchitectureEdge[],
): string[][] {
  const adjacency = new Map<string, string[]>();
  for (const node of nodes) adjacency.set(node.id, []);
  for (const edge of edges) adjacency.get(edge.source)?.push(edge.target);

  const cycles: string[][] = [];
  const seen = new Set<string>();
  const inPath = new Set<string>();
  const path: string[] = [];

  const visit = (id: string): void => {
    if (inPath.has(id)) {
      const start = path.indexOf(id);
      if (start !== -1) {
        const cycle = path.slice(start);
        // Normalise rotation so the same cycle is never recorded twice.
        const key = [...cycle].sort().join('|');
        if (!cycles.some((c) => [...c].sort().join('|') === key)) {
          cycles.push(cycle);
        }
      }
      return;
    }
    if (seen.has(id)) return;

    seen.add(id);
    inPath.add(id);
    path.push(id);
    for (const next of adjacency.get(id) ?? []) visit(next);
    path.pop();
    inPath.delete(id);
  };

  for (const node of nodes) visit(node.id);
  return cycles;
}

/** Assembles a complete, self-consistent architecture graph. */
export function buildGraph(
  specs: readonly ArchitectureNodeSpec[],
  edges: readonly ArchitectureEdge[],
): ArchitectureGraph {
  const fanOut = new Map<string, number>();
  const fanIn = new Map<string, number>();

  for (const edge of edges) {
    fanOut.set(edge.source, (fanOut.get(edge.source) ?? 0) + 1);
    fanIn.set(edge.target, (fanIn.get(edge.target) ?? 0) + 1);
  }

  const nodes: ArchitectureNode[] = specs.map((spec) => {
    const out = fanOut.get(spec.id) ?? 0;
    const incoming = fanIn.get(spec.id) ?? 0;
    return {
      ...spec,
      complexity: complexityBandFor(spec.complexityScore),
      fanOut: out,
      fanIn: incoming,
      risk: riskFor(spec.complexityScore, out, spec.coverage ?? 100),
    };
  });

  return {
    nodes,
    edges,
    circularDependencies: findCycles(specs, edges),
  };
}

/** Convenience builder so the authored edge list stays readable. */
export function edge(
  source: string,
  target: string,
  kind: ArchitectureEdge['kind'] = 'imports',
  weight = 3,
): ArchitectureEdge {
  return { id: `${source}->${target}`, source, target, kind, weight };
}
