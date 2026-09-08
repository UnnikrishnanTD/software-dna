import { RepositoryAnalysis, RepositoryRef } from '../../models/analysis.model';
import { ATLAS_ARCHITECTURE } from './architecture.data';
import { ATLAS_CODEBASE } from './codebase.data';
import { ATLAS_DEPENDENCIES } from './dependencies.data';
import { ATLAS_DNA } from './dna.data';
import { ATLAS_EVOLUTION } from './evolution.data';
import { ATLAS_HOTSPOTS } from './hotspots.data';
import {
  ATLAS_DOCTOR_PROMPTS,
  ATLAS_INSIGHTS,
  ATLAS_REMEDIATION,
} from './insights.data';

const REPOSITORY: RepositoryRef = {
  owner: 'atlas-commerce',
  name: 'atlas-platform',
  displayName: 'Atlas Commerce Platform',
  url: 'https://github.com/atlas-commerce/atlas-platform',
  defaultBranch: 'main',
  description:
    'Commerce platform serving storefront, checkout and fulfilment across seven services.',
  stars: 4820,
  forks: 617,
  primaryLanguage: 'Java',
  createdAt: '2019-03-11',
  lastCommitAt: '2026-09-05',
};

export const ATLAS_ANALYSIS: RepositoryAnalysis = {
  id: 'atlas-platform',
  repository: REPOSITORY,
  generatedAt: '2026-09-07T09:24:00.000Z',
  stats: {
    files: 1284,
    linesOfCode: 90980,
    modules: 28,
    services: 11,
    components: 46,
    commits: 21470,
    contributors: 17,
    testCoverage: 71,
    analysisDurationMs: 47280,
  },
  dna: ATLAS_DNA,
  architecture: ATLAS_ARCHITECTURE,
  codebase: ATLAS_CODEBASE,
  hotspots: ATLAS_HOTSPOTS,
  dependencies: ATLAS_DEPENDENCIES,
  evolution: ATLAS_EVOLUTION,
  insights: ATLAS_INSIGHTS,
  remediation: ATLAS_REMEDIATION,
  doctorPrompts: ATLAS_DOCTOR_PROMPTS,
  incompleteDimensions: [],
};
