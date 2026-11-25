import type {
  GameState,
  PlayerStats,
  SchedulePresetId,
  TurnGranularity,
  WeeklySchedule,
} from './types';
import { clamp } from './rng';
import { getDt, getLifeStage } from './time';

export const HOURS_PER_WEEK = 168;

export const SCHEDULE_PRESETS: Record<SchedulePresetId, WeeklySchedule> = {
  balanced: {
    sleep: 56,
    workOrSchool: 40,
    hustleAndSkills: 10,
    fitnessAndSelfCare: 8,
    familyAndSocial: 24,
    leisureAndRest: 30,
  },
  hustle: {
    sleep: 46,
    workOrSchool: 50,
    hustleAndSkills: 32,
    fitnessAndSelfCare: 6,
    familyAndSocial: 14,
    leisureAndRest: 20,
  },
  family_first: {
    sleep: 56,
    workOrSchool: 36,
    hustleAndSkills: 6,
    fitnessAndSelfCare: 8,
    familyAndSocial: 42,
    leisureAndRest: 20,
  },
  health_kick: {
    sleep: 60,
    workOrSchool: 38,
    hustleAndSkills: 10,
    fitnessAndSelfCare: 20,
    familyAndSocial: 18,
    leisureAndRest: 22,
  },
};

export function getDefaultScheduleForAge(ageMonths: number): WeeklySchedule {
  const stage = getLifeStage(ageMonths);
  if (stage === 'infancy') {
    return {
      sleep: 84,
      workOrSchool: 0,
      hustleAndSkills: 0,
      fitnessAndSelfCare: 8,
      familyAndSocial: 46,
      leisureAndRest: 30,
    };
  }
  if (stage === 'childhood') {
    return {
      sleep: 68,
      workOrSchool: 28,
      hustleAndSkills: 8,
      fitnessAndSelfCare: 10,
      familyAndSocial: 28,
      leisureAndRest: 26,
    };
  }
  if (stage === 'elder') {
    return {
      sleep: 58,
      workOrSchool: 0,
      hustleAndSkills: 10,
      fitnessAndSelfCare: 14,
      familyAndSocial: 40,
      leisureAndRest: 46,
    };
  }
  return { ...SCHEDULE_PRESETS.balanced };
}

export function sumScheduleHours(schedule: WeeklySchedule): number {
  return (
    schedule.sleep +
    schedule.workOrSchool +
    schedule.hustleAndSkills +
    schedule.fitnessAndSelfCare +
    schedule.familyAndSocial +
    schedule.leisureAndRest
  );
}

/**
 * Normalizes any schedule so all buckets are non-negative integers summing to 168h.
 */
export function normalizeSchedule(input: WeeklySchedule): WeeklySchedule {
  const keys: Array<keyof WeeklySchedule> = [
    'sleep',
    'workOrSchool',
    'hustleAndSkills',
    'fitnessAndSelfCare',
    'familyAndSocial',
    'leisureAndRest',
  ];
  const cleaned: WeeklySchedule = {
    sleep: Math.max(21, Math.round(input.sleep)), // minimum 3h/night physiological floor
    workOrSchool: Math.max(0, Math.round(input.workOrSchool)),
    hustleAndSkills: Math.max(0, Math.round(input.hustleAndSkills)),
    fitnessAndSelfCare: Math.max(0, Math.round(input.fitnessAndSelfCare)),
    familyAndSocial: Math.max(0, Math.round(input.familyAndSocial)),
    leisureAndRest: Math.max(0, Math.round(input.leisureAndRest)),
  };

  let total = sumScheduleHours(cleaned);
  if (total === HOURS_PER_WEEK) return cleaned;

  const diff = HOURS_PER_WEEK - total;
  if (diff > 0) {
    cleaned.leisureAndRest += diff;
    return cleaned;
  }

  // Need to trim excess hours from leisure -> hustle -> family -> fitness -> work -> sleep
  let excess = -diff;
  const trimOrder: Array<keyof WeeklySchedule> = [
    'leisureAndRest',
    'hustleAndSkills',
    'familyAndSocial',
    'fitnessAndSelfCare',
    'workOrSchool',
    'sleep',
  ];
  for (const key of trimOrder) {
    if (excess <= 0) break;
    const floor = key === 'sleep' ? 21 : 0;
    const available = cleaned[key] - floor;
    const take = Math.min(available, excess);
    cleaned[key] -= take;
    excess -= take;
  }

  // Final guarantee
  total = keys.reduce((acc, k) => acc + cleaned[k], 0);
  if (total !== HOURS_PER_WEEK) {
    cleaned.leisureAndRest = Math.max(0, cleaned.leisureAndRest + (HOURS_PER_WEEK - total));
  }
  return cleaned;
}

export interface ScheduleTickResult {
  statDeltas: Partial<PlayerStats>;
  sleepDebtDelta: number;
  burnoutDelta: number;
  focusSkillXp: number;
  passiveNpcAffectionDelta: number;
  overtimeHours: number;
}

/**
 * Evaluates the continuous effects of the player's 168-hour weekly schedule over 1 turn.
 * All rates are annual and scaled by dt = monthsPerTurn / 12.
 */
export function evaluateScheduleTick(
  schedule: WeeklySchedule,
  granularity: TurnGranularity,
  requiredWorkOrSchoolHours: number,
  currentBurnout: number,
): ScheduleTickResult {
  const dt = getDt(granularity);
  const s = normalizeSchedule(schedule);

  let physicalHealth = 0;
  let mentalHealth = 0;
  let happiness = 0;
  let energy = 0;
  let stress = 0;
  let looks = 0;
  let intelligence = 0;
  let discipline = 0;
  let sleepDebtDelta = 0;
  let burnoutDelta = 0;

  // 1. Sleep effects (49h = 7h/night healthy threshold, 56h = 8h/night optimal)
  if (s.sleep < 49) {
    const deficit = 49 - s.sleep; // hours short per week
    sleepDebtDelta += deficit * 1.8 * dt;
    stress += deficit * 1.4 * dt;
    physicalHealth -= deficit * 0.7 * dt;
    mentalHealth -= deficit * 0.9 * dt;
    energy -= deficit * 1.6 * dt;
  } else {
    const recovery = Math.min(14, s.sleep - 47);
    sleepDebtDelta -= recovery * 2.2 * dt;
    energy += recovery * 1.2 * dt;
    physicalHealth += recovery * 0.25 * dt;
  }

  // 2. Work/School + Overtime
  const overtimeHours = Math.max(0, s.workOrSchool - requiredWorkOrSchoolHours);
  const totalGrind = s.workOrSchool + s.hustleAndSkills;
  if (totalGrind > 50) {
    const grindExcess = totalGrind - 50;
    stress += grindExcess * 0.95 * dt;
    burnoutDelta += grindExcess * 0.75 * dt;
    discipline += Math.min(8, grindExcess * 0.25) * dt;
  } else {
    burnoutDelta -= 6 * dt;
  }

  // 3. Hustle & Skills
  const focusSkillXp = s.hustleAndSkills * 0.85 * dt;
  if (s.hustleAndSkills >= 10) {
    intelligence += Math.min(4, s.hustleAndSkills * 0.12) * dt;
  }

  // 4. Fitness & Self-Care
  if (s.fitnessAndSelfCare >= 5) {
    const fitBonus = Math.min(18, s.fitnessAndSelfCare - 3);
    physicalHealth += fitBonus * 0.75 * dt;
    mentalHealth += fitBonus * 0.45 * dt;
    looks += fitBonus * 0.35 * dt;
    stress -= fitBonus * 0.65 * dt;
  } else if (s.fitnessAndSelfCare < 3) {
    physicalHealth -= 4 * dt;
  }

  // 5. Leisure & Rest
  if (s.leisureAndRest >= 18) {
    const restBonus = Math.min(25, s.leisureAndRest - 14);
    stress -= restBonus * 0.85 * dt;
    happiness += restBonus * 0.55 * dt;
    burnoutDelta -= restBonus * 0.6 * dt;
  } else if (s.leisureAndRest < 10) {
    const deprivation = 10 - s.leisureAndRest;
    happiness -= deprivation * 1.4 * dt;
    stress += deprivation * 1.1 * dt;
    burnoutDelta += deprivation * 1.2 * dt;
  }

  // 6. Family & Social passive relationship maintenance
  let passiveNpcAffectionDelta = 0;
  if (s.familyAndSocial >= 18) {
    passiveNpcAffectionDelta = Math.min(12, (s.familyAndSocial - 14) * 0.4) * dt;
    happiness += 3 * dt;
    mentalHealth += 3 * dt;
  } else if (s.familyAndSocial < 10) {
    passiveNpcAffectionDelta = -(10 - s.familyAndSocial) * 0.8 * dt;
    mentalHealth -= 4 * dt;
  }

  // High burnout amplifies mental/happiness drag
  if (currentBurnout >= 65) {
    mentalHealth -= 8 * dt;
    happiness -= 8 * dt;
    energy -= 10 * dt;
  }

  return {
    statDeltas: {
      physicalHealth,
      mentalHealth,
      happiness,
      energy,
      stress,
      looks,
      intelligence,
      discipline,
    },
    sleepDebtDelta,
    burnoutDelta,
    focusSkillXp,
    passiveNpcAffectionDelta,
    overtimeHours,
  };
}

export function applySchedulePreset(state: GameState, preset: SchedulePresetId): GameState {
  const nextSchedule = normalizeSchedule(SCHEDULE_PRESETS[preset]);
  return {
    ...state,
    player: {
      ...state.player,
      schedule: nextSchedule,
    },
  };
}

export function updateSchedule(state: GameState, schedule: WeeklySchedule): GameState {
  return {
    ...state,
    player: {
      ...state.player,
      schedule: normalizeSchedule(schedule),
    },
  };
}

export function applyStatDeltas(stats: PlayerStats, deltas: Partial<PlayerStats>): PlayerStats {
  return {
    physicalHealth: clamp(stats.physicalHealth + (deltas.physicalHealth ?? 0), 0, 100),
    mentalHealth: clamp(stats.mentalHealth + (deltas.mentalHealth ?? 0), 0, 100),
    happiness: clamp(stats.happiness + (deltas.happiness ?? 0), 0, 100),
    energy: clamp(stats.energy + (deltas.energy ?? 0), 0, 100),
    stress: clamp(stats.stress + (deltas.stress ?? 0), 0, 100),
    looks: clamp(stats.looks + (deltas.looks ?? 0), 0, 100),
    intelligence: clamp(stats.intelligence + (deltas.intelligence ?? 0), 0, 100),
    charisma: clamp(stats.charisma + (deltas.charisma ?? 0), 0, 100),
    discipline: clamp(stats.discipline + (deltas.discipline ?? 0), 0, 100),
  };
}
