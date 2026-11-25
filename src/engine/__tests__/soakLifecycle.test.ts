import { describe, expect, it } from 'vitest';
import {
  advanceTurn,
  createNewLife,
  getAgeYears,
  HOURS_PER_WEEK,
  isChoiceAvailable,
  loadEraPack,
  resolveEventChoice,
  sumScheduleHours,
} from '../index';
import type { GameState, TurnGranularity } from '../types';

function assertStateInvariants(state: GameState) {
  const s = state.player.stats;
  for (const [key, val] of Object.entries(s)) {
    expect(Number.isNaN(val), `stat ${key} is NaN`).toBe(false);
    expect(val, `stat ${key} out of [0,100]`).toBeGreaterThanOrEqual(0);
    expect(val, `stat ${key} out of [0,100]`).toBeLessThanOrEqual(100);
  }
  expect(Number.isNaN(state.player.finance.cash)).toBe(false);
  expect(state.player.finance.creditScore).toBeGreaterThanOrEqual(300);
  expect(state.player.finance.creditScore).toBeLessThanOrEqual(850);
  expect(sumScheduleHours(state.player.schedule)).toBe(HOURS_PER_WEEK);
}

function simulateFullLife(seed: number, granularity: TurnGranularity): GameState {
  const pack = loadEraPack('modern_2020s');
  let state = createNewLife(
    {
      seed,
      granularity,
      countryId: 'usa',
      chaosLevel: 45,
    },
    pack,
  );

  const maxTurns = granularity === 'MONTHLY' ? 1600 : granularity === 'BIMONTHLY' ? 800 : 400;
  for (let i = 0; i < maxTurns && state.player.alive; i++) {
    state = advanceTurn(state, pack);
    assertStateInvariants(state);

    if (state.pendingEvent) {
      const validChoice =
        state.pendingEvent.choices.find((c) => isChoiceAvailable(state, c)) ??
        state.pendingEvent.choices[0]!;
      state = resolveEventChoice(state, validChoice.id, pack);
      assertStateInvariants(state);
    }
  }
  return state;
}

describe('Full Birth-to-Death Soak Tests', () => {
  it('simulates complete lives cleanly across all 4 granularities with zero NaNs', () => {
    const granularities: TurnGranularity[] = ['YEARLY', 'QUARTERLY', 'BIMONTHLY', 'MONTHLY'];

    for (const g of granularities) {
      const finalState = simulateFullLife(1001, g);
      expect(finalState.player.alive).toBe(false);
      expect(finalState.deathSummary).not.toBeNull();
      expect(getAgeYears(finalState.player.ageMonths)).toBeGreaterThanOrEqual(35);
      expect(getAgeYears(finalState.player.ageMonths)).toBeLessThanOrEqual(115);
      expect(finalState.deathSummary!.legacyScore).toBeGreaterThan(0);
    }
  });

  it('is 100% deterministic for identical seeds across full lifetimes', () => {
    const runA = simulateFullLife(7777, 'BIMONTHLY');
    const runB = simulateFullLife(7777, 'BIMONTHLY');

    expect(runA.player.ageMonths).toBe(runB.player.ageMonths);
    expect(runA.player.finance.cash).toBe(runB.player.finance.cash);
    expect(runA.deathSummary).toEqual(runB.deathSummary);
    expect(runA.timeline.length).toBe(runB.timeline.length);
  });

  it('runs a 15-life multi-seed soak without crashes or invalid states', () => {
    let totalAge = 0;
    for (let seed = 1; seed <= 15; seed++) {
      const end = simulateFullLife(seed * 97, 'BIMONTHLY');
      expect(end.player.alive).toBe(false);
      expect(end.deathSummary).not.toBeNull();
      totalAge += getAgeYears(end.player.ageMonths);
    }
    const avgAge = totalAge / 15;
    expect(avgAge).toBeGreaterThan(55);
    expect(avgAge).toBeLessThan(100);
  });
});
