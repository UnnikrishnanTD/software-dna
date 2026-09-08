import { ATLAS_ARCHITECTURE } from '../../core/mock/atlas/architecture.data';
import { buildGraph, edge, ArchitectureNodeSpec } from '../../core/mock/graph-builder';
import { layoutGraph, NODE_WIDTH } from './graph-layout';

function spec(
  id: string,
  layer: ArchitectureNodeSpec['layer'],
): ArchitectureNodeSpec {
  return {
    id,
    name: id,
    kind: 'service',
    layer,
    path: id,
    language: 'TypeScript',
    complexityScore: 5,
    linesOfCode: 100,
    coverage: 90,
    description: '',
  };
}

describe('layoutGraph', () => {
  it('places every node exactly once', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    expect(layout.nodes.length).toBe(ATLAS_ARCHITECTURE.nodes.length);
    expect(new Set(layout.nodes.map((n) => n.node.id)).size).toBe(
      ATLAS_ARCHITECTURE.nodes.length,
    );
  });

  it('gives one column per architectural layer, ordered by dependency flow', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    expect(layout.columns.map((column) => column.layer)).toEqual([
      'presentation',
      'application',
      'domain',
      'infrastructure',
      'external',
    ]);

    for (let i = 1; i < layout.columns.length; i++) {
      expect(layout.columns[i].x).toBeGreaterThan(layout.columns[i - 1].x);
    }
  });

  it('puts every node of a layer at that layer’s column x', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    const columnX = new Map(layout.columns.map((c) => [c.layer, c.x]));

    for (const item of layout.nodes) {
      const expected = columnX.get(item.node.layer);
      expect(expected).toBeDefined();
      expect(item.x).toBe(expected as number);
    }
  });

  it('never overlaps two nodes in the same column', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    const byColumn = new Map<number, number[]>();
    for (const item of layout.nodes) {
      byColumn.set(item.x, [...(byColumn.get(item.x) ?? []), item.y]);
    }
    for (const ys of byColumn.values()) {
      expect(new Set(ys).size).toBe(ys.length);
    }
  });

  it('produces a path for every edge', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    expect(layout.edges.length).toBe(ATLAS_ARCHITECTURE.edges.length);
    for (const item of layout.edges) {
      expect(item.path.startsWith('M')).toBe(true);
      expect(item.path).not.toContain('NaN');
    }
  });

  it('routes a back edge around the outside instead of straight through', () => {
    // domain -> application runs against the layer flow.
    const graph = buildGraph(
      [spec('front', 'application'), spec('back', 'domain')],
      [edge('back', 'front')],
    );
    const layout = layoutGraph(graph);
    const path = layout.edges[0].path;

    // A back edge is drawn as two curves rather than one.
    expect(path.split('C').length - 1).toBe(2);
  });

  it('is deterministic across repeated runs', () => {
    const first = layoutGraph(ATLAS_ARCHITECTURE);
    const second = layoutGraph(ATLAS_ARCHITECTURE);
    expect(first.nodes.map((n) => `${n.node.id}:${n.x}:${n.y}`)).toEqual(
      second.nodes.map((n) => `${n.node.id}:${n.x}:${n.y}`),
    );
  });

  it('sizes the canvas to hold the widest column and every node', () => {
    const layout = layoutGraph(ATLAS_ARCHITECTURE);
    const maxX = Math.max(...layout.nodes.map((n) => n.x)) + NODE_WIDTH;
    const maxY = Math.max(...layout.nodes.map((n) => n.y));

    expect(layout.width).toBeGreaterThanOrEqual(maxX);
    expect(layout.height).toBeGreaterThan(maxY);
  });

  it('handles an empty graph without throwing', () => {
    const layout = layoutGraph({ nodes: [], edges: [], circularDependencies: [] });
    expect(layout.nodes).toEqual([]);
    expect(layout.edges).toEqual([]);
  });
});
