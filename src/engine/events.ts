import type {
  EraPack,
  EventChoice,
  GameEvent,
  GameState,
  SkillId,
} from './types';
import { clamp, SeededRng } from './rng';
import { getAgeYears } from './time';
import { applyStatDeltas } from './schedule';
import { addConditionToPlayer, addHabitToPlayer, removeHabitFromPlayer } from './health';
import { investInAsset } from './career';
import { addNpcMemory, createNpc, spawnChildNpc } from './relationships';

export function isEventEligible(state: GameState, event: GameEvent): boolean {
  if (event.oneShot && state.triggeredOneShots.includes(event.id)) return false;

  const lastTurn = state.eventHistory[event.id];
  if (lastTurn !== undefined && state.turn - lastTurn < event.cooldownTurns) {
    return false;
  }

  const c = event.conditions;
  const ageYears = getAgeYears(state.player.ageMonths);
  const hasPartner = state.npcs.some((n) => n.alive && (n.role === 'partner' || n.role === 'spouse'));

  if (c.minAge !== undefined && ageYears < c.minAge) return false;
  if (c.maxAge !== undefined && ageYears > c.maxAge) return false;
  if (c.employed !== undefined && Boolean(state.player.job) !== c.employed) return false;
  if (c.hasPartner !== undefined && hasPartner !== c.hasPartner) return false;
  if (c.minCash !== undefined && state.player.finance.cash < c.minCash) return false;
  if (c.minStress !== undefined && state.player.stats.stress < c.minStress) return false;
  if (c.minIntelligence !== undefined && state.player.stats.intelligence < c.minIntelligence) return false;
  if (c.minPhysicalHealth !== undefined && state.player.stats.physicalHealth < c.minPhysicalHealth) return false;
  if (c.minChaos !== undefined && state.config.chaosLevel < c.minChaos) return false;
  if (c.requiredFlag !== undefined && !state.player.flags[c.requiredFlag]) return false;

  return true;
}

export function selectTurnEvent(state: GameState, pack: EraPack, rng: SeededRng): GameEvent | null {
  // 1. Check scheduled multi-stage follow-up events first
  const dueFollowUp = state.scheduledFollowUps.find((f) => f.triggerTurn <= state.turn);
  if (dueFollowUp) {
    const found = pack.events.find((e) => e.id === dueFollowUp.eventId);
    if (found && isEventEligible(state, found)) {
      return found;
    }
  }

  // 2. Roll chance to trigger a turn event (lively bi-monthly cadence + chaos scaling)
  const baseEventChance = 0.76 + (state.config.chaosLevel / 100) * 0.16;
  if (!rng.chance(clamp(baseEventChance, 0.5, 0.94))) {
    return null;
  }

  const eligible = pack.events.filter((e) => e.weight > 0 && isEventEligible(state, e));
  if (eligible.length === 0) return null;

  return rng.weightedPick(eligible, (e) => {
    // Slightly boost events that have never appeared yet so turns stay fresh
    const noveltyMultiplier = state.eventHistory[e.id] === undefined ? 1.35 : 0.85;
    if (e.category === 'chaos') {
      return e.weight * noveltyMultiplier * (0.55 + state.config.chaosLevel / 50);
    }
    return e.weight * noveltyMultiplier;
  });
}

export function isChoiceAvailable(state: GameState, choice: EventChoice): boolean {
  const req = choice.requirements;
  if (!req) return true;
  if (req.minCash !== undefined && state.player.finance.cash < req.minCash) return false;
  if (req.minIntelligence !== undefined && state.player.stats.intelligence < req.minIntelligence) return false;
  if (req.minCharisma !== undefined && state.player.stats.charisma < req.minCharisma) return false;
  return true;
}

export function resolveEventChoice(
  state: GameState,
  choiceId: string,
  pack: EraPack,
): GameState {
  const event = state.pendingEvent;
  if (!event) return state;

  const choice = event.choices.find((c) => c.id === choiceId);
  if (!choice) {
    throw new Error(`Choice "${choiceId}" not found on event "${event.id}"`);
  }
  if (!isChoiceAvailable(state, choice)) {
    throw new Error(`Choice "${choiceId}" requirements not met`);
  }

  const rng = new SeededRng(state.rngState);
  const fx = choice.effects;
  let nextState: GameState = {
    ...state,
    pendingEvent: null,
    eventHistory: { ...state.eventHistory, [event.id]: state.turn },
    triggeredOneShots: event.oneShot
      ? Array.from(new Set([...state.triggeredOneShots, event.id]))
      : state.triggeredOneShots,
    scheduledFollowUps: state.scheduledFollowUps.filter((f) => f.eventId !== event.id),
  };

  // Stats
  if (fx.stats) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        stats: applyStatDeltas(nextState.player.stats, fx.stats),
      },
    };
  }

  // Skills
  if (fx.skills) {
    const nextSkills = { ...nextState.player.skills };
    for (const [skill, delta] of Object.entries(fx.skills)) {
      const k = skill as SkillId;
      nextSkills[k] = clamp((nextSkills[k] ?? 0) + (delta ?? 0), 0, 100);
    }
    nextState = {
      ...nextState,
      player: { ...nextState.player, skills: nextSkills },
    };
  }

  // Cash & Credit
  if (fx.cashDelta || fx.creditScoreDelta) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        finance: {
          ...nextState.player.finance,
          cash: nextState.player.finance.cash + (fx.cashDelta ?? 0),
          creditScore: clamp(nextState.player.finance.creditScore + (fx.creditScoreDelta ?? 0), 300, 850),
        },
      },
    };
  }

  // Direct investment from event (already deducted cash if cashDelta < 0; handle cleanly)
  if (fx.investIn) {
    const invested = investInAsset(
      {
        ...nextState,
        player: {
          ...nextState.player,
          finance: {
            ...nextState.player.finance,
            cash: nextState.player.finance.cash + fx.investIn.amount, // offset double deduction if cashDelta already paid
          },
        },
      },
      fx.investIn.assetId,
      fx.investIn.amount,
      pack,
    );
    if (invested.success) {
      nextState = invested.state;
    }
  }

  // GPA
  if (fx.gpaDelta) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        education: {
          ...nextState.player.education,
          gpa: clamp(Number((nextState.player.education.gpa + fx.gpaDelta).toFixed(2)), 0.5, 4.0),
        },
      },
    };
  }

  // Job performance & retirement
  if (fx.jobPerformanceDelta && nextState.player.job) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        job: {
          ...nextState.player.job,
          performance: clamp(nextState.player.job.performance + fx.jobPerformanceDelta, 0, 100),
        },
      },
    };
  }
  if (fx.retire) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        retired: true,
        job: null,
      },
    };
  }

  // Habits & Conditions
  if (fx.addHabit) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        habits: addHabitToPlayer(nextState.player.habits, fx.addHabit, pack),
      },
    };
  }
  if (fx.removeHabit) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        habits: removeHabitFromPlayer(nextState.player.habits, fx.removeHabit),
      },
    };
  }
  if (fx.addCondition) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        conditions: addConditionToPlayer(
          nextState.player.conditions,
          fx.addCondition,
          nextState.player.ageMonths,
          pack,
        ),
      },
    };
  }

  // Housing
  if (fx.setHousing) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        finance: {
          ...nextState.player.finance,
          housingId: fx.setHousing,
          ownsHousing: false,
        },
      },
    };
  }

  // Flags & Macro
  if (fx.setFlag) {
    nextState = {
      ...nextState,
      player: {
        ...nextState.player,
        flags: { ...nextState.player.flags, [fx.setFlag]: true },
      },
    };
  }
  if (fx.setMacro) {
    nextState = {
      ...nextState,
      world: { ...nextState.world, macroState: fx.setMacro },
    };
  }

  // NPC affection & memory tags
  let nextNpcs = [...nextState.npcs];
  if (fx.parentAffection || fx.partnerAffection || fx.allNpcAffection || fx.memoryTag) {
    nextNpcs = nextNpcs.map((npc) => {
      if (!npc.alive) return npc;
      let updated = npc;
      if (fx.parentAffection && npc.role === 'parent') {
        updated = { ...updated, affection: clamp(updated.affection + fx.parentAffection, 0, 100) };
      }
      if (fx.partnerAffection && (npc.role === 'partner' || npc.role === 'spouse')) {
        updated = { ...updated, affection: clamp(updated.affection + fx.partnerAffection, 0, 100) };
      }
      if (fx.allNpcAffection) {
        updated = { ...updated, affection: clamp(updated.affection + fx.allNpcAffection, 0, 100) };
      }
      if (fx.memoryTag && (npc.role === 'partner' || npc.role === 'spouse' || npc.role === 'friend')) {
        updated = addNpcMemory(
          updated,
          fx.memoryTag.tag,
          fx.memoryTag.label,
          fx.memoryTag.sentimentDelta,
          nextState.player.ageMonths,
        );
      }
      return updated;
    });
  }

  if (fx.spawnPartner) {
    const partnerAge = Math.max(14 * 12, nextState.player.ageMonths + rng.nextInt(-24, 24));
    const partner = createNpc(rng, pack, nextState.config.countryId, 'partner', partnerAge);
    nextNpcs.push(partner);
  }
  if (fx.spawnChild) {
    const child = spawnChildNpc(nextState, pack, rng);
    nextNpcs.push(child);
  }

  // Follow-up story arc scheduling
  const nextFollowUps = [...nextState.scheduledFollowUps];
  if (fx.followUp) {
    nextFollowUps.push({
      eventId: fx.followUp.eventId,
      triggerTurn: nextState.turn + fx.followUp.delayTurns,
    });
  }

  // Timeline log
  const entry = {
    id: `tl_evt_${nextState.turn}_${event.id}`,
    turn: nextState.turn,
    ageMonths: nextState.player.ageMonths,
    year: nextState.world.currentYear,
    category: event.category,
    title: event.title,
    detail: `Chose: "${choice.label}"`,
  };

  return {
    ...nextState,
    rngState: rng.getState(),
    npcs: nextNpcs,
    scheduledFollowUps: nextFollowUps,
    timeline: [entry, ...nextState.timeline].slice(0, 250),
  };
}
