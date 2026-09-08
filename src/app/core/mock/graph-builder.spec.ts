import { buildGraph, complexityBandFor, edge, ArchitectureNodeSpec } from './graph-builder';
import { ATLAS_ARCHITECTURE } from './atlas/architecture.data';

function spec(
  id: string,
  overrides: Partial<ArchitectureNodeSpec> = {},
): ArchitectureNodeSpec {
  return {
    id,
    name: id,
    kind: 'service',
    layer: 'domain',
    path: `src/${id}.ts`,
    language: 'TypeScript',
    complexityScore: 5,
    linesOfCode: 100,
    coverage: 90,
    description: '',
    ...overrides,
  };
}

describe('buildGraph', () => {
  it('derives fan-out and fan-in from the edge list', () => {
    const graph = buildGraph(
      [spec('a'), spec('b'), spec('c')],
      [edge('a', 'b'), edge('a', 'c'), edge('b', 'c')],
    );

    const byId = new Map(graph.nodes.map((node) => [node.id, node]));
    expect(byId.get('a')?.fanOut).toBe(2);
    expect(byId.get('a')?.fanIn).toBe(0);
    expect(byId.get('c')?.fanIn).toBe(2);
    expect(byId.get('c')?.fanOut).toBe(0);
  });

  it('raises risk as complexity, coupling and coverage gaps compound', () => {
    const safe = buildGraph(
      [spec('safe', { complexityScore: 4, coverage: 95 })],
      [],
    ).nodes[0];

    const dangerous = buildGraph(
      [
        spec('hub', { complexityScore: 34, coverage: 40 }),
        ...Array.from({ length: 10 }, (_, i) => spec(`leaf${i}`)),
      ],
      Array.from({ length: 10 }, (_, i) => edge('hub', `leaf${i}`)),
    ).nodes.find((node) => node.id === 'hub');

    expect(safe.risk).toBe('low');
    expect(dangerous?.risk).toBe('critical');
  });

  it('detects a mutual dependency as a cycle', () => {
    const graph = buildGraph(
      [spec('a'), spec('b')],
      [edge('a', 'b'), edge('b', 'a')],
    );
    expect(graph.circularDependencies.length).toBe(1);
    expect(graph.circularDependencies[0].sort()).toEqual(['a', 'b']);
  });

  it('records a cycle once rather than once per rotation', () => {
    const graph = buildGraph(
      [spec('a'), spec('b'), spec('c')],
      [edge('a', 'b'), edge('b', 'c'), edge('c', 'a')],
    );
    expect(graph.circularDependencies.length).toBe(1);
  });

  it('reports no cycles for an acyclic graph', () => {
    const graph = buildGraph(
      [spec('a'), spec('b'), spec('c')],
      [edge('a', 'b'), edge('b', 'c')],
    );
    expect(graph.circularDependencies).toEqual([]);
  });

  it('bands complexity scores', () => {
    expect(complexityBandFor(3)).toBe('low');
    expect(complexityBandFor(10)).toBe('medium');
    expect(complexityBandFor(20)).toBe('high');
    expect(complexityBandFor(34)).toBe('very-high');
  });
});

describe('the Atlas graph', () => {
  it('never references a node that does not exist', () => {
    const ids = new Set(ATLAS_ARCHITECTURE.nodes.map((node) => node.id));
    for (const edge of ATLAS_ARCHITECTURE.edges) {
      expect(ids.has(edge.source)).toBe(true);
      expect(ids.has(edge.target)).toBe(true);
    }
  });

  it('carries the story the product tells: identity is the riskiest unit', () => {
    const userService = ATLAS_ARCHITECTURE.nodes.find(
      (node) => node.id === 'user-service',
    );
    expect(userService?.risk).toBe('critical');
    expect(userService?.fanOut).toBeGreaterThanOrEqual(8);
  });

  it('routes both of its circular dependencies through UserService', () => {
    expect(ATLAS_ARCHITECTURE.circularDependencies.length).toBe(2);
    for (const cycle of ATLAS_ARCHITECTURE.circularDependencies) {
      expect(cycle).toContain('user-service');
    }
  });
});
