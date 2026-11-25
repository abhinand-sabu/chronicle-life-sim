import { describe, expect, it } from 'vitest';
import {
  computeTurnActionPoints,
  evaluateScheduleTick,
  getDt,
  getLifeStage,
  HOURS_PER_WEEK,
  normalizeSchedule,
  scaleAnnualProbability,
  scaleCompoundFactor,
  SCHEDULE_PRESETS,
  SeededRng,
  sumScheduleHours,
} from '../index';
import type { TurnGranularity } from '../types';

describe('SeededRng determinism', () => {
  it('produces identical sequences from the same seed', () => {
    const a = new SeededRng(424242);
    const b = new SeededRng(424242);
    const seqA = Array.from({ length: 20 }, () => a.next());
    const seqB = Array.from({ length: 20 }, () => b.next());
    expect(seqA).toEqual(seqB);
    expect(a.getState()).toBe(b.getState());
  });
});

describe('Time & Granularity Scaling', () => {
  it('preserves annual probability under Poisson survival scaling across all granularities', () => {
    const annualP = 0.25;
    const granularities: Array<[TurnGranularity, number]> = [
      ['YEARLY', 1],
      ['QUARTERLY', 4],
      ['BIMONTHLY', 6],
      ['MONTHLY', 12],
    ];

    for (const [g, turnsPerYear] of granularities) {
      const pTurn = scaleAnnualProbability(annualP, g);
      const compoundedAnnual = 1 - Math.pow(1 - pTurn, turnsPerYear);
      expect(compoundedAnnual).toBeCloseTo(annualP, 8);
    }
  });

  it('compounds annual growth consistently across granularities', () => {
    const annualRate = 0.08;
    const bimonthlyFactor = scaleCompoundFactor(annualRate, 'BIMONTHLY');
    expect(Math.pow(bimonthlyFactor, 6)).toBeCloseTo(1 + annualRate, 8);
    expect(getDt('BIMONTHLY')).toBeCloseTo(2 / 12, 8);
  });

  it('maps ages to life stages accurately', () => {
    expect(getLifeStage(0)).toBe('infancy');
    expect(getLifeStage(5 * 12)).toBe('childhood');
    expect(getLifeStage(14 * 12)).toBe('adolescence');
    expect(getLifeStage(22 * 12)).toBe('young_adult');
    expect(getLifeStage(38 * 12)).toBe('prime_adult');
    expect(getLifeStage(55 * 12)).toBe('mature_adult');
    expect(getLifeStage(70 * 12)).toBe('elder');
  });

  it('scales Action Points by granularity and penalizes high burnout', () => {
    const normalAp = computeTurnActionPoints('BIMONTHLY', 80, 10);
    const burnedOutAp = computeTurnActionPoints('BIMONTHLY', 80, 80);
    expect(normalAp).toBe(5);
    expect(burnedOutAp).toBeLessThan(normalAp);
    expect(computeTurnActionPoints('YEARLY', 80, 0)).toBeGreaterThan(normalAp);
  });
});

describe('168-Hour Weekly Schedule', () => {
  it('normalizes arbitrary schedules to sum to exactly 168 hours with sleep floor', () => {
    const overAllocated = normalizeSchedule({
      sleep: 60,
      workOrSchool: 60,
      hustleAndSkills: 40,
      fitnessAndSelfCare: 20,
      familyAndSocial: 20,
      leisureAndRest: 20,
    });
    expect(sumScheduleHours(overAllocated)).toBe(HOURS_PER_WEEK);

    const sleepStarved = normalizeSchedule({
      sleep: 5,
      workOrSchool: 80,
      hustleAndSkills: 40,
      fitnessAndSelfCare: 10,
      familyAndSocial: 10,
      leisureAndRest: 23,
    });
    expect(sleepStarved.sleep).toBeGreaterThanOrEqual(21);
    expect(sumScheduleHours(sleepStarved)).toBe(HOURS_PER_WEEK);
  });

  it('penalizes sleep deprivation and heavy hustle with stress and sleep debt', () => {
    const balanced = evaluateScheduleTick(SCHEDULE_PRESETS.balanced, 'BIMONTHLY', 40, 10);
    const hustle = evaluateScheduleTick(SCHEDULE_PRESETS.hustle, 'BIMONTHLY', 40, 10);

    expect(hustle.sleepDebtDelta).toBeGreaterThan(0);
    expect(balanced.sleepDebtDelta).toBeLessThan(0);
    expect(hustle.statDeltas.stress ?? 0).toBeGreaterThan(balanced.statDeltas.stress ?? 0);
    expect(hustle.focusSkillXp).toBeGreaterThan(balanced.focusSkillXp);
  });
});
