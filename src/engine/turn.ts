import type {
  ActionResult,
  DeathSummary,
  EraPack,
  GameState,
  MacroStateId,
  PlayerAction,
  TimelineEntry,
} from './types';
import { clamp, SeededRng } from './rng';
import { getCountry, loadEraPack } from './eraLoader';
import {
  advanceCalendar,
  computeTurnActionPoints,
  getAgeYears,
  getLifeStage,
  getMonthsPerTurn,
  scaleAnnualProbability,
  scaleCompoundFactor,
} from './time';
import {
  applyStatDeltas,
  evaluateScheduleTick,
  getDefaultScheduleForAge,
} from './schedule';
import {
  addHabitToPlayer,
  performDoctorVisit,
  removeHabitFromPlayer,
  tickHealthAndHabits,
} from './health';
import {
  applyForJob,
  calculateNetWorth,
  enrollInEducation,
  getRequiredWorkOrSchoolHours,
  investInAsset,
  requestPromotion,
  sellInvestment,
  tickEducationCareerAndFinance,
} from './career';
import {
  interactWithNpc,
  setChildParentingStyle,
  tickNpcs,
} from './relationships';
import { selectTurnEvent } from './events';

export function buildDeathSummary(state: GameState, causeOfDeath: string, pack: EraPack): DeathSummary {
  const finalNetWorth = calculateNetWorth(state, pack);
  const peakNetWorth = Math.max(state.peakNetWorth, finalNetWorth);
  const childrenCount = state.npcs.filter((n) => n.role === 'child').length;
  const ageYears = getAgeYears(state.player.ageMonths);

  const avgRelationship =
    state.npcs.length > 0
      ? state.npcs.reduce((sum, n) => sum + n.affection, 0) / state.npcs.length
      : 50;

  const wealthPoints = Math.min(350, Math.round(Math.log10(Math.max(10, peakNetWorth)) * 50));
  const legacyScore = Math.max(
    10,
    Math.round(ageYears * 4 + wealthPoints + childrenCount * 45 + avgRelationship * 2),
  );

  return {
    ageMonths: state.player.ageMonths,
    causeOfDeath,
    finalNetWorth,
    peakNetWorth,
    highestJobTitle: state.player.highestJobTitle,
    childrenCount,
    legacyScore,
  };
}

/**
 * Master pure simulation step: advances the world by 1 turn (`monthsPerTurn` months).
 * Blocked if the player is dead or has an unresolved pendingEvent.
 */
export function advanceTurn(state: GameState, eraPack?: EraPack): GameState {
  if (!state.player.alive) return state;
  if (state.pendingEvent) return state;

  const pack = eraPack ?? loadEraPack(state.config.eraId);
  const rng = new SeededRng(state.rngState);
  const monthsToAdd = getMonthsPerTurn(state.config.granularity);

  const prevStage = getLifeStage(state.player.ageMonths);
  const nextAgeMonths = state.player.ageMonths + monthsToAdd;
  const nextStage = getLifeStage(nextAgeMonths);
  const nextTurn = state.turn + 1;
  const nextCal = advanceCalendar(state.world.currentYear, state.world.currentMonth, monthsToAdd);

  const newLogs: TimelineEntry[] = [];
  const pushLog = (
    category: TimelineEntry['category'],
    title: string,
    detail: string,
  ) => {
    newLogs.push({
      id: `tl_${nextTurn}_${newLogs.length}`,
      turn: nextTurn,
      ageMonths: nextAgeMonths,
      year: nextCal.year,
      category,
      title,
      detail,
    });
  };

  // Auto-adapt default schedule on major life-stage transitions (infancy -> childhood -> adolescence)
  let currentSchedule = state.player.schedule;
  if (
    (prevStage === 'infancy' && nextStage === 'childhood') ||
    (prevStage === 'childhood' && nextStage === 'adolescence')
  ) {
    currentSchedule = getDefaultScheduleForAge(nextAgeMonths);
  }

  // 1. World macro-economy tick (gentle stochastic transitions + inflation compounding)
  let nextMacro: MacroStateId = state.world.macroState;
  const macroShiftChance = scaleAnnualProbability(
    0.08 + (state.config.economyVolatility / 100) * 0.14,
    state.config.granularity,
  );
  if (rng.chance(macroShiftChance)) {
    const candidates: MacroStateId[] =
      nextMacro === 'normal'
        ? ['boom', 'recession']
        : ['normal', nextMacro === 'boom' ? 'recession' : 'boom'];
    nextMacro = rng.pick(candidates);
    pushLog(
      'world',
      `Economy Shifted: ${pack.meta.macroStates[nextMacro].label}`,
      `Global markets entered a ${pack.meta.macroStates[nextMacro].label.toLowerCase()} phase.`,
    );
  }
  const macroDef = pack.meta.macroStates[nextMacro];
  const nextInflationIndex = Number(
    (
      state.world.inflationIndex *
      scaleCompoundFactor(macroDef.annualInflation, state.config.granularity)
    ).toFixed(4),
  );

  // 2. 168-Hour Weekly Schedule tick
  const requiredHours = getRequiredWorkOrSchoolHours(state, pack);
  const schedTick = evaluateScheduleTick(
    currentSchedule,
    state.config.granularity,
    requiredHours,
    state.player.hidden.burnout,
  );

  // 3. Health, Genetics, Habits & Mortality tick
  const healthTick = tickHealthAndHabits(
    {
      ...state,
      player: { ...state.player, ageMonths: nextAgeMonths },
    },
    pack,
    rng,
  );
  for (const hLog of healthTick.healthLogs) {
    pushLog('health', hLog.title, hLog.detail);
  }

  // 4. Education, Career & Finance tick
  const careerTick = tickEducationCareerAndFinance(
    {
      ...state,
      world: {
        currentYear: nextCal.year,
        currentMonth: nextCal.month,
        macroState: nextMacro,
        inflationIndex: nextInflationIndex,
      },
      player: { ...state.player, ageMonths: nextAgeMonths },
    },
    pack,
    rng,
    schedTick.overtimeHours,
    healthTick.habitAnnualCost,
  );
  for (const cLog of careerTick.logs) {
    pushLog(cLog.category, cLog.title, cLog.detail);
  }

  // 5. NPCs tick (aging, memory decay, passive affection, mortality)
  const npcTick = tickNpcs(state, rng, schedTick.passiveNpcAffectionDelta);
  for (const nLog of npcTick.logs) {
    pushLog('relationship', nLog.title, nLog.detail);
  }

  // 6. Merge all stat & skill deltas
  const mergedStats = applyStatDeltas(state.player.stats, {
    physicalHealth: (schedTick.statDeltas.physicalHealth ?? 0) + healthTick.healthDelta,
    mentalHealth: (schedTick.statDeltas.mentalHealth ?? 0) + healthTick.mentalDelta + npcTick.mentalDelta,
    happiness: (schedTick.statDeltas.happiness ?? 0) + careerTick.happinessDelta + npcTick.happinessDelta,
    energy: schedTick.statDeltas.energy ?? 0,
    stress: (schedTick.statDeltas.stress ?? 0) + healthTick.stressDelta + careerTick.stressDelta,
    looks: schedTick.statDeltas.looks ?? 0,
    intelligence: (schedTick.statDeltas.intelligence ?? 0) + healthTick.intelligenceDelta,
    discipline: schedTick.statDeltas.discipline ?? 0,
  });

  // Cap intelligence gently by DNA intellect ceiling (can still exceed slightly with hard work)
  mergedStats.intelligence = clamp(
    mergedStats.intelligence,
    0,
    Math.min(100, state.player.dna.intellectCeiling + 12),
  );

  const nextHidden = {
    ...state.player.hidden,
    sleepDebt: clamp(state.player.hidden.sleepDebt + schedTick.sleepDebtDelta, 0, 100),
    burnout: clamp(state.player.hidden.burnout + schedTick.burnoutDelta, 0, 100),
  };

  const nextSkills = { ...state.player.skills };
  nextSkills[state.player.focusSkill] = clamp(
    (nextSkills[state.player.focusSkill] ?? 0) + schedTick.focusSkillXp,
    0,
    100,
  );
  for (const [k, delta] of Object.entries(careerTick.skillDeltas)) {
    const skillKey = k as keyof typeof nextSkills;
    nextSkills[skillKey] = clamp((nextSkills[skillKey] ?? 0) + (delta ?? 0), 0, 100);
  }

  const nextMaxAp = computeTurnActionPoints(
    state.config.granularity,
    mergedStats.energy,
    nextHidden.burnout,
  );

  let nextState: GameState = {
    ...state,
    turn: nextTurn,
    rngState: rng.getState(),
    world: {
      currentYear: nextCal.year,
      currentMonth: nextCal.month,
      macroState: nextMacro,
      inflationIndex: nextInflationIndex,
    },
    player: {
      ...state.player,
      ageMonths: nextAgeMonths,
      alive: !healthTick.died,
      stats: mergedStats,
      hidden: nextHidden,
      skills: nextSkills,
      schedule: currentSchedule,
      actionPoints: nextMaxAp,
      maxActionPoints: nextMaxAp,
      conditions: healthTick.nextConditions,
      habits: healthTick.nextHabits,
      education: careerTick.nextEducation,
      job: careerTick.nextJob,
      finance: careerTick.nextFinance,
    },
    npcs: npcTick.nextNpcs,
    timeline: [...newLogs, ...state.timeline].slice(0, 250),
  };

  // Track Net Worth history
  const netWorth = calculateNetWorth(nextState, pack);
  const nextPeak = Math.max(nextState.peakNetWorth, netWorth);
  nextState = {
    ...nextState,
    peakNetWorth: nextPeak,
    player: {
      ...nextState.player,
      finance: {
        ...nextState.player.finance,
        netWorthHistory: [
          ...nextState.player.finance.netWorthHistory.slice(-120),
          { ageMonths: nextAgeMonths, netWorth },
        ],
      },
    },
  };

  // 7. Handle Death or Roll Turn Event
  if (healthTick.died) {
    const cause = healthTick.causeOfDeath ?? 'Natural causes';
    const summary = buildDeathSummary(nextState, cause, pack);
    const deathLog: TimelineEntry = {
      id: `tl_death_${nextTurn}`,
      turn: nextTurn,
      ageMonths: nextAgeMonths,
      year: nextCal.year,
      category: 'death',
      title: `Passed Away at Age ${getAgeYears(nextAgeMonths)}`,
      detail: `Cause: ${cause}. Legacy Score: ${summary.legacyScore}.`,
    };
    return {
      ...nextState,
      deathSummary: summary,
      pendingEvent: null,
      timeline: [deathLog, ...nextState.timeline].slice(0, 250),
    };
  }

  const event = selectTurnEvent(nextState, pack, rng);
  return {
    ...nextState,
    rngState: rng.getState(),
    pendingEvent: event,
  };
}

/**
 * Executes a discrete player action during the current turn, deducting Action Points (AP).
 */
export function performPlayerAction(
  state: GameState,
  action: PlayerAction,
  eraPack?: EraPack,
): ActionResult {
  if (!state.player.alive) {
    return { state, success: false, message: 'Cannot take actions after death.' };
  }
  if (state.pendingEvent) {
    return { state, success: false, message: 'Resolve the pending event first.' };
  }

  const apCost =
    action.type === 'SET_PARENTING_STYLE' || action.type === 'QUIT_JOB' || action.type === 'RETIRE'
      ? 0
      : action.type === 'DOCTOR_VISIT' || action.type === 'THERAPY_SESSION' || action.type === 'CHANGE_HOUSING'
        ? 2
        : 1;

  if (state.player.actionPoints < apCost) {
    return { state, success: false, message: `Not enough Action Points (need ${apCost} AP).` };
  }

  const pack = eraPack ?? loadEraPack(state.config.eraId);
  const country = getCountry(pack, state.config.countryId);
  const ageYears = getAgeYears(state.player.ageMonths);
  const rng = new SeededRng(state.rngState);
  let res: { state: GameState; success: boolean; message: string };

  switch (action.type) {
    case 'ENROLL_EDUCATION':
      res = enrollInEducation(state, action.programId, pack);
      break;
    case 'APPLY_JOB':
      res = applyForJob(state, action.trackId, action.tierId, pack, rng);
      break;
    case 'REQUEST_PROMOTION':
      res = requestPromotion(state, pack, rng);
      break;
    case 'QUIT_JOB':
      if (!state.player.job) {
        res = { state, success: false, message: 'You have no job to quit.' };
      } else {
        res = {
          state: { ...state, player: { ...state.player, job: null } },
          success: true,
          message: `Resigned from ${state.player.job.title}.`,
        };
      }
      break;
    case 'RETIRE':
      if (ageYears < country.legalAdultAge) {
        res = { state, success: false, message: 'You are too young to retire.' };
      } else {
        res = {
          state: { ...state, player: { ...state.player, retired: true, job: null } },
          success: true,
          message: 'You have officially retired.',
        };
      }
      break;
    case 'INVEST':
      if (ageYears < country.legalAdultAge) {
        res = {
          state,
          success: false,
          message: `You must be at least ${country.legalAdultAge} to open an investment account.`,
        };
      } else {
        res = investInAsset(state, action.assetId, action.amount, pack);
      }
      break;
    case 'SELL_INVESTMENT':
      res = sellInvestment(state, action.assetId, action.amount);
      break;
    case 'CHANGE_HOUSING': {
      const h = pack.assets.housing.find((x) => x.id === action.housingId);
      if (!h) {
        res = { state, success: false, message: 'Housing option not found.' };
        break;
      }
      if (h.id !== 'parents_home' && ageYears < country.legalAdultAge) {
        res = {
          state,
          success: false,
          message: `You must be at least ${country.legalAdultAge} to move out or sign a lease/mortgage.`,
        };
        break;
      }

      // If player currently owns a home, realize net equity upon selling it
      let saleProceeds = 0;
      if (state.player.finance.ownsHousing) {
        const currentHome = pack.assets.housing.find((x) => x.id === state.player.finance.housingId);
        const currentHomeValue = currentHome
          ? Math.round(currentHome.purchasePrice * country.costOfLivingMultiplier)
          : 0;
        saleProceeds = currentHomeValue - state.player.finance.mortgageBalance;
      }
      const cashAfterSale = state.player.finance.cash + saleProceeds;

      if (action.buy && h.purchasePrice > 0) {
        if (state.player.finance.housingId === h.id && state.player.finance.ownsHousing) {
          res = { state, success: false, message: `You already own ${h.name}.` };
          break;
        }
        const price = Math.round(h.purchasePrice * country.costOfLivingMultiplier);
        const downPayment = Math.round(price * 0.2);
        if (cashAfterSale < downPayment) {
          res = { state, success: false, message: `Need ${country.currencySymbol}${downPayment} for 20% down payment.` };
          break;
        }
        res = {
          state: {
            ...state,
            player: {
              ...state.player,
              finance: {
                ...state.player.finance,
                cash: cashAfterSale - downPayment,
                housingId: h.id,
                ownsHousing: true,
                mortgageBalance: price - downPayment,
              },
            },
          },
          success: true,
          message:
            saleProceeds !== 0
              ? `Sold previous home and purchased ${h.name}!`
              : `Purchased ${h.name}!`,
        };
      } else {
        res = {
          state: {
            ...state,
            player: {
              ...state.player,
              finance: {
                ...state.player.finance,
                cash: cashAfterSale,
                housingId: h.id,
                ownsHousing: false,
                mortgageBalance: 0,
              },
            },
          },
          success: true,
          message:
            saleProceeds !== 0
              ? `Sold previous home and moved into ${h.name}.`
              : `Moved into ${h.name}.`,
        };
      }
      break;
    }
    case 'INTERACT_NPC':
      res = interactWithNpc(state, action.npcId, action.interaction, rng, pack);
      break;
    case 'SET_PARENTING_STYLE':
      res = setChildParentingStyle(state, action.npcId, action.style);
      break;
    case 'DOCTOR_VISIT':
      res = performDoctorVisit(state, pack, rng, action.conditionId);
      break;
    case 'THERAPY_SESSION': {
      const country = getCountry(pack, state.config.countryId);
      const cost = Math.round(180 * country.healthcareCostMultiplier);
      if (state.player.finance.cash < cost) {
        res = { state, success: false, message: `Therapy costs ${country.currencySymbol}${cost}.` };
        break;
      }
      res = {
        state: {
          ...state,
          player: {
            ...state.player,
            stats: applyStatDeltas(state.player.stats, { mentalHealth: 8, stress: -10, happiness: 4 }),
            hidden: { ...state.player.hidden, burnout: clamp(state.player.hidden.burnout - 8, 0, 100) },
            finance: { ...state.player.finance, cash: state.player.finance.cash - cost },
          },
        },
        success: true,
        message: `Completed therapy session (+8 Mental Health, -10 Stress).`,
      };
      break;
    }
    case 'ADOPT_HABIT': {
      const adultOnlyHabits = new Set(['smoking', 'heavy_drinking', 'gambling']);
      if (adultOnlyHabits.has(action.habitId) && ageYears < country.legalAdultAge) {
        res = {
          state,
          success: false,
          message: `Must be ${country.legalAdultAge}+ to adopt this habit.`,
        };
        break;
      }
      const nextHabits = addHabitToPlayer(state.player.habits, action.habitId, pack);
      if (nextHabits.length === state.player.habits.length) {
        res = { state, success: false, message: 'Habit already active or unknown.' };
      } else {
        res = {
          state: { ...state, player: { ...state.player, habits: nextHabits } },
          success: true,
          message: `Adopted new habit.`,
        };
      }
      break;
    }
    case 'QUIT_HABIT': {
      const nextHabits = removeHabitFromPlayer(state.player.habits, action.habitId);
      if (nextHabits.length === state.player.habits.length) {
        res = { state, success: false, message: 'Habit not active.' };
      } else {
        res = {
          state: {
            ...state,
            player: {
              ...state.player,
              habits: nextHabits,
              stats: applyStatDeltas(state.player.stats, { discipline: 3, stress: 4 }),
            },
          },
          success: true,
          message: `Worked hard to quit the habit (+3 Discipline).`,
        };
      }
      break;
    }
  }

  if (!res.success) {
    return { ...res, state: { ...res.state, rngState: rng.getState() } };
  }

  return {
    success: true,
    message: res.message,
    state: {
      ...res.state,
      rngState: rng.getState(),
      player: {
        ...res.state.player,
        actionPoints: Math.max(0, res.state.player.actionPoints - apCost),
      },
    },
  };
}
