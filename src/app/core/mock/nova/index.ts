import { RepositoryAnalysis, RepositoryRef } from '../../models/analysis.model';
import { DnaDimension, DnaScore } from '../../models/dna.model';
import { FileNode } from '../../models/codebase.model';
import { Hotspot } from '../../models/hotspot.model';
import { DependencyProfile } from '../../models/dependency.model';
import { EvolutionHistory } from '../../models/evolution.model';
import { verdictFor } from '../../util/health';
import { buildGraph, edge, ArchitectureNodeSpec } from '../graph-builder';

/**
 * Nova Platform — a younger, smaller retail system built on Next.js and Go.
 * It exists primarily as the comparison subject: stronger on testing,
 * security and performance, weaker on maintainability and evolution. The
 * contrast is what makes the Compare screen say something.
 */

const REPOSITORY: RepositoryRef = {
  owner: 'nova-retail',
  name: 'nova-platform',
  displayName: 'Nova Platform',
  url: 'https://github.com/nova-retail/nova-platform',
  defaultBranch: 'main',
  description:
    'Retail ordering and fulfilment platform built on Next.js and Go services.',
  stars: 1930,
  forks: 208,
  primaryLanguage: 'Go',
  createdAt: '2022-07-19',
  lastCommitAt: '2026-09-04',
};

type AuthoredDimension = Omit<DnaDimension, 'verdict' | 'score'> & {
  readonly score: number;
};

const DIMENSIONS: readonly AuthoredDimension[] = [
  {
    key: 'architecture',
    label: 'Architecture',
    score: 84,
    weight: 0.18,
    delta: +3,
    headline: 'Clean service boundaries, with a shared package that pulls too much weight.',
    summary:
      'Nova has a flat, comprehensible service topology and no circular dependencies. A single shared internal package is imported by every service, which centralises change risk.',
    strengths: ['No circular dependencies', 'Flat service topology', 'Explicit API contracts between services'],
    watchItems: ['One shared package is imported by all eleven services', 'Gateway owns both routing and authorisation'],
  },
  {
    key: 'maintainability',
    label: 'Maintainability',
    score: 76,
    weight: 0.16,
    delta: -2,
    headline: 'Rapid growth has outpaced refactoring.',
    summary:
      'Nova has added features faster than it has consolidated them. Duplication between the ordering and fulfilment services accounts for most of the deficit.',
    strengths: ['Consistent Go idioms across services', 'Small median file size'],
    watchItems: ['Order and fulfilment share duplicated domain logic', 'Nine files exceed a complexity of 20', 'Gateway routing table is hand-maintained'],
  },
  {
    key: 'security',
    label: 'Security',
    score: 93,
    weight: 0.14,
    delta: +6,
    headline: 'The strongest security posture in the corpus.',
    summary:
      'Nova enforces mutual TLS between services, scans dependencies on every build and has no outstanding advisories. Authorisation is policy-driven rather than role-checked in code.',
    strengths: ['Mutual TLS between all services', 'Zero outstanding advisories', 'Policy-based authorisation', 'Secrets sourced from a managed vault'],
    watchItems: ['Gateway is a single authorisation choke point'],
  },
  {
    key: 'performance',
    label: 'Performance',
    score: 87,
    weight: 0.11,
    delta: +4,
    headline: 'Fast by construction, with headroom in the read path.',
    summary:
      'Go services keep p99 latency under 120 ms and the Next.js frontend ships a small initial bundle. Read-heavy endpoints are not yet cached.',
    strengths: ['p99 under 120 ms across services', 'Initial bundle of 148 kB compressed', 'Streaming server rendering'],
    watchItems: ['No caching layer on catalog reads', 'Search fans out to three services per query'],
  },
  {
    key: 'testing',
    label: 'Testing',
    score: 81,
    weight: 0.12,
    delta: +5,
    headline: 'Coverage is high and, unusually, evenly distributed.',
    summary:
      'Nova holds 84% coverage with no service below 74%. Contract tests run between every service pair, which is the practice Atlas most visibly lacks.',
    strengths: ['84% overall coverage', 'No service below 74%', 'Contract tests between every service pair'],
    watchItems: ['Frontend end-to-end coverage is thinner than the backend'],
  },
  {
    key: 'dependencies',
    label: 'Dependencies',
    score: 78,
    weight: 0.11,
    delta: -4,
    headline: 'Broad surface with several packages drifting behind.',
    summary:
      'Nova carries a wider dependency surface than its size suggests, and fourteen packages are a major version behind. Nothing is deprecated or vulnerable.',
    strengths: ['No deprecated dependencies', 'No open advisories'],
    watchItems: ['Fourteen packages a major version behind', 'Two overlapping HTTP client libraries'],
  },
  {
    key: 'documentation',
    label: 'Documentation',
    score: 88,
    weight: 0.08,
    delta: +7,
    headline: 'Well documented, including the decisions.',
    summary:
      'Nova maintains decision records alongside generated API documentation, and every service documents its contract. This is its clearest advantage over Atlas.',
    strengths: ['22 architecture decision records', 'Generated, versioned API documentation', 'Every service documents its contract'],
    watchItems: ['Frontend component documentation is sparse'],
  },
  {
    key: 'evolution',
    label: 'Evolution',
    score: 72,
    weight: 0.1,
    delta: +2,
    headline: 'Young, fast-moving and concentrated in a few hands.',
    summary:
      'Four years of history with a small core team. Two contributors account for more than half of all changes, which is the main structural risk in Nova’s profile.',
    strengths: ['Steady delivery cadence', 'Complexity has stayed flat as features grew'],
    watchItems: ['Two contributors own 54% of the codebase', 'Only four years of history to reason from'],
  },
];

const NOVA_DNA: DnaScore = {
  overall: 83,
  verdict: verdictFor(83),
  percentile: 88,
  confidence: 100,
  dimensions: DIMENSIONS.map((d) => ({ ...d, verdict: verdictFor(d.score) })),
};

const NODES: readonly ArchitectureNodeSpec[] = [
  { id: 'nova-web', name: 'NovaWeb', kind: 'app', layer: 'presentation', path: 'apps/web', language: 'TypeScript', complexityScore: 8, linesOfCode: 428, coverage: 76, description: 'Next.js storefront with streaming server rendering.' },
  { id: 'nova-admin', name: 'NovaAdmin', kind: 'app', layer: 'presentation', path: 'apps/admin', language: 'TypeScript', complexityScore: 11, linesOfCode: 512, coverage: 71, description: 'Internal operations console.' },
  { id: 'nova-storefront', name: 'StorefrontUI', kind: 'module', layer: 'presentation', path: 'apps/web/src/storefront', language: 'TypeScript', complexityScore: 9, linesOfCode: 386, coverage: 78, description: 'Catalog browsing and product pages.' },
  { id: 'nova-basket-ui', name: 'BasketUI', kind: 'module', layer: 'presentation', path: 'apps/web/src/basket', language: 'TypeScript', complexityScore: 12, linesOfCode: 344, coverage: 74, description: 'Basket and checkout flow.' },
  { id: 'nova-gateway', name: 'APIGateway', kind: 'api', layer: 'application', path: 'services/gateway', language: 'Go', complexityScore: 19, linesOfCode: 684, coverage: 88, description: 'Edge routing, authentication and policy enforcement.' },
  { id: 'nova-shared', name: 'SharedKernel', kind: 'module', layer: 'application', path: 'internal/shared', language: 'Go', complexityScore: 14, linesOfCode: 592, coverage: 91, description: 'Shared types, errors and telemetry. Imported by every service.' },
  { id: 'nova-order', name: 'OrderSvc', kind: 'service', layer: 'domain', path: 'services/order', language: 'Go', complexityScore: 21, linesOfCode: 812, coverage: 82, description: 'Order lifecycle and state machine.' },
  { id: 'nova-fulfilment', name: 'FulfilmentSvc', kind: 'service', layer: 'domain', path: 'services/fulfilment', language: 'Go', complexityScore: 22, linesOfCode: 774, coverage: 79, description: 'Picking, packing and dispatch. Shares logic with OrderSvc.' },
  { id: 'nova-catalog', name: 'CatalogSvc', kind: 'service', layer: 'domain', path: 'services/catalog', language: 'Go', complexityScore: 13, linesOfCode: 546, coverage: 89, description: 'Product and category read model.' },
  { id: 'nova-identity', name: 'IdentitySvc', kind: 'service', layer: 'domain', path: 'services/identity', language: 'Go', complexityScore: 12, linesOfCode: 468, coverage: 90, description: 'Accounts, sessions and policy evaluation.' },
  { id: 'nova-payment', name: 'PaymentSvc', kind: 'service', layer: 'domain', path: 'services/payment', language: 'Go', complexityScore: 16, linesOfCode: 602, coverage: 86, description: 'Authorisation, capture and refunds.' },
  { id: 'nova-search', name: 'SearchSvc', kind: 'service', layer: 'domain', path: 'services/search', language: 'Go', complexityScore: 15, linesOfCode: 524, coverage: 81, description: 'Query parsing and relevance ranking.' },
  { id: 'nova-notify', name: 'NotifySvc', kind: 'service', layer: 'domain', path: 'services/notify', language: 'Go', complexityScore: 8, linesOfCode: 286, coverage: 87, description: 'Email, SMS and webhook dispatch.' },
  { id: 'nova-store', name: 'MongoDB', kind: 'datastore', layer: 'infrastructure', path: 'infra/mongo', language: 'Config', complexityScore: 3, linesOfCode: 214, coverage: 100, description: 'Primary document store, one database per service.' },
  { id: 'nova-queue', name: 'NATS', kind: 'datastore', layer: 'infrastructure', path: 'infra/nats', language: 'Config', complexityScore: 3, linesOfCode: 128, coverage: 100, description: 'Event backbone between services.' },
  { id: 'nova-payments-ext', name: 'PaymentGateway', kind: 'external', layer: 'external', path: 'external://payments', language: 'HTTP', complexityScore: 2, linesOfCode: 0, coverage: 100, description: 'Third-party payment processing.' },
  { id: 'nova-carrier-ext', name: 'CarrierAPI', kind: 'external', layer: 'external', path: 'external://carriers', language: 'HTTP', complexityScore: 2, linesOfCode: 0, coverage: 100, description: 'Carrier rating and labels.' },
];

const NOVA_ARCHITECTURE = buildGraph(NODES, [
  edge('nova-web', 'nova-storefront', 'imports', 5),
  edge('nova-web', 'nova-basket-ui', 'imports', 5),
  edge('nova-web', 'nova-gateway', 'calls', 8),
  edge('nova-admin', 'nova-gateway', 'calls', 7),
  edge('nova-storefront', 'nova-gateway', 'calls', 6),
  edge('nova-basket-ui', 'nova-gateway', 'calls', 7),
  edge('nova-gateway', 'nova-identity', 'calls', 9),
  edge('nova-gateway', 'nova-order', 'calls', 8),
  edge('nova-gateway', 'nova-catalog', 'calls', 8),
  edge('nova-gateway', 'nova-search', 'calls', 7),
  edge('nova-gateway', 'nova-payment', 'calls', 7),
  edge('nova-gateway', 'nova-shared', 'imports', 6),
  edge('nova-order', 'nova-shared', 'imports', 6),
  edge('nova-fulfilment', 'nova-shared', 'imports', 6),
  edge('nova-catalog', 'nova-shared', 'imports', 6),
  edge('nova-identity', 'nova-shared', 'imports', 6),
  edge('nova-payment', 'nova-shared', 'imports', 6),
  edge('nova-search', 'nova-shared', 'imports', 6),
  edge('nova-notify', 'nova-shared', 'imports', 6),
  edge('nova-order', 'nova-queue', 'calls', 7),
  edge('nova-order', 'nova-store', 'reads', 9),
  edge('nova-fulfilment', 'nova-queue', 'calls', 7),
  edge('nova-fulfilment', 'nova-store', 'reads', 8),
  edge('nova-fulfilment', 'nova-carrier-ext', 'calls', 7),
  edge('nova-catalog', 'nova-store', 'reads', 8),
  edge('nova-identity', 'nova-store', 'reads', 8),
  edge('nova-payment', 'nova-payments-ext', 'calls', 8),
  edge('nova-payment', 'nova-store', 'reads', 7),
  edge('nova-search', 'nova-store', 'reads', 7),
  edge('nova-notify', 'nova-queue', 'calls', 7),
]);

const NOVA_HOTSPOTS: readonly Hotspot[] = [
  { id: 'nhs-fulfilment', name: 'fulfilment/dispatch.go', path: 'services/fulfilment/dispatch.go', changes: 88, complexityScore: 22, complexity: 'high', dependencies: 12, bugFixes: 5, linesOfCode: 774, coverage: 79, contributors: 4, riskScore: 58, severity: 'high', architectureNodeId: 'nova-fulfilment', rationale: 'Dispatch logic duplicates order state handling, so the same rule change lands in two places.', recommendation: 'Consolidate the shared state machine into the shared kernel.' },
  { id: 'nhs-order', name: 'order/state.go', path: 'services/order/state.go', changes: 76, complexityScore: 21, complexity: 'high', dependencies: 10, bugFixes: 4, linesOfCode: 812, coverage: 82, contributors: 4, riskScore: 52, severity: 'medium', architectureNodeId: 'nova-order', rationale: 'The order state machine changes with every new fulfilment mode.', recommendation: 'Extract transitions into a declarative table.' },
  { id: 'nhs-gateway', name: 'gateway/router.go', path: 'services/gateway/router.go', changes: 64, complexityScore: 19, complexity: 'high', dependencies: 14, bugFixes: 3, linesOfCode: 684, coverage: 88, contributors: 3, riskScore: 47, severity: 'medium', architectureNodeId: 'nova-gateway', rationale: 'The routing table is hand-maintained and every new endpoint touches it.', recommendation: 'Generate routes from the service contracts.' },
  { id: 'nhs-shared', name: 'internal/shared/types.go', path: 'internal/shared/types.go', changes: 58, complexityScore: 14, complexity: 'medium', dependencies: 4, bugFixes: 2, linesOfCode: 592, coverage: 91, contributors: 6, riskScore: 38, severity: 'medium', architectureNodeId: 'nova-shared', rationale: 'Imported by all eleven services, so any change has platform-wide reach.', recommendation: 'Split into per-concern packages to narrow the blast radius.' },
  { id: 'nhs-search', name: 'search/rank.go', path: 'services/search/rank.go', changes: 41, complexityScore: 15, complexity: 'medium', dependencies: 6, bugFixes: 2, linesOfCode: 524, coverage: 81, contributors: 3, riskScore: 33, severity: 'low', architectureNodeId: 'nova-search', rationale: 'Relevance tuning drives churn; structure is stable.', recommendation: 'No action needed.' },
  { id: 'nhs-payment', name: 'payment/capture.go', path: 'services/payment/capture.go', changes: 29, complexityScore: 16, complexity: 'high', dependencies: 7, bugFixes: 1, linesOfCode: 602, coverage: 86, contributors: 3, riskScore: 30, severity: 'low', architectureNodeId: 'nova-payment', rationale: 'Complex but stable and well covered.', recommendation: 'No action needed.' },
  { id: 'nhs-catalog', name: 'catalog/query.go', path: 'services/catalog/query.go', changes: 34, complexityScore: 13, complexity: 'medium', dependencies: 5, bugFixes: 1, linesOfCode: 546, coverage: 89, contributors: 4, riskScore: 26, severity: 'low', architectureNodeId: 'nova-catalog', rationale: 'Ordinary read-model churn with strong coverage.', recommendation: 'No action needed.' },
  { id: 'nhs-identity', name: 'identity/policy.go', path: 'services/identity/policy.go', changes: 22, complexityScore: 12, complexity: 'medium', dependencies: 4, bugFixes: 1, linesOfCode: 468, coverage: 90, contributors: 3, riskScore: 22, severity: 'low', architectureNodeId: 'nova-identity', rationale: 'Policy rules are declarative and well covered.', recommendation: 'No action needed.' },
];

const NOVA_DEPENDENCIES: DependencyProfile = {
  total: 246,
  direct: 74,
  transitive: 172,
  outdated: 31,
  deprecated: 0,
  advisories: 0,
  dependencies: [
    { id: 'nd-next', name: 'next', ecosystem: 'npm', version: '14.2.7', latestVersion: '15.0.1', status: 'major-behind', license: 'MIT', direct: true, usedBy: 2, sizeKb: 1840, risk: 'medium', advisories: 0 },
    { id: 'nd-react', name: 'react', ecosystem: 'npm', version: '18.3.1', latestVersion: '19.0.0', status: 'major-behind', license: 'MIT', direct: true, usedBy: 2, sizeKb: 142, risk: 'medium', advisories: 0 },
    { id: 'nd-tanstack', name: '@tanstack/react-query', ecosystem: 'npm', version: '5.56.2', latestVersion: '5.59.0', status: 'minor-behind', license: 'MIT', direct: true, usedBy: 2, sizeKb: 96, risk: 'low', advisories: 0 },
    { id: 'nd-gin', name: 'github.com/gin-gonic/gin', ecosystem: 'system', version: '1.10.0', latestVersion: '1.10.0', status: 'current', license: 'MIT', direct: true, usedBy: 8, sizeKb: 412, risk: 'low', advisories: 0 },
    { id: 'nd-mongo', name: 'go.mongodb.org/mongo-driver', ecosystem: 'system', version: '1.17.0', latestVersion: '1.17.1', status: 'minor-behind', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 1240, risk: 'low', advisories: 0 },
    { id: 'nd-nats', name: 'github.com/nats-io/nats.go', ecosystem: 'system', version: '1.37.0', latestVersion: '1.37.0', status: 'current', license: 'Apache-2.0', direct: true, usedBy: 4, sizeKb: 386, risk: 'low', advisories: 0 },
    { id: 'nd-otel', name: 'go.opentelemetry.io/otel', ecosystem: 'system', version: '1.30.0', latestVersion: '1.30.0', status: 'current', license: 'Apache-2.0', direct: true, usedBy: 11, sizeKb: 528, risk: 'low', advisories: 0 },
    { id: 'nd-resty', name: 'github.com/go-resty/resty', ecosystem: 'system', version: '2.14.0', latestVersion: '2.15.3', status: 'minor-behind', license: 'MIT', direct: true, usedBy: 3, sizeKb: 184, risk: 'low', advisories: 0, note: 'Overlaps with the standard library HTTP client used elsewhere' },
    { id: 'nd-docker-go', name: 'golang:1.23-alpine', ecosystem: 'docker', version: '1.23.1', latestVersion: '1.23.1', status: 'current', license: 'BSD-3-Clause', direct: true, usedBy: 11, sizeKb: 0, risk: 'low', advisories: 0 },
    { id: 'nd-docker-mongo', name: 'mongo:7', ecosystem: 'docker', version: '7.0.14', latestVersion: '7.0.14', status: 'current', license: 'SSPL-1.0', direct: true, usedBy: 1, sizeKb: 0, risk: 'low', advisories: 0 },
  ],
  technologies: [
    { id: 'nt-go', name: 'Go', category: 'backend', share: 46, linesOfCode: 24380, version: '1.23', dependencyCount: 38 },
    { id: 'nt-ts', name: 'TypeScript', category: 'language', share: 24, linesOfCode: 12720, version: '5.6', dependencyCount: 18 },
    { id: 'nt-next', name: 'Next.js', category: 'frontend', share: 14, linesOfCode: 7420, version: '14.2', dependencyCount: 12 },
    { id: 'nt-mongo', name: 'MongoDB', category: 'datastore', share: 7, linesOfCode: 3710, version: '7.0', dependencyCount: 3 },
    { id: 'nt-nats', name: 'NATS', category: 'infrastructure', share: 5, linesOfCode: 2650, version: '2.10', dependencyCount: 2 },
    { id: 'nt-docker', name: 'Docker', category: 'infrastructure', share: 4, linesOfCode: 2120, version: '27.2', dependencyCount: 3 },
  ],
};

const NOVA_EVOLUTION: EvolutionHistory = {
  points: [
    { year: 2022, scores: { architecture: 64, maintainability: 71, security: 68, performance: 74, testing: 58, dependencies: 88, documentation: 61, evolution: 52 }, overall: 67, contributors: 2, commits: 1240, linesOfCode: 14200, modules: 5, hotspots: 4, testCoverage: 54 },
    { year: 2023, scores: { architecture: 73, maintainability: 78, security: 79, performance: 79, testing: 69, dependencies: 86, documentation: 72, evolution: 61 }, overall: 75, contributors: 4, commits: 3980, linesOfCode: 26400, modules: 9, hotspots: 7, testCoverage: 68 },
    { year: 2024, scores: { architecture: 79, maintainability: 79, security: 85, performance: 83, testing: 76, dependencies: 84, documentation: 79, evolution: 66 }, overall: 79, contributors: 5, commits: 6720, linesOfCode: 38900, modules: 13, hotspots: 8, testCoverage: 77 },
    { year: 2025, scores: { architecture: 81, maintainability: 78, security: 87, performance: 83, testing: 76, dependencies: 82, documentation: 81, evolution: 70 }, overall: 80, contributors: 6, commits: 9140, linesOfCode: 46800, modules: 15, hotspots: 8, testCoverage: 80 },
    { year: 2026, scores: { architecture: 84, maintainability: 76, security: 93, performance: 87, testing: 81, dependencies: 78, documentation: 88, evolution: 72 }, overall: 83, contributors: 7, commits: 11860, linesOfCode: 53000, modules: 17, hotspots: 8, testCoverage: 84 },
  ],
  milestones: [
    { id: 'nm-2022', year: 2022, offset: 0.2, kind: 'architecture', title: 'Nova begins', description: 'Two engineers ship the first Go services behind a Next.js storefront.', impact: 0 },
    { id: 'nm-2023', year: 2023, offset: 0.5, kind: 'quality', title: 'Contract testing adopted', description: 'Contract tests land between every service pair, lifting testing eleven points.', impact: +11 },
    { id: 'nm-2024', year: 2024, offset: 0.4, kind: 'technology', title: 'NATS event backbone', description: 'Synchronous service calls give way to an event backbone for fulfilment.', impact: +6 },
    { id: 'nm-2025', year: 2025, offset: 0.6, kind: 'quality', title: 'Mutual TLS everywhere', description: 'All inter-service traffic moves to mutual TLS with policy-based authorisation.', impact: +8 },
    { id: 'nm-2026', year: 2026, offset: 0.3, kind: 'scale', title: 'Duplication surfaces', description: 'Order and fulfilment logic diverge, and maintainability slips for the first time.', impact: -3 },
  ],
};

const NOVA_CODEBASE: FileNode = {
  id: 'nova-root',
  name: 'nova-platform',
  path: '',
  type: 'directory',
  health: 83,
  children: [
    {
      id: 'nova-apps', name: 'apps', path: 'apps', type: 'directory', health: 79,
      children: [
        { id: 'nova-f1', name: 'page.tsx', path: 'apps/web/src/storefront/page.tsx', type: 'file', language: 'TypeScript', health: 80, architectureNodeId: 'nova-storefront', metrics: { linesOfCode: 386, complexity: 'medium', complexityScore: 9, dependencies: 6, dependents: 1, changes: 31, coverage: 78, risk: 'medium', lastChanged: '2026-08-22', primaryAuthor: 'a.lindqvist' } },
        { id: 'nova-f2', name: 'basket.tsx', path: 'apps/web/src/basket/basket.tsx', type: 'file', language: 'TypeScript', health: 76, architectureNodeId: 'nova-basket-ui', metrics: { linesOfCode: 344, complexity: 'medium', complexityScore: 12, dependencies: 7, dependents: 1, changes: 38, coverage: 74, risk: 'medium', lastChanged: '2026-09-01', primaryAuthor: 'a.lindqvist' } },
      ],
    },
    {
      id: 'nova-services', name: 'services', path: 'services', type: 'directory', health: 84,
      children: [
        { id: 'nova-f3', name: 'dispatch.go', path: 'services/fulfilment/dispatch.go', type: 'file', language: 'Go', health: 71, architectureNodeId: 'nova-fulfilment', metrics: { linesOfCode: 774, complexity: 'high', complexityScore: 22, dependencies: 12, dependents: 2, changes: 88, coverage: 79, risk: 'high', lastChanged: '2026-09-03', primaryAuthor: 's.varga' } },
        { id: 'nova-f4', name: 'state.go', path: 'services/order/state.go', type: 'file', language: 'Go', health: 76, architectureNodeId: 'nova-order', metrics: { linesOfCode: 812, complexity: 'high', complexityScore: 21, dependencies: 10, dependents: 3, changes: 76, coverage: 82, risk: 'medium', lastChanged: '2026-09-02', primaryAuthor: 's.varga' } },
        { id: 'nova-f5', name: 'router.go', path: 'services/gateway/router.go', type: 'file', language: 'Go', health: 82, architectureNodeId: 'nova-gateway', metrics: { linesOfCode: 684, complexity: 'high', complexityScore: 19, dependencies: 14, dependents: 0, changes: 64, coverage: 88, risk: 'medium', lastChanged: '2026-08-29', primaryAuthor: 'k.brennan' } },
        { id: 'nova-f6', name: 'policy.go', path: 'services/identity/policy.go', type: 'file', language: 'Go', health: 92, architectureNodeId: 'nova-identity', metrics: { linesOfCode: 468, complexity: 'medium', complexityScore: 12, dependencies: 4, dependents: 2, changes: 22, coverage: 90, risk: 'low', lastChanged: '2026-07-30', primaryAuthor: 'k.brennan' } },
      ],
    },
    {
      id: 'nova-internal', name: 'internal', path: 'internal', type: 'directory', health: 88,
      children: [
        { id: 'nova-f7', name: 'types.go', path: 'internal/shared/types.go', type: 'file', language: 'Go', health: 88, architectureNodeId: 'nova-shared', metrics: { linesOfCode: 592, complexity: 'medium', complexityScore: 14, dependencies: 4, dependents: 11, changes: 58, coverage: 91, risk: 'medium', lastChanged: '2026-08-26', primaryAuthor: 'k.brennan' } },
      ],
    },
  ],
};

export const NOVA_ANALYSIS: RepositoryAnalysis = {
  id: 'nova-platform',
  repository: REPOSITORY,
  generatedAt: '2026-09-06T15:12:00.000Z',
  stats: {
    files: 642,
    linesOfCode: 53000,
    modules: 17,
    services: 8,
    components: 24,
    commits: 11860,
    contributors: 7,
    testCoverage: 84,
    analysisDurationMs: 31640,
  },
  dna: NOVA_DNA,
  architecture: NOVA_ARCHITECTURE,
  codebase: NOVA_CODEBASE,
  hotspots: NOVA_HOTSPOTS,
  dependencies: NOVA_DEPENDENCIES,
  evolution: NOVA_EVOLUTION,
  insights: [
    { id: 'ni-duplication', kind: 'risk', severity: 'high', title: 'Order and fulfilment logic have diverged', body: 'The two services maintain parallel copies of the same state transitions. Every rule change must land twice, and one of the two is usually missed.', dimension: 'maintainability', relatedNodeIds: ['nova-order', 'nova-fulfilment'], confidence: 0.92 },
    { id: 'ni-shared', kind: 'risk', severity: 'medium', title: 'SharedKernel is imported by every service', body: 'A single package is imported by all eleven services, so any change to it has platform-wide reach.', dimension: 'architecture', relatedNodeIds: ['nova-shared'], confidence: 0.89 },
    { id: 'ni-security', kind: 'strength', severity: 'low', title: 'Security posture is exemplary', body: 'Mutual TLS between all services, policy-based authorisation and zero outstanding advisories place Nova in the top percentile of the corpus.', dimension: 'security', relatedNodeIds: ['nova-gateway', 'nova-identity'], confidence: 0.97 },
    { id: 'ni-bus', kind: 'risk', severity: 'medium', title: 'Ownership is concentrated', body: 'Two contributors account for 54% of all changes. For a team of seven, that is a meaningful continuity risk.', dimension: 'evolution', relatedNodeIds: [], confidence: 0.9 },
  ],
  remediation: [
    { rank: 1, title: 'Consolidate the order state machine', detail: 'Move the shared transitions into the shared kernel so a rule change lands once.', effort: 'medium', expectedGain: 7, dimension: 'maintainability' },
    { rank: 2, title: 'Split SharedKernel by concern', detail: 'Break the shared package into per-concern modules to narrow the blast radius of a change.', effort: 'medium', expectedGain: 5, dimension: 'architecture' },
    { rank: 3, title: 'Spread ownership', detail: 'Rotate service ownership so no two contributors hold the majority of the history.', effort: 'low', expectedGain: 6, dimension: 'evolution' },
    { rank: 4, title: 'Cache the catalog read path', detail: 'Read-heavy catalog endpoints have no cache. The headroom is the largest available performance win.', effort: 'low', expectedGain: 4, dimension: 'performance' },
  ],
  doctorPrompts: [
    { id: 'np-1', question: 'Why is maintainability falling?', category: 'diagnose' },
    { id: 'np-2', question: 'What should I fix first?', category: 'prioritise' },
    { id: 'np-3', question: 'Which files are the biggest risk?', category: 'diagnose' },
    { id: 'np-4', question: 'Explain this dependency graph.', category: 'explain' },
    { id: 'np-5', question: 'What changed over the last year?', category: 'history' },
  ],
  incompleteDimensions: [],
};
