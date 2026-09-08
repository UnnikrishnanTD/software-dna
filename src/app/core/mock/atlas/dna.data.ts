import { DnaDimension, DnaScore } from '../../models/dna.model';
import { verdictFor } from '../../util/health';

/**
 * Weights describe how much each dimension contributes to the roll-up in
 * the analysis engine's model. They are shown in the UI so a reader can see
 * why the headline number sits where it does.
 *
 * Note: `overall` is *not* recomputed on the client. In the real system it
 * is produced by the analysis engine and normalised against a reference
 * corpus, so the client renders the score it was given rather than
 * inventing its own. That keeps scoring logic in exactly one place when the
 * HTTP implementation lands.
 */
type AuthoredDimension = Omit<DnaDimension, 'verdict' | 'score'> & {
  readonly score: number;
};

const DIMENSIONS: readonly AuthoredDimension[] = [
  {
    key: 'architecture',
    label: 'Architecture',
    score: 91,
    weight: 0.18,
    delta: +4,
    headline: 'Strong modular structure with clear feature boundaries.',
    summary:
      'Atlas separates presentation, application and domain concerns cleanly, and the six bounded contexts map onto deployable services with little bleed between them. The two circular dependencies that exist are narrow and both involve UserService.',
    strengths: [
      'Feature modules are lazy-loaded and own their routes',
      'Domain services map one-to-one onto bounded contexts',
      'Transport is isolated behind a single typed ApiClient',
      'Persistence is reached only through repository adapters',
    ],
    watchItems: [
      'UserService and OrderService form a mutual dependency',
      'Three services carry a fan-out well above the codebase median',
      'AdminModule reaches the transport layer without a facade',
    ],
  },
  {
    key: 'maintainability',
    label: 'Maintainability',
    score: 84,
    weight: 0.16,
    delta: +7,
    headline: 'Readable and consistent, with complexity pooling in two files.',
    summary:
      'Median cyclomatic complexity across the codebase is 11, which is comfortable for a system of this size. The distribution has a long tail: four files exceed 20 and account for a disproportionate share of recent defect fixes.',
    strengths: [
      'Median file length is 284 lines',
      'Naming and layering conventions are applied consistently',
      'No file exceeds 1,300 lines',
    ],
    watchItems: [
      'UserService is 1,284 lines with a complexity of 34',
      'PromotionService stacking rules are deeply nested',
      'AnalyticsPanelComponent mixes data shaping with rendering',
    ],
  },
  {
    key: 'security',
    label: 'Security',
    score: 88,
    weight: 0.14,
    delta: +11,
    headline: 'Sound posture; one advisory outstanding in a transitive package.',
    summary:
      'Authentication is centralised, secrets are externalised, and payment data never touches Atlas servers thanks to hosted fields. One moderate advisory remains open against a transitive dependency of the Java stack.',
    strengths: [
      'Card data is handled entirely by hosted gateway fields',
      'Token refresh is centralised in a single interceptor',
      'No secrets committed anywhere in the analysed history',
      'Authorisation is enforced at the controller boundary',
    ],
    watchItems: [
      'One moderate advisory in a transitive Maven dependency',
      'Admin endpoints rely on a single role check',
    ],
  },
  {
    key: 'performance',
    label: 'Performance',
    score: 79,
    weight: 0.11,
    delta: +2,
    headline: 'Good client budgets; server-side N+1 risk in order retrieval.',
    summary:
      'The storefront bundle is well within budget and the catalog cache absorbs most read traffic. Order retrieval issues a query per line item under certain fulfilment states, which shows up as latency on large orders.',
    strengths: [
      'Initial storefront bundle is 214 kB compressed',
      'Catalog reads are served from Redis at a 94% hit rate',
      'Product grid is virtualised',
    ],
    watchItems: [
      'OrderRepository issues N+1 queries for split shipments',
      'AnalyticsPanelComponent renders without change-detection hints',
      'Admin bundle is 3.1× the storefront bundle',
    ],
  },
  {
    key: 'testing',
    label: 'Testing',
    score: 67,
    weight: 0.12,
    delta: -3,
    headline: 'Coverage is uneven — the riskiest code is the least tested.',
    summary:
      'Overall line coverage sits at 71%, but it is distributed inversely to risk. The infrastructure layer is covered above 90% while the two highest-risk domain services sit below 60%, which is the single clearest weakness in the profile.',
    strengths: [
      'Repository and transport layers exceed 90% coverage',
      'Checkout has end-to-end coverage across all four steps',
      'Test suite runs in 94 seconds',
    ],
    watchItems: [
      'UserService is 44% covered despite being the highest-risk unit',
      'OrderService is 58% covered',
      'AnalyticsPanelComponent is 38% covered',
      'No contract tests between services',
    ],
  },
  {
    key: 'dependencies',
    label: 'Dependencies',
    score: 92,
    weight: 0.11,
    delta: +6,
    headline: 'Lean, current and concentrated on well-maintained packages.',
    summary:
      'Atlas depends on 184 packages across npm and Maven, of which 61 are direct. Nothing is more than one major version behind, and the dependency surface is concentrated in a small number of actively maintained ecosystems.',
    strengths: [
      'No dependency more than one major version behind',
      'No deprecated direct dependencies',
      'Every direct dependency carries a permissive licence',
    ],
    watchItems: [
      'Nine packages have a minor update available',
      'One transitive package carries an open advisory',
    ],
  },
  {
    key: 'documentation',
    label: 'Documentation',
    score: 74,
    weight: 0.08,
    delta: +9,
    headline: 'Good onboarding material; API and decision records lag behind.',
    summary:
      'The repository has a genuinely useful README and a working local setup guide. What is missing is the layer above that: the reasoning behind the service split is not written down anywhere, and API docs have drifted from the implementation.',
    strengths: [
      'README covers setup, architecture and deployment',
      'Every service documents its environment contract',
      'Public TypeScript interfaces carry doc comments',
    ],
    watchItems: [
      'No architecture decision records',
      'OpenAPI spec is four endpoints behind the implementation',
      'Java domain services average 18% comment density',
    ],
  },
  {
    key: 'evolution',
    label: 'Evolution',
    score: 90,
    weight: 0.10,
    delta: +5,
    headline: 'Healthy, sustained growth with steadily broadening ownership.',
    summary:
      'Atlas has grown steadily for six years without the complexity spikes that usually accompany that growth. Contributor count has more than quadrupled since 2021 and knowledge is no longer concentrated in one or two people.',
    strengths: [
      'Contributors grew from 4 to 17 since 2021',
      'Complexity per module has fallen for three straight years',
      'No single contributor owns more than 22% of the codebase',
    ],
    watchItems: [
      'Hotspot count has been flat since 2023',
      'UserService has been the most-changed file for four years running',
    ],
  },
];

export const ATLAS_DNA: DnaScore = {
  overall: 87,
  verdict: verdictFor(87),
  percentile: 94,
  // The sample data is complete, so nothing was scored from partial evidence.
  confidence: 100,
  dimensions: DIMENSIONS.map((dimension) => ({
    ...dimension,
    verdict: verdictFor(dimension.score),
  })),
};
