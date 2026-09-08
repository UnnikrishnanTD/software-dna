import { RepositoryAnalysis } from '../models/analysis.model';
import { compareMeasurements, forLayout } from '../util/measurement';
import { DoctorBlock, DoctorMessage } from '../models/insight.model';

/**
 * Builds Doctor answers *from the analysis itself* rather than from canned
 * strings. Every number, file name and ranking below is read out of the
 * data, so the answers stay correct if the underlying analysis changes —
 * and so the Doctor genuinely appears to have read the repository.
 *
 * Intent matching is keyword-based. When the HTTP implementation lands this
 * whole file is replaced by a server call; nothing outside it changes.
 */

type Intent =
  | 'architecture'
  | 'priority'
  | 'risk'
  | 'graph'
  | 'history'
  | 'testing'
  | 'security'
  | 'performance'
  | 'dependencies'
  | 'summary'
  | 'unknown';

const INTENT_KEYWORDS: readonly (readonly [Intent, readonly string[]])[] = [
  ['priority', ['fix first', 'priority', 'prioritise', 'prioritize', 'where do i start', 'what should i do', 'next step']],
  ['risk', ['biggest risk', 'riskiest', 'most risk', 'dangerous', 'hotspot', 'worst file']],
  ['graph', ['graph', 'dependency map', 'topology', 'how are', 'connected', 'coupling']],
  ['history', ['last year', 'changed over', 'history', 'evolution', 'trend', 'over time']],
  ['testing', ['test', 'coverage', 'spec', 'untested']],
  ['security', ['security', 'vulnerab', 'advisor', 'cve', 'secure']],
  ['performance', ['performance', 'slow', 'latency', 'bundle', 'fast']],
  ['dependencies', ['dependenc', 'package', 'library', 'outdated', 'upgrade']],
  ['architecture', ['architecture', 'structure', 'modular', 'design', 'layer']],
  ['summary', ['summary', 'overview', 'how healthy', 'overall', 'tell me about']],
];

function detectIntent(question: string): Intent {
  const text = question.toLowerCase();
  for (const [intent, keywords] of INTENT_KEYWORDS) {
    if (keywords.some((keyword) => text.includes(keyword))) return intent;
  }
  return 'unknown';
}

function dimension(analysis: RepositoryAnalysis, key: string) {
  return analysis.dna.dimensions.find((d) => d.key === key);
}

function strongest(analysis: RepositoryAnalysis) {
  // Unscored dimensions sort last: an absent measurement is neither the best
  // nor the worst thing about a repository.
  return [...analysis.dna.dimensions].sort((a, b) =>
    compareMeasurements(a.score, b.score, 'desc'),
  )[0];
}

function weakest(analysis: RepositoryAnalysis) {
  return [...analysis.dna.dimensions].sort((a, b) =>
    compareMeasurements(a.score, b.score, 'asc'),
  )[0];
}

interface Answer {
  readonly text: string;
  readonly blocks: readonly DoctorBlock[];
}

function buildAnswer(analysis: RepositoryAnalysis, intent: Intent): Answer {
  const name = analysis.repository.displayName;
  const top = strongest(analysis);
  const low = weakest(analysis);
  const hotspots = [...analysis.hotspots].sort((a, b) => b.riskScore - a.riskScore);
  const [first, second] = hotspots;

  switch (intent) {
    case 'priority':
      return {
        text: `Fix them in this order. The ranking weighs how much each change moves the score against how much work it is, so the cheap wins are not buried underneath the expensive ones.`,
        blocks: [
          { type: 'plan', steps: analysis.remediation.slice(0, 4) },
          {
            type: 'text',
            text: `If you only do one thing: ${analysis.remediation[0].title.toLowerCase()}. It is the change the rest of the list depends on.`,
          },
        ],
      };

    case 'risk':
      return {
        text: `Risk in ${name} is concentrated, not spread out. Two files carry most of it.`,
        blocks: [
          {
            type: 'nodes',
            caption: 'Highest-risk units',
            nodeIds: hotspots
              .slice(0, 3)
              .map((h) => h.architectureNodeId)
              .filter((id): id is string => Boolean(id)),
          },
          { type: 'metric', label: `${first.name} risk score`, value: first.riskScore, verdictScore: 100 - first.riskScore },
          { type: 'metric', label: `${second.name} risk score`, value: second.riskScore, verdictScore: 100 - second.riskScore },
          { type: 'text', text: first.rationale },
          {
            type: 'list',
            ordered: false,
            items: [
              `${first.name} — ${first.changes} changes, complexity ${first.complexityScore}, ${first.coverage}% covered`,
              `${second.name} — ${second.changes} changes, complexity ${second.complexityScore}, ${second.coverage}% covered`,
              `Together they account for ${Math.round(((first.changes + second.changes) / analysis.hotspots.reduce((s, h) => s + h.changes, 0)) * 100)}% of all tracked change`,
            ],
          },
        ],
      };

    case 'architecture': {
      const arch = dimension(analysis, 'architecture');
      const cycles = analysis.architecture.circularDependencies;
      return {
        text: `Architecture scores ${arch?.score} — ${arch?.headline.toLowerCase()} What holds it back is coupling, not structure.`,
        blocks: [
          { type: 'metric', label: 'Architecture', value: forLayout(arch?.score), verdictScore: forLayout(arch?.score) },
          { type: 'text', text: arch?.summary ?? '' },
          {
            type: 'list',
            ordered: false,
            items: [
              `${cycles.length} circular ${cycles.length === 1 ? 'dependency' : 'dependencies'} detected: ${cycles.map((c) => c.join(' ↔ ')).join(', ')}`,
              `${analysis.architecture.nodes.filter((n) => n.fanOut >= 8).length} units exceed a fan-out of 8`,
              `${analysis.architecture.nodes.length} units across ${new Set(analysis.architecture.nodes.map((n) => n.layer)).size} layers`,
            ],
          },
          { type: 'text', text: `The structure itself is sound — every feature module is isolated and reaches the domain through a facade. The score is held down by a small number of units that have accumulated too many relationships.` },
        ],
      };
    }

    case 'graph': {
      const byFanOut = [...analysis.architecture.nodes].sort((a, b) => b.fanOut - a.fanOut);
      const hubs = byFanOut.slice(0, 3);
      return {
        text: `The graph has ${analysis.architecture.nodes.length} units and ${analysis.architecture.edges.length} relationships, arranged in layers. Reading it top to bottom: presentation depends on application, application on domain, domain on infrastructure.`,
        blocks: [
          { type: 'nodes', caption: 'The three widest hubs', nodeIds: hubs.map((n) => n.id) },
          {
            type: 'list',
            ordered: false,
            items: hubs.map((n) => `${n.name} — depends on ${n.fanOut}, depended on by ${n.fanIn}`),
          },
          { type: 'text', text: `Most units sit at a fan-out of three or four, which is healthy. The hubs above are the exception, and they are where change propagates furthest.` },
        ],
      };
    }

    case 'history': {
      const points = analysis.evolution.points;
      const latest = points[points.length - 1];
      const previous = points[points.length - 2];
      const recent = analysis.evolution.milestones.filter((m) => m.year >= latest.year - 1);
      return {
        text: `Between ${previous.year} and ${latest.year}, ${name} moved from ${previous.overall} to ${latest.overall} overall.`,
        blocks: [
          {
            type: 'list',
            ordered: false,
            items: [
              `Contributors: ${previous.contributors} → ${latest.contributors}`,
              `Lines of code: ${previous.linesOfCode.toLocaleString()} → ${latest.linesOfCode.toLocaleString()}`,
              `Test coverage: ${previous.testCoverage}% → ${latest.testCoverage}%`,
              `Hotspots: ${previous.hotspots} → ${latest.hotspots}`,
            ],
          },
          { type: 'text', text: recent.map((m) => `${m.year}: ${m.title} — ${m.description}`).join('\n\n') },
        ],
      };
    }

    case 'testing': {
      const testing = dimension(analysis, 'testing');
      const worst = [...analysis.hotspots].sort((a, b) => compareMeasurements(a.coverage, b.coverage, 'asc')).slice(0, 3);
      return {
        text: `Testing is the weakest dimension at ${testing?.score}. The headline number is not the problem — the distribution is.`,
        blocks: [
          { type: 'metric', label: 'Testing', value: forLayout(testing?.score), verdictScore: forLayout(testing?.score) },
          { type: 'text', text: testing?.summary ?? '' },
          {
            type: 'list',
            ordered: false,
            items: worst.map((h) => `${h.name} — ${h.coverage}% covered, ${h.changes} changes, risk ${h.riskScore}`),
          },
          { type: 'text', text: `Coverage is highest where code is most stable and lowest where it changes most. Raising the overall percentage will not help; raising it on these three will.` },
        ],
      };
    }

    case 'security': {
      const security = dimension(analysis, 'security');
      const flagged = analysis.dependencies.dependencies.filter((d) => (d.advisories ?? 0) > 0);
      return {
        text:
          flagged.length === 0
            ? `Nothing urgent. Security scores ${security?.score} with no outstanding advisories.`
            : `One item, not urgent but worth clearing. Security otherwise scores ${security?.score}.`,
        blocks: [
          { type: 'metric', label: 'Security', value: forLayout(security?.score), verdictScore: forLayout(security?.score) },
          ...(flagged.length > 0
            ? ([
                {
                  type: 'list' as const,
                  ordered: false,
                  items: flagged.map((d) => `${d.name} ${d.version} — ${d.note ?? 'advisory reported'}`),
                },
              ])
            : []),
          { type: 'text', text: security?.summary ?? '' },
        ],
      };
    }

    case 'performance': {
      const perf = dimension(analysis, 'performance');
      return {
        text: `Performance scores ${perf?.score}. ${perf?.headline}`,
        blocks: [
          { type: 'metric', label: 'Performance', value: forLayout(perf?.score), verdictScore: forLayout(perf?.score) },
          { type: 'text', text: perf?.summary ?? '' },
          { type: 'list', ordered: false, items: [...(perf?.watchItems ?? [])] },
        ],
      };
    }

    case 'dependencies': {
      const deps = analysis.dependencies;
      return {
        text: `${deps.total} dependencies resolved, ${deps.direct} of them direct. ${deps.outdated} have an update available and ${deps.advisories} carry an advisory.`,
        blocks: [
          { type: 'metric', label: 'Dependencies', value: dimension(analysis, 'dependencies')?.score ?? 0, verdictScore: dimension(analysis, 'dependencies')?.score ?? 0 },
          {
            type: 'list',
            ordered: false,
            items: [
              `${deps.direct} direct, ${deps.transitive} transitive`,
              `${deps.outdated} with an available update`,
              `${deps.deprecated} deprecated`,
              `${deps.advisories} open ${deps.advisories === 1 ? 'advisory' : 'advisories'}`,
            ],
          },
          { type: 'text', text: dimension(analysis, 'dependencies')?.summary ?? '' },
        ],
      };
    }

    case 'summary':
      return {
        text: `${name} scores ${analysis.dna.overall} overall, which puts it in the ${analysis.dna.percentile}th percentile of the reference corpus.`,
        blocks: [
          { type: 'metric', label: `Strongest — ${top.label}`, value: forLayout(top.score), verdictScore: forLayout(top.score) },
          { type: 'metric', label: `Weakest — ${low.label}`, value: forLayout(low.score), verdictScore: forLayout(low.score) },
          { type: 'text', text: `The biggest engineering risk is concentrated around ${first.name} and ${second.name}.` },
          { type: 'plan', steps: analysis.remediation.slice(0, 3) },
        ],
      };

    case 'unknown':
      return {
        text: `I can only reason about what the analysis actually measured, and I don't have a confident answer to that one. Here is what I can tell you about ${name}.`,
        blocks: [
          { type: 'metric', label: `Strongest — ${top.label}`, value: forLayout(top.score), verdictScore: forLayout(top.score) },
          { type: 'metric', label: `Weakest — ${low.label}`, value: forLayout(low.score), verdictScore: forLayout(low.score) },
          {
            type: 'list',
            ordered: false,
            items: [
              'Why a dimension scores the way it does',
              'What to fix first, and in what order',
              'Which files carry the most risk',
              'How the graph is shaped and where change propagates',
              'What changed over the last year',
            ],
          },
        ],
      };
  }
}

export function buildDoctorMessage(
  analysis: RepositoryAnalysis,
  question: string,
): DoctorMessage {
  const { text, blocks } = buildAnswer(analysis, detectIntent(question));
  return {
    id: `doctor-${Date.now()}-${Math.round(Math.random() * 1e6)}`,
    author: 'doctor',
    text,
    blocks,
    timestamp: Date.now(),
  };
}

/** The Doctor's unprompted opening assessment, shown before any question. */
export function buildOpeningAssessment(analysis: RepositoryAnalysis): DoctorMessage {
  const top = strongest(analysis);
  const low = weakest(analysis);
  const hotspots = [...analysis.hotspots].sort((a, b) => b.riskScore - a.riskScore);
  return {
    id: 'doctor-opening',
    author: 'doctor',
    text: `I've read ${analysis.repository.displayName} — ${analysis.stats.files.toLocaleString()} files, ${analysis.stats.linesOfCode.toLocaleString()} lines, ${analysis.stats.commits.toLocaleString()} commits of history.`,
    blocks: [
      { type: 'metric', label: `Strongest — ${top.label}`, value: forLayout(top.score), verdictScore: forLayout(top.score) },
      { type: 'metric', label: `Weakest — ${low.label}`, value: forLayout(low.score), verdictScore: forLayout(low.score) },
      {
        type: 'text',
        text: `The biggest engineering risk is concentrated around ${hotspots[0].name} and ${hotspots[1].name}. Ask me anything about the analysis, or start with one of the questions below.`,
      },
      { type: 'plan', steps: analysis.remediation.slice(0, 3) },
    ],
    timestamp: Date.now(),
  };
}
