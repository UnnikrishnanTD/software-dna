import {
  ArchitectureEdge,
  ArchitectureGraph,
  ArchitectureLayer,
  ArchitectureNode,
} from '../../core/models/architecture.model';

export interface LaidOutNode {
  readonly node: ArchitectureNode;
  readonly x: number;
  readonly y: number;
}

export interface LaidOutEdge {
  readonly edge: ArchitectureEdge;
  /** Cubic bezier path from the source's right edge to the target's left. */
  readonly path: string;
}

export interface GraphLayout {
  readonly nodes: readonly LaidOutNode[];
  readonly edges: readonly LaidOutEdge[];
  readonly columns: readonly { layer: ArchitectureLayer; label: string; x: number }[];
  readonly width: number;
  readonly height: number;
}

export const NODE_WIDTH = 178;
export const NODE_HEIGHT = 30;

const COLUMN_GAP = 268;
const ROW_GAP = 42;
const MARGIN_X = 110;
const MARGIN_Y = 64;

/** Dependencies flow left to right, so layers are ordered by what they use. */
const LAYER_ORDER: readonly ArchitectureLayer[] = [
  'presentation',
  'application',
  'domain',
  'infrastructure',
  'external',
];

const LAYER_LABELS: Readonly<Record<ArchitectureLayer, string>> = {
  presentation: 'Presentation',
  application: 'Application',
  domain: 'Domain',
  infrastructure: 'Infrastructure',
  external: 'External',
};

/**
 * A layered layout rather than a force simulation.
 *
 * The data already carries the one thing a force layout would spend
 * hundreds of iterations trying to discover — which architectural layer
 * each unit belongs to. Laying the layers out as columns and ordering
 * within each column by barycentre gives a graph that is readable on first
 * paint, identical on every reload, and free of the drifting settle
 * animation that makes force graphs hard to read and hard to click.
 */
export function layoutGraph(graph: ArchitectureGraph): GraphLayout {
  const columns = new Map<ArchitectureLayer, ArchitectureNode[]>();
  for (const layer of LAYER_ORDER) columns.set(layer, []);
  for (const node of graph.nodes) columns.get(node.layer)?.push(node);

  const columnIndex = new Map<ArchitectureLayer, number>(
    LAYER_ORDER.map((layer, index) => [layer, index]),
  );

  // Seed each column alphabetically so the starting order is deterministic.
  for (const nodes of columns.values()) {
    nodes.sort((a, b) => a.name.localeCompare(b.name));
  }

  const order = new Map<string, number>();
  const setOrder = (): void => {
    for (const nodes of columns.values()) {
      nodes.forEach((node, index) => order.set(node.id, index));
    }
  };
  setOrder();

  const incoming = new Map<string, string[]>();
  const outgoing = new Map<string, string[]>();
  for (const node of graph.nodes) {
    incoming.set(node.id, []);
    outgoing.set(node.id, []);
  }
  for (const edge of graph.edges) {
    outgoing.get(edge.source)?.push(edge.target);
    incoming.get(edge.target)?.push(edge.source);
  }

  // Barycentre sweeps: repeatedly move each node next to the average
  // position of its neighbours. Four passes is enough to settle this graph
  // and keeps the layout cheap to recompute.
  for (let pass = 0; pass < 4; pass++) {
    const forward = pass % 2 === 0;
    for (const layer of forward ? LAYER_ORDER : [...LAYER_ORDER].reverse()) {
      const nodes = columns.get(layer);
      if (!nodes || nodes.length < 2) continue;

      const barycentre = new Map<string, number>();
      for (const node of nodes) {
        const neighbours = forward
          ? (incoming.get(node.id) ?? [])
          : (outgoing.get(node.id) ?? []);
        const positions = neighbours
          .map((id) => order.get(id))
          .filter((value): value is number => value !== undefined);
        barycentre.set(
          node.id,
          positions.length === 0
            ? (order.get(node.id) ?? 0)
            : positions.reduce((sum, value) => sum + value, 0) / positions.length,
        );
      }

      nodes.sort(
        (a, b) => (barycentre.get(a.id) ?? 0) - (barycentre.get(b.id) ?? 0),
      );
      setOrder();
    }
  }

  const tallest = Math.max(
    ...LAYER_ORDER.map((layer) => columns.get(layer)?.length ?? 0),
  );
  const height = MARGIN_Y * 2 + Math.max(tallest - 1, 0) * ROW_GAP + NODE_HEIGHT;
  const width = MARGIN_X * 2 + (LAYER_ORDER.length - 1) * COLUMN_GAP + NODE_WIDTH;

  const positions = new Map<string, { x: number; y: number }>();
  const laidOutNodes: LaidOutNode[] = [];

  for (const layer of LAYER_ORDER) {
    const nodes = columns.get(layer) ?? [];
    const x = MARGIN_X + (columnIndex.get(layer) ?? 0) * COLUMN_GAP;
    // Centre each column vertically against the tallest one.
    const columnHeight = Math.max(nodes.length - 1, 0) * ROW_GAP;
    const startY = (height - columnHeight - NODE_HEIGHT) / 2;

    nodes.forEach((node, index) => {
      const y = startY + index * ROW_GAP;
      positions.set(node.id, { x, y });
      laidOutNodes.push({ node, x, y });
    });
  }

  const laidOutEdges: LaidOutEdge[] = [];
  for (const edge of graph.edges) {
    const from = positions.get(edge.source);
    const to = positions.get(edge.target);
    if (!from || !to) continue;

    const x1 = from.x + NODE_WIDTH;
    const y1 = from.y + NODE_HEIGHT / 2;
    const x2 = to.x;
    const y2 = to.y + NODE_HEIGHT / 2;

    if (x2 >= x1) {
      const control = Math.max((x2 - x1) * 0.5, 40);
      laidOutEdges.push({
        edge,
        path: `M${x1} ${y1} C${x1 + control} ${y1}, ${x2 - control} ${y2}, ${x2} ${y2}`,
      });
    } else {
      // A back edge — a dependency pointing against the layer flow. Routed
      // around the outside so it is unmistakable rather than hidden.
      const sourceLeft = from.x;
      const targetRight = to.x + NODE_WIDTH;
      const sweep = Math.max(y1, y2) + 46;
      laidOutEdges.push({
        edge,
        path:
          `M${sourceLeft} ${y1} C${sourceLeft - 46} ${y1}, ` +
          `${sourceLeft - 46} ${sweep}, ${(sourceLeft + targetRight) / 2} ${sweep} ` +
          `C${targetRight + 46} ${sweep}, ${targetRight + 46} ${y2}, ${targetRight} ${y2}`,
      });
    }
  }

  return {
    nodes: laidOutNodes,
    edges: laidOutEdges,
    columns: LAYER_ORDER.map((layer) => ({
      layer,
      label: LAYER_LABELS[layer],
      x: MARGIN_X + (columnIndex.get(layer) ?? 0) * COLUMN_GAP,
    })),
    width,
    height,
  };
}
