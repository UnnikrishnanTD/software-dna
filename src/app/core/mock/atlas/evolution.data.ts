import { DnaDimensionKey } from '../../models/dna.model';
import {
  EvolutionHistory,
  EvolutionMilestone,
  EvolutionPoint,
} from '../../models/evolution.model';

type Scores = Record<DnaDimensionKey, number>;

function scores(
  architecture: number,
  maintainability: number,
  security: number,
  performance: number,
  testing: number,
  dependencies: number,
  documentation: number,
  evolution: number,
): Scores {
  return {
    architecture,
    maintainability,
    security,
    performance,
    testing,
    dependencies,
    documentation,
    evolution,
  };
}

/**
 * Six years of Atlas. The shape matters: architecture and security climb
 * steadily, while testing peaks in 2024 and then slips as the codebase
 * outgrows its suite — which is exactly the weakness the DNA reports.
 */
const POINTS: readonly EvolutionPoint[] = [
  {
    year: 2019,
    scores: scores(58, 52, 49, 61, 38, 71, 34, 55),
    overall: 52,
    contributors: 2,
    commits: 840,
    linesOfCode: 12400,
    modules: 4,
    hotspots: 3,
    testCoverage: 31,
  },
  {
    year: 2020,
    scores: scores(62, 55, 55, 63, 44, 74, 40, 60),
    overall: 57,
    contributors: 3,
    commits: 2410,
    linesOfCode: 24800,
    modules: 7,
    hotspots: 6,
    testCoverage: 39,
  },
  {
    year: 2021,
    scores: scores(72, 61, 63, 66, 55, 78, 47, 68),
    overall: 65,
    contributors: 4,
    commits: 4620,
    linesOfCode: 38600,
    modules: 11,
    hotspots: 11,
    testCoverage: 48,
  },
  {
    year: 2022,
    scores: scores(79, 68, 71, 70, 63, 82, 55, 75),
    overall: 72,
    contributors: 7,
    commits: 7980,
    linesOfCode: 54200,
    modules: 16,
    hotspots: 14,
    testCoverage: 58,
  },
  {
    year: 2023,
    scores: scores(84, 73, 74, 74, 71, 84, 59, 80),
    overall: 77,
    contributors: 11,
    commits: 11840,
    linesOfCode: 68400,
    modules: 21,
    hotspots: 12,
    testCoverage: 68,
  },
  {
    year: 2024,
    scores: scores(87, 76, 76, 76, 72, 85, 62, 83),
    overall: 79,
    contributors: 13,
    commits: 15290,
    linesOfCode: 78900,
    modules: 24,
    hotspots: 12,
    testCoverage: 72,
  },
  {
    year: 2025,
    scores: scores(87, 77, 77, 77, 70, 86, 65, 85),
    overall: 80,
    contributors: 15,
    commits: 18640,
    linesOfCode: 85600,
    modules: 26,
    hotspots: 12,
    testCoverage: 72,
  },
  {
    year: 2026,
    scores: scores(91, 84, 88, 79, 67, 92, 74, 90),
    overall: 87,
    contributors: 17,
    commits: 21470,
    linesOfCode: 90980,
    modules: 28,
    hotspots: 11,
    testCoverage: 71,
  },
];

const MILESTONES: readonly EvolutionMilestone[] = [
  {
    id: 'm-2019-genesis',
    year: 2019,
    offset: 0.15,
    kind: 'architecture',
    title: 'Monolithic first release',
    description:
      'Atlas ships as a single Spring Boot application with a server-rendered storefront. Four modules, two contributors.',
    impact: 0,
  },
  {
    id: 'm-2020-angular',
    year: 2020,
    offset: 0.4,
    kind: 'technology',
    title: 'Angular storefront introduced',
    description:
      'The storefront moves to a single-page Angular client, separating presentation from the service layer for the first time.',
    impact: +5,
  },
  {
    id: 'm-2021-split',
    year: 2021,
    offset: 0.55,
    kind: 'architecture',
    title: 'Service extraction begins',
    description:
      'Catalog, pricing and identity are extracted into their own deployable services. Architecture climbs ten points in a single year.',
    impact: +10,
  },
  {
    id: 'm-2021-incident',
    year: 2021,
    offset: 0.85,
    kind: 'incident',
    title: 'Checkout outage',
    description:
      'A pricing regression took checkout down for 41 minutes. It triggered the investment in contract testing that followed.',
    impact: -3,
  },
  {
    id: 'm-2022-redis',
    year: 2022,
    offset: 0.3,
    kind: 'technology',
    title: 'Redis caching layer',
    description:
      'A read-through cache lands in front of the catalog, cutting median product page latency by 62%.',
    impact: +4,
  },
  {
    id: 'm-2022-tests',
    year: 2022,
    offset: 0.7,
    kind: 'quality',
    title: 'Test suite overhaul',
    description:
      'Testcontainers replaces the shared integration database. Coverage rises from 48% to 58% over two quarters.',
    impact: +8,
  },
  {
    id: 'm-2023-scale',
    year: 2023,
    offset: 0.35,
    kind: 'scale',
    title: 'Team doubles',
    description:
      'Contributor count moves from 7 to 11. Ownership begins to spread beyond the two original maintainers.',
    impact: +3,
  },
  {
    id: 'm-2024-userservice',
    year: 2024,
    offset: 0.5,
    kind: 'quality',
    title: 'UserService crosses 1,000 lines',
    description:
      'Loyalty enrolment lands in the identity service rather than its own context. UserService becomes the repository’s largest file.',
    impact: -4,
  },
  {
    id: 'm-2025-security',
    year: 2025,
    offset: 0.45,
    kind: 'quality',
    title: 'Hosted payment fields',
    description:
      'Card capture moves entirely to gateway-hosted fields, removing Atlas from the cardholder data path.',
    impact: +7,
  },
  {
    id: 'm-2026-modernisation',
    year: 2026,
    offset: 0.35,
    kind: 'technology',
    title: 'Platform modernisation',
    description:
      'Java 21, Spring Boot 3.3 and Angular 18 land together. Dependency health reaches its highest recorded level.',
    impact: +6,
  },
];

export const ATLAS_EVOLUTION: EvolutionHistory = {
  points: POINTS,
  milestones: MILESTONES,
};
