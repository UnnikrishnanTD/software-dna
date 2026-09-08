import {
  clamp,
  healthRamp,
  riskRank,
  scale,
  verdictFor,
  verdictLabel,
} from './health';

describe('health scale', () => {
  describe('verdictFor', () => {
    it('bands scores from critical through exemplary', () => {
      expect(verdictFor(12)).toBe('critical');
      expect(verdictFor(50)).toBe('at-risk');
      expect(verdictFor(70)).toBe('fair');
      expect(verdictFor(84)).toBe('healthy');
      expect(verdictFor(95)).toBe('exemplary');
    });

    it('treats each band boundary as inclusive of its lower bound', () => {
      expect(verdictFor(90)).toBe('exemplary');
      expect(verdictFor(89)).toBe('healthy');
      expect(verdictFor(78)).toBe('healthy');
      expect(verdictFor(77)).toBe('fair');
    });

    it('clamps values outside 0–100 rather than falling through', () => {
      expect(verdictFor(-40)).toBe('critical');
      expect(verdictFor(180)).toBe('exemplary');
    });

    it('labels the demo repository score as healthy', () => {
      expect(verdictLabel(87)).toBe('Healthy');
    });
  });

  describe('healthRamp', () => {
    it('returns a colour for every point on the scale', () => {
      for (let score = 0; score <= 100; score += 5) {
        expect(healthRamp(score)).toMatch(/^rgb\(\d+ \d+ \d+\)$/);
      }
    });

    it('interpolates between stops instead of snapping to them', () => {
      // Halfway between the critical and poor stops.
      expect(healthRamp(22.5)).toBe('rgb(242 108 94)');
    });

    it('anchors the ends of the ramp', () => {
      expect(healthRamp(0)).toBe('rgb(242 87 108)');
      expect(healthRamp(100)).toBe('rgb(62 207 142)');
    });

    it('moves monotonically away from red as the score rises', () => {
      const redAt20 = Number(/rgb\((\d+)/.exec(healthRamp(20))?.[1]);
      const redAt90 = Number(/rgb\((\d+)/.exec(healthRamp(90))?.[1]);
      expect(redAt90).toBeLessThan(redAt20);
    });
  });

  it('orders risk levels from low to critical', () => {
    expect(riskRank('low')).toBeLessThan(riskRank('medium'));
    expect(riskRank('medium')).toBeLessThan(riskRank('high'));
    expect(riskRank('high')).toBeLessThan(riskRank('critical'));
  });

  describe('scale', () => {
    it('maps a value between two ranges', () => {
      expect(scale(5, 0, 10, 0, 100)).toBe(50);
      expect(scale(0, 0, 10, 20, 40)).toBe(20);
    });

    it('supports inverted target ranges, as used by plot Y axes', () => {
      expect(scale(0, 0, 100, 460, 20)).toBe(460);
      expect(scale(100, 0, 100, 460, 20)).toBe(20);
    });

    it('returns the low bound rather than dividing by zero', () => {
      expect(scale(5, 3, 3, 10, 90)).toBe(10);
    });
  });

  it('clamps to the given bounds', () => {
    expect(clamp(5, 0, 10)).toBe(5);
    expect(clamp(-5, 0, 10)).toBe(0);
    expect(clamp(50, 0, 10)).toBe(10);
  });
});
