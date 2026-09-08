import { ATLAS_ANALYSIS } from './atlas';
import { DoctorBlock } from '../models/insight.model';
import { buildDoctorMessage, buildOpeningAssessment } from './doctor-responses';

function typesIn(blocks: readonly DoctorBlock[] | undefined): string[] {
  return (blocks ?? []).map((block) => block.type);
}

describe('the Software Doctor', () => {
  it('opens with an assessment drawn from the analysis, not a greeting', () => {
    const opening = buildOpeningAssessment(ATLAS_ANALYSIS);

    expect(opening.author).toBe('doctor');
    expect(opening.text).toContain('Atlas Commerce Platform');
    expect(opening.text).toContain('1,284');
    expect(typesIn(opening.blocks)).toContain('plan');
  });

  it('names the true strongest and weakest dimensions', () => {
    const opening = buildOpeningAssessment(ATLAS_ANALYSIS);
    const metrics = (opening.blocks ?? []).filter(
      (block): block is Extract<DoctorBlock, { type: 'metric' }> =>
        block.type === 'metric',
    );

    expect(metrics[0].label).toBe('Strongest — Dependencies');
    expect(metrics[0].value).toBe(92);
    expect(metrics[1].label).toBe('Weakest — Testing');
    expect(metrics[1].value).toBe(67);
  });

  it('answers a prioritisation question with the remediation plan', () => {
    const answer = buildDoctorMessage(ATLAS_ANALYSIS, 'What should I fix first?');
    expect(typesIn(answer.blocks)).toContain('plan');
  });

  it('answers a risk question with the highest-risk files and graph links', () => {
    const answer = buildDoctorMessage(
      ATLAS_ANALYSIS,
      'Which files are the biggest risk?',
    );

    expect(typesIn(answer.blocks)).toContain('nodes');
    const nodes = (answer.blocks ?? []).find(
      (block): block is Extract<DoctorBlock, { type: 'nodes' }> =>
        block.type === 'nodes',
    );
    expect(nodes?.nodeIds).toContain('user-service');
  });

  it('quotes the real circular dependencies when asked about architecture', () => {
    const answer = buildDoctorMessage(
      ATLAS_ANALYSIS,
      'Why is my architecture score not higher?',
    );
    const list = (answer.blocks ?? []).find(
      (block): block is Extract<DoctorBlock, { type: 'list' }> =>
        block.type === 'list',
    );

    expect(list?.items[0]).toContain('2 circular dependencies');
    expect(list?.items[0]).toContain('user-service');
  });

  it('reports the actual year-over-year movement for a history question', () => {
    const answer = buildDoctorMessage(
      ATLAS_ANALYSIS,
      'What changed over the last year?',
    );

    expect(answer.text).toContain('2025');
    expect(answer.text).toContain('2026');
    const list = (answer.blocks ?? []).find(
      (block): block is Extract<DoctorBlock, { type: 'list' }> =>
        block.type === 'list',
    );
    expect(list?.items.some((item) => item.includes('15 → 17'))).toBe(true);
  });

  it('flags the outstanding advisory when asked about security', () => {
    const answer = buildDoctorMessage(
      ATLAS_ANALYSIS,
      'Is there anything urgent on security?',
    );
    const list = (answer.blocks ?? []).find(
      (block): block is Extract<DoctorBlock, { type: 'list' }> =>
        block.type === 'list',
    );
    expect(list?.items.some((item) => item.includes('commons-compress'))).toBe(
      true,
    );
  });

  it('admits when it cannot answer rather than inventing something', () => {
    const answer = buildDoctorMessage(
      ATLAS_ANALYSIS,
      'What is the weather in Lisbon?',
    );

    expect(answer.text).toContain("don't have a confident answer");
    expect(typesIn(answer.blocks)).toContain('list');
  });

  it('matches intent on phrasing rather than exact wording', () => {
    const phrasings = [
      'what should i fix first',
      'Where do I start?',
      'give me a priority order',
    ];
    for (const question of phrasings) {
      expect(typesIn(buildDoctorMessage(ATLAS_ANALYSIS, question).blocks)).toContain(
        'plan',
      );
    }
  });

  it('gives every message a distinct id', () => {
    const ids = new Set(
      Array.from({ length: 5 }, () =>
        buildDoctorMessage(ATLAS_ANALYSIS, 'What should I fix first?').id,
      ),
    );
    expect(ids.size).toBe(5);
  });
});
