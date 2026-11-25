import type {
  ActiveCondition,
  ActiveHabit,
  ConditionDef,
  DnaProfile,
  EraPack,
  GameState,
  HabitDef,
} from './types';
import { clamp, SeededRng } from './rng';
import { getAgeYears, getDt, scaleAnnualProbability } from './time';
import { getCountry } from './eraLoader';

export function generateDna(rng: SeededRng, pack: EraPack, overrides?: Partial<DnaProfile>): DnaProfile {
  const heritableConditions = pack.conditions.conditions.filter((c) => c.heritable);
  const predispositions: string[] = [];
  for (const cond of heritableConditions) {
    if (rng.chance(0.1)) {
      predispositions.push(cond.id);
    }
  }
  return {
    longevity: clamp(Math.round(overrides?.longevity ?? 55 + rng.nextGaussian() * 18), 15, 100),
    intellectCeiling: clamp(Math.round(overrides?.intellectCeiling ?? 60 + rng.nextGaussian() * 16), 25, 100),
    athleticism: clamp(Math.round(overrides?.athleticism ?? 55 + rng.nextGaussian() * 18), 15, 100),
    mentalResilience: clamp(Math.round(overrides?.mentalResilience ?? 58 + rng.nextGaussian() * 17), 15, 100),
    appearance: clamp(Math.round(overrides?.appearance ?? 55 + rng.nextGaussian() * 18), 15, 100),
    predispositions: overrides?.predispositions ?? predispositions,
  };
}

export function inheritDna(rng: SeededRng, parentA: DnaProfile, parentB?: DnaProfile): DnaProfile {
  const b = parentB ?? parentA;
  const blend = (v1: number, v2: number) =>
    clamp(Math.round((v1 + v2) / 2 + rng.nextGaussian() * 8), 15, 100);

  const combinedPredispositions = new Set([...parentA.predispositions, ...b.predispositions]);
  const inheritedPredispositions: string[] = [];
  for (const id of combinedPredispositions) {
    if (rng.chance(0.45)) inheritedPredispositions.push(id);
  }

  return {
    longevity: blend(parentA.longevity, b.longevity),
    intellectCeiling: blend(parentA.intellectCeiling, b.intellectCeiling),
    athleticism: blend(parentA.athleticism, b.athleticism),
    mentalResilience: blend(parentA.mentalResilience, b.mentalResilience),
    appearance: blend(parentA.appearance, b.appearance),
    predispositions: inheritedPredispositions,
  };
}

export function getConditionDef(pack: EraPack, id: string): ConditionDef | undefined {
  return pack.conditions.conditions.find((c) => c.id === id);
}

export function getHabitDef(pack: EraPack, id: string): HabitDef | undefined {
  return pack.conditions.habits.find((h) => h.id === id);
}

export function addConditionToPlayer(
  conditions: ActiveCondition[],
  conditionId: string,
  ageMonths: number,
  pack: EraPack,
): ActiveCondition[] {
  if (conditions.some((c) => c.id === conditionId)) return conditions;
  const def = getConditionDef(pack, conditionId);
  if (!def) return conditions;
  return [
    ...conditions,
    {
      id: conditionId,
      diagnosedAgeMonths: ageMonths,
      severity: def.severity,
      treated: false,
    },
  ];
}

export function addHabitToPlayer(habits: ActiveHabit[], habitId: string, pack: EraPack): ActiveHabit[] {
  if (habits.some((h) => h.id === habitId)) return habits;
  if (!getHabitDef(pack, habitId)) return habits;
  return [...habits, { id: habitId, strength: 35 }];
}

export function removeHabitFromPlayer(habits: ActiveHabit[], habitId: string): ActiveHabit[] {
  return habits.filter((h) => h.id !== habitId);
}

export interface HealthTickOutput {
  healthDelta: number;
  mentalDelta: number;
  stressDelta: number;
  intelligenceDelta: number;
  habitAnnualCost: number;
  nextConditions: ActiveCondition[];
  nextHabits: ActiveHabit[];
  died: boolean;
  causeOfDeath: string | null;
  healthLogs: Array<{ title: string; detail: string }>;
}

/**
 * Computes annual mortality probability based on age, physical health, DNA longevity,
 * and active medical conditions, then scales by dt using Poisson survival math.
 */
export function computeAnnualMortalityRisk(state: GameState, pack: EraPack): number {
  const ageYears = getAgeYears(state.player.ageMonths);
  const { physicalHealth } = state.player.stats;
  const { longevity } = state.player.dna;

  // Baseline Gompertz-style age curve (very low until 65, steepens past 75)
  let annualRisk = 0.0004;
  if (ageYears >= 50) {
    const longevityOffset = (longevity - 55) * 0.16; // -6.4 to +7.2 years effective shift
    const effectiveAge = Math.max(40, ageYears - longevityOffset);
    if (effectiveAge > 55) {
      annualRisk += 0.002 * Math.pow(1.115, effectiveAge - 55);
    }
  }

  // Severe low health raises risk sharply
  if (physicalHealth <= 10) {
    annualRisk += 0.45;
  } else if (physicalHealth <= 25) {
    annualRisk += 0.12;
  } else if (physicalHealth <= 40) {
    annualRisk += 0.025;
  }

  // Active conditions add mortality risk (reduced if treated)
  for (const active of state.player.conditions) {
    const def = getConditionDef(pack, active.id);
    if (!def) continue;
    const treatmentFactor = active.treated ? 0.35 : 1.0;
    annualRisk += def.annualMortalityRisk * treatmentFactor;
  }

  // Hard ceiling near extreme old age
  if (ageYears >= 105) {
    annualRisk = Math.max(annualRisk, 0.55);
  }

  return clamp(annualRisk, 0, 0.95);
}

export function tickHealthAndHabits(
  state: GameState,
  pack: EraPack,
  rng: SeededRng,
): HealthTickOutput {
  const dt = getDt(state.config.granularity);
  const ageYears = getAgeYears(state.player.ageMonths);
  const healthLogs: Array<{ title: string; detail: string }> = [];

  let healthDelta = 0;
  let mentalDelta = 0;
  let stressDelta = 0;
  let intelligenceDelta = 0;
  let habitAnnualCost = 0;

  // Natural age-related physical decline past 42, buffered by DNA longevity & athleticism
  if (ageYears >= 42) {
    const declineRate = (ageYears - 40) * 0.14 * (1.25 - state.player.dna.longevity / 200);
    healthDelta -= declineRate * dt;
  }

  // 1. Habits
  const nextHabits: ActiveHabit[] = state.player.habits.map((h) => {
    const def = getHabitDef(pack, h.id);
    if (def) {
      const intensity = 0.5 + h.strength / 200;
      healthDelta += def.annualHealthDelta * intensity * dt;
      mentalDelta += def.annualMentalDelta * intensity * dt;
      stressDelta += def.annualStressDelta * intensity * dt;
      if (def.annualIntelligenceDelta) {
        intelligenceDelta += def.annualIntelligenceDelta * intensity * dt;
      }
      habitAnnualCost += def.annualCost;
    }
    return {
      ...h,
      strength: clamp(h.strength + 4 * dt, 10, 100),
    };
  });

  // Stress-induced negative coping habit emergence
  if (state.player.stats.stress >= 78 && ageYears >= 16) {
    const emergenceProb = scaleAnnualProbability(0.18, state.config.granularity);
    if (rng.chance(emergenceProb)) {
      const candidates = ['doomscrolling', 'smoking', 'heavy_drinking'].filter(
        (id) => !nextHabits.some((h) => h.id === id),
      );
      if (candidates.length > 0) {
        const picked = rng.pick(candidates);
        const def = getHabitDef(pack, picked);
        if (def) {
          nextHabits.push({ id: picked, strength: 30 });
          healthLogs.push({
            title: `Coping Habit Formed: ${def.name}`,
            detail: `Sustained high stress pushed you into ${def.name.toLowerCase()}.`,
          });
        }
      }
    }
  }

  // 2. Active conditions
  let nextConditions: ActiveCondition[] = [];
  for (const cond of state.player.conditions) {
    const def = getConditionDef(pack, cond.id);
    if (!def) continue;

    const mitigation = cond.treated ? 0.35 : 1.0;
    healthDelta -= def.annualHealthDrain * mitigation * dt;
    mentalDelta -= def.annualMentalDrain * mitigation * dt;

    // Non-chronic conditions naturally recover over time
    if (!def.chronic) {
      const recoveryProb = scaleAnnualProbability(cond.treated ? 0.9 : 0.65, state.config.granularity);
      if (rng.chance(recoveryProb)) {
        healthLogs.push({
          title: `Recovered from ${def.name}`,
          detail: `Your ${def.name.toLowerCase()} has cleared up.`,
        });
        continue;
      }
    }
    nextConditions.push(cond);
  }

  // 3. Predisposition / age-based condition onset
  if (ageYears >= 35) {
    for (const predId of state.player.dna.predispositions) {
      if (nextConditions.some((c) => c.id === predId)) continue;
      const onsetProb = scaleAnnualProbability(0.025, state.config.granularity);
      if (rng.chance(onsetProb)) {
        const def = getConditionDef(pack, predId);
        if (def) {
          nextConditions = addConditionToPlayer(nextConditions, predId, state.player.ageMonths, pack);
          healthLogs.push({
            title: `Diagnosed: ${def.name}`,
            detail: `A routine check reveals ${def.name} (linked to family genetics).`,
          });
        }
      }
    }
  }

  // 4. Mortality check
  const annualRisk = computeAnnualMortalityRisk(state, pack);
  const turnMortalityProb = scaleAnnualProbability(annualRisk, state.config.granularity);
  const projectedHealth = clamp(state.player.stats.physicalHealth + healthDelta, 0, 100);

  let died = false;
  let causeOfDeath: string | null = null;
  if (projectedHealth <= 0 || rng.chance(turnMortalityProb)) {
    died = true;
    const lethalCond = nextConditions
      .map((c) => getConditionDef(pack, c.id))
      .find((d) => d && d.annualMortalityRisk >= 0.01);
    if (lethalCond) {
      causeOfDeath = `Complications from ${lethalCond.name}`;
    } else if (ageYears >= 72) {
      causeOfDeath = 'Peaceful natural causes';
    } else if (projectedHealth <= 5) {
      causeOfDeath = 'Severe health collapse';
    } else {
      causeOfDeath = 'Sudden medical emergency';
    }
  }

  return {
    healthDelta,
    mentalDelta,
    stressDelta,
    intelligenceDelta,
    habitAnnualCost,
    nextConditions,
    nextHabits,
    died,
    causeOfDeath,
    healthLogs,
  };
}

export function performDoctorVisit(
  state: GameState,
  pack: EraPack,
  rng: SeededRng,
  conditionId?: string,
): { state: GameState; success: boolean; message: string } {
  const country = getCountry(pack, state.config.countryId);
  const targetCond = conditionId
    ? state.player.conditions.find((c) => c.id === conditionId)
    : state.player.conditions[0];

  if (!targetCond) {
    const checkupCost = Math.round(150 * country.healthcareCostMultiplier);
    if (state.player.finance.cash < checkupCost) {
      return { state, success: false, message: `Not enough cash (${country.currencySymbol}${checkupCost} needed).` };
    }
    return {
      state: {
        ...state,
        player: {
          ...state.player,
          stats: {
            ...state.player.stats,
            physicalHealth: clamp(state.player.stats.physicalHealth + 5, 0, 100),
          },
          finance: {
            ...state.player.finance,
            cash: state.player.finance.cash - checkupCost,
          },
        },
      },
      success: true,
      message: `Preventive checkup complete (+5 Physical Health, -${country.currencySymbol}${checkupCost}).`,
    };
  }

  const def = getConditionDef(pack, targetCond.id);
  const cost = Math.round((def?.treatmentCost ?? 500) * country.healthcareCostMultiplier);
  if (state.player.finance.cash < cost) {
    return { state, success: false, message: `Treatment costs ${country.currencySymbol}${cost}.` };
  }

  const cured = def ? rng.chance(def.cureChance) : true;
  const nextConditions = cured
    ? state.player.conditions.filter((c) => c.id !== targetCond.id)
    : state.player.conditions.map((c) => (c.id === targetCond.id ? { ...c, treated: true } : c));

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        conditions: nextConditions,
        stats: {
          ...state.player.stats,
          physicalHealth: clamp(state.player.stats.physicalHealth + (cured ? 10 : 4), 0, 100),
        },
        finance: {
          ...state.player.finance,
          cash: state.player.finance.cash - cost,
        },
      },
    },
    success: true,
    message: cured
      ? `Successfully treated and cured ${def?.name ?? targetCond.id}!`
      : `${def?.name ?? targetCond.id} is now being managed with treatment.`,
  };
}
