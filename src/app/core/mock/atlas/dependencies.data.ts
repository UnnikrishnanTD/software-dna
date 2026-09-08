import { RiskLevel } from '../../models/architecture.model';
import {
  Dependency,
  DependencyProfile,
  DependencyStatus,
  Technology,
} from '../../models/dependency.model';

/**
   * The mock authors every field, including the ones the real backend leaves
   * null when it has not checked. Narrowing them here keeps the sample data
   * honest about being complete, without weakening the shared model.
   */
type DependencySpec = Omit<Dependency, 'status' | 'risk' | 'latestVersion' | 'advisories'> & {
  readonly latestVersion: string;
  readonly advisories: number;
};

/** Version distance drives status; status plus advisories drive risk. */
function statusFor(spec: DependencySpec): DependencyStatus {
  if (spec.note?.includes('deprecated')) return 'deprecated';
  const [major, minor] = spec.version.split('.').map(Number);
  const [latestMajor, latestMinor] = spec.latestVersion.split('.').map(Number);
  if (latestMajor > major) return 'major-behind';
  if (latestMinor > minor) return 'minor-behind';
  return 'current';
}

function riskFor(spec: DependencySpec, status: DependencyStatus): RiskLevel {
  if (spec.advisories > 0 && status === 'major-behind') return 'critical';
  if (spec.advisories > 0) return 'high';
  if (status === 'deprecated') return 'high';
  if (status === 'major-behind') return 'medium';
  return 'low';
}

const SPECS: readonly DependencySpec[] = [
  // ---- npm / frontend ----------------------------------------------------
  { id: 'd-angular-core', name: '@angular/core', ecosystem: 'npm', version: '18.2.0', latestVersion: '18.2.0', license: 'MIT', direct: true, usedBy: 46, sizeKb: 312, advisories: 0 },
  { id: 'd-angular-router', name: '@angular/router', ecosystem: 'npm', version: '18.2.0', latestVersion: '18.2.0', license: 'MIT', direct: true, usedBy: 18, sizeKb: 128, advisories: 0 },
  { id: 'd-angular-forms', name: '@angular/forms', ecosystem: 'npm', version: '18.2.0', latestVersion: '18.2.0', license: 'MIT', direct: true, usedBy: 14, sizeKb: 96, advisories: 0 },
  { id: 'd-angular-cdk', name: '@angular/cdk', ecosystem: 'npm', version: '18.1.0', latestVersion: '18.2.0', license: 'MIT', direct: true, usedBy: 11, sizeKb: 184, advisories: 0 },
  { id: 'd-rxjs', name: 'rxjs', ecosystem: 'npm', version: '7.8.1', latestVersion: '7.8.1', license: 'Apache-2.0', direct: true, usedBy: 38, sizeKb: 214, advisories: 0 },
  { id: 'd-typescript', name: 'typescript', ecosystem: 'npm', version: '5.5.4', latestVersion: '5.6.2', license: 'Apache-2.0', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-zone', name: 'zone.js', ecosystem: 'npm', version: '0.14.10', latestVersion: '0.14.10', license: 'MIT', direct: true, usedBy: 1, sizeKb: 42, advisories: 0 },
  { id: 'd-date-fns', name: 'date-fns', ecosystem: 'npm', version: '3.6.0', latestVersion: '4.1.0', license: 'MIT', direct: true, usedBy: 9, sizeKb: 74, advisories: 0 },
  { id: 'd-zod', name: 'zod', ecosystem: 'npm', version: '3.23.8', latestVersion: '3.23.8', license: 'MIT', direct: true, usedBy: 12, sizeKb: 58, advisories: 0 },
  { id: 'd-jest', name: 'jest', ecosystem: 'npm', version: '29.7.0', latestVersion: '29.7.0', license: 'MIT', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-playwright', name: '@playwright/test', ecosystem: 'npm', version: '1.47.0', latestVersion: '1.48.1', license: 'Apache-2.0', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-eslint', name: 'eslint', ecosystem: 'npm', version: '9.10.0', latestVersion: '9.12.0', license: 'MIT', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-lodash-es', name: 'lodash-es', ecosystem: 'npm', version: '4.17.21', latestVersion: '4.17.21', license: 'MIT', direct: false, usedBy: 4, sizeKb: 92, advisories: 0, note: 'Transitive via tooling only' },

  // ---- Maven / backend ---------------------------------------------------
  { id: 'd-spring-boot', name: 'spring-boot-starter-web', ecosystem: 'maven', version: '3.3.4', latestVersion: '3.3.4', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 486, advisories: 0 },
  { id: 'd-spring-data', name: 'spring-boot-starter-data-jpa', ecosystem: 'maven', version: '3.3.4', latestVersion: '3.3.4', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 412, advisories: 0 },
  { id: 'd-spring-security', name: 'spring-boot-starter-security', ecosystem: 'maven', version: '3.3.4', latestVersion: '3.3.4', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 368, advisories: 0 },
  { id: 'd-spring-cache', name: 'spring-boot-starter-cache', ecosystem: 'maven', version: '3.3.4', latestVersion: '3.3.4', license: 'Apache-2.0', direct: true, usedBy: 5, sizeKb: 96, advisories: 0 },
  { id: 'd-hibernate', name: 'hibernate-core', ecosystem: 'maven', version: '6.5.2', latestVersion: '6.6.1', license: 'LGPL-2.1', direct: false, usedBy: 7, sizeKb: 8420, advisories: 0 },
  { id: 'd-postgresql-driver', name: 'postgresql', ecosystem: 'maven', version: '42.7.4', latestVersion: '42.7.4', license: 'BSD-2-Clause', direct: true, usedBy: 7, sizeKb: 1080, advisories: 0 },
  { id: 'd-lettuce', name: 'lettuce-core', ecosystem: 'maven', version: '6.4.0', latestVersion: '6.4.0', license: 'Apache-2.0', direct: true, usedBy: 4, sizeKb: 946, advisories: 0 },
  { id: 'd-jackson', name: 'jackson-databind', ecosystem: 'maven', version: '2.17.2', latestVersion: '2.18.0', license: 'Apache-2.0', direct: false, usedBy: 7, sizeKb: 1620, advisories: 0 },
  { id: 'd-flyway', name: 'flyway-core', ecosystem: 'maven', version: '10.17.3', latestVersion: '10.19.0', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 284, advisories: 0 },
  { id: 'd-resilience4j', name: 'resilience4j-spring-boot3', ecosystem: 'maven', version: '2.2.0', latestVersion: '2.2.0', license: 'Apache-2.0', direct: true, usedBy: 5, sizeKb: 214, advisories: 0 },
  { id: 'd-mapstruct', name: 'mapstruct', ecosystem: 'maven', version: '1.6.2', latestVersion: '1.6.2', license: 'Apache-2.0', direct: true, usedBy: 7, sizeKb: 68, advisories: 0 },
  { id: 'd-junit', name: 'junit-jupiter', ecosystem: 'maven', version: '5.11.0', latestVersion: '5.11.2', license: 'EPL-2.0', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-testcontainers', name: 'testcontainers', ecosystem: 'maven', version: '1.20.1', latestVersion: '1.20.2', license: 'MIT', direct: true, usedBy: 0, sizeKb: 0, advisories: 0 },
  { id: 'd-commons-compress', name: 'commons-compress', ecosystem: 'maven', version: '1.26.0', latestVersion: '1.27.1', license: 'Apache-2.0', direct: false, usedBy: 2, sizeKb: 1040, advisories: 1, note: 'Moderate advisory: unbounded memory allocation on malformed archives' },

  // ---- Infrastructure ----------------------------------------------------
  { id: 'd-docker-eclipse-temurin', name: 'eclipse-temurin:21-jre', ecosystem: 'docker', version: '21.0.4', latestVersion: '21.0.4', license: 'GPL-2.0-with-CPE', direct: true, usedBy: 7, sizeKb: 0, advisories: 0 },
  { id: 'd-docker-node', name: 'node:20-alpine', ecosystem: 'docker', version: '20.17.0', latestVersion: '20.17.0', license: 'MIT', direct: true, usedBy: 1, sizeKb: 0, advisories: 0 },
  { id: 'd-docker-postgres', name: 'postgres:16-alpine', ecosystem: 'docker', version: '16.4.0', latestVersion: '16.4.0', license: 'PostgreSQL', direct: true, usedBy: 1, sizeKb: 0, advisories: 0 },
  { id: 'd-docker-redis', name: 'redis:7-alpine', ecosystem: 'docker', version: '7.4.0', latestVersion: '7.4.0', license: 'BSD-3-Clause', direct: true, usedBy: 1, sizeKb: 0, advisories: 0 },
];

const DEPENDENCIES: readonly Dependency[] = SPECS.map((spec) => {
  const status = statusFor(spec);
  return { ...spec, status, risk: riskFor(spec, status) };
});

const TECHNOLOGIES: readonly Technology[] = [
  { id: 't-angular', name: 'Angular', category: 'frontend', share: 24, linesOfCode: 21840, version: '18.2', dependencyCount: 12 },
  { id: 't-typescript', name: 'TypeScript', category: 'language', share: 19, linesOfCode: 17290, version: '5.5', dependencyCount: 8 },
  { id: 't-java', name: 'Java', category: 'language', share: 27, linesOfCode: 24560, version: '21', dependencyCount: 9 },
  { id: 't-spring', name: 'Spring Boot', category: 'backend', share: 14, linesOfCode: 12730, version: '3.3', dependencyCount: 14 },
  { id: 't-rxjs', name: 'RxJS', category: 'frontend', share: 6, linesOfCode: 5460, version: '7.8', dependencyCount: 1 },
  { id: 't-postgres', name: 'PostgreSQL', category: 'datastore', share: 5, linesOfCode: 4550, version: '16', dependencyCount: 3 },
  { id: 't-redis', name: 'Redis', category: 'datastore', share: 2, linesOfCode: 1820, version: '7.4', dependencyCount: 2 },
  { id: 't-docker', name: 'Docker', category: 'infrastructure', share: 3, linesOfCode: 2730, version: '27.2', dependencyCount: 4 },
];

const outdated: number = DEPENDENCIES.filter(
  (d) => d.status === 'minor-behind' || d.status === 'major-behind',
).length;

export const ATLAS_DEPENDENCIES: DependencyProfile = {
  // Direct dependencies are enumerated above; the transitive total reflects
  // the full resolved tree reported by the package managers.
  total: 184,
  direct: DEPENDENCIES.filter((d) => d.direct).length,
  transitive: 184 - DEPENDENCIES.filter((d) => d.direct).length,
  outdated,
  deprecated: DEPENDENCIES.filter((d) => d.status === 'deprecated').length,
  advisories: DEPENDENCIES.reduce((sum, d) => sum + (d.advisories ?? 0), 0),
  dependencies: DEPENDENCIES,
  technologies: TECHNOLOGIES,
};
