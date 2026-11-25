import type { LifeStage, TurnGranularity } from './types';
import { clamp } from './rng';

export const GRANULARITY_MONTHS: Record<TurnGranularity, number> = {
  YEARLY: 12,
  QUARTERLY: 3,
  BIMONTHLY: 2,
  MONTHLY: 1,
};

export function getMonthsPerTurn(granularity: TurnGranularity): number {
  return GRANULARITY_MONTHS[granularity];
}

/** Fraction of a year represented by one turn (e.g. BIMONTHLY -> 2/12 = 1/6). */
export function getDt(granularity: TurnGranularity): number {
  return getMonthsPerTurn(granularity) / 12;
}

/**
 * Poisson survival scaling: converts an annual probability into an equivalent
 * per-turn probability so changing turn granularity never inflates/deflates annual odds.
 */
export function scaleAnnualProbability(annualProb: number, granularity: TurnGranularity): number {
  const p = clamp(annualProb, 0, 1);
  if (p === 0) return 0;
  if (p === 1) return 1;
  const dt = getDt(granularity);
  return 1 - Math.pow(1 - p, dt);
}

/** Converts an annual compound rate (e.g. 0.08 = +8%/yr) to a turn multiplier. */
export function scaleCompoundFactor(annualRate: number, granularity: TurnGranularity): number {
  const dt = getDt(granularity);
  const safeBase = Math.max(0.05, 1 + annualRate);
  return Math.pow(safeBase, dt);
}

export function getAgeYears(ageMonths: number): number {
  return Math.floor(ageMonths / 12);
}

export function getLifeStage(ageMonths: number): LifeStage {
  const years = getAgeYears(ageMonths);
  if (years <= 4) return 'infancy';
  if (years <= 12) return 'childhood';
  if (years <= 17) return 'adolescence';
  if (years <= 29) return 'young_adult';
  if (years <= 49) return 'prime_adult';
  if (years <= 64) return 'mature_adult';
  return 'elder';
}

export function formatAge(ageMonths: number): string {
  const years = Math.floor(ageMonths / 12);
  const months = ageMonths % 12;
  if (months === 0) return `${years}y`;
  return `${years}y ${months}m`;
}

/**
 * Computes Action Points granted at the start of a turn, scaled by granularity
 * and penalized by severe burnout.
 */
export function computeTurnActionPoints(
  granularity: TurnGranularity,
  energy: number,
  burnout: number,
): number {
  const baseByGranularity: Record<TurnGranularity, number> = {
    MONTHLY: 3,
    BIMONTHLY: 5,
    QUARTERLY: 7,
    YEARLY: 12,
  };
  let ap = baseByGranularity[granularity];
  if (burnout >= 75) ap -= 2;
  else if (burnout >= 50) ap -= 1;
  if (energy <= 20) ap -= 1;
  return Math.max(2, ap);
}

export function advanceCalendar(
  year: number,
  month: number,
  monthsToAdd: number,
): { year: number; month: number } {
  const totalMonths = (year * 12 + (month - 1)) + monthsToAdd;
  return {
    year: Math.floor(totalMonths / 12),
    month: (totalMonths % 12) + 1,
  };
}
