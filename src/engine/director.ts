import type {
  EraPack,
  GameState,
  MacroStateId,
  PlayerStats,
  TurnGranularity,
} from './types';
import { clamp } from './rng';

export type DirectorMutation =
  | { type: 'SET_PLAYER_STAT'; stat: keyof PlayerStats; value: number }
  | { type: 'SET_CASH'; amount: number }
  | { type: 'SET_CREDIT_SCORE'; score: number }
  | { type: 'SET_GRANULARITY'; granularity: TurnGranularity }
  | { type: 'SET_CHAOS_LEVEL'; chaosLevel: number }
  | { type: 'SET_MACRO_STATE'; macroState: MacroStateId }
  | { type: 'SET_NPC_METERS'; npcId: string; affection?: number; trust?: number; respect?: number; tension?: number }
  | { type: 'TRIGGER_EVENT'; eventId: string }
  | { type: 'CURE_ALL_CONDITIONS' };

export interface DirectorResult {
  state: GameState;
  applied: boolean;
  message: string;
}

/**
 * Applies a live Director Mode mutation.
 * Strictly blocked if Ironman mode is enabled (except changing Turn Granularity,
 * which is a pacing preference rather than a cheat).
 */
export function applyDirectorMutation(
  state: GameState,
  mutation: DirectorMutation,
  pack: EraPack,
): DirectorResult {
  if (state.config.ironman && mutation.type !== 'SET_GRANULARITY') {
    return {
      state,
      applied: false,
      message: 'Director Mode is locked in Ironman runs.',
    };
  }

  let nextState = state;
  let logTitle = 'Director Adjustment';
  let logDetail = '';

  switch (mutation.type) {
    case 'SET_PLAYER_STAT': {
      const val = clamp(Math.round(mutation.value), 0, 100);
      nextState = {
        ...state,
        player: {
          ...state.player,
          stats: {
            ...state.player.stats,
            [mutation.stat]: val,
          },
        },
      };
      logDetail = `Set ${mutation.stat} to ${val}.`;
      break;
    }
    case 'SET_CASH': {
      const amt = Math.round(mutation.amount);
      nextState = {
        ...state,
        player: {
          ...state.player,
          finance: { ...state.player.finance, cash: amt },
        },
      };
      logDetail = `Adjusted cash balance to ${amt}.`;
      break;
    }
    case 'SET_CREDIT_SCORE': {
      const score = clamp(Math.round(mutation.score), 300, 850);
      nextState = {
        ...state,
        player: {
          ...state.player,
          finance: { ...state.player.finance, creditScore: score },
        },
      };
      logDetail = `Adjusted credit score to ${score}.`;
      break;
    }
    case 'SET_GRANULARITY': {
      nextState = {
        ...state,
        config: { ...state.config, granularity: mutation.granularity },
      };
      logTitle = 'Turn Speed Updated';
      logDetail = `Switched turn granularity to ${mutation.granularity}.`;
      break;
    }
    case 'SET_CHAOS_LEVEL': {
      const chaosLevel = clamp(Math.round(mutation.chaosLevel), 0, 100);
      nextState = {
        ...state,
        config: { ...state.config, chaosLevel },
      };
      logDetail = `Set World Chaos slider to ${chaosLevel}.`;
      break;
    }
    case 'SET_MACRO_STATE': {
      nextState = {
        ...state,
        world: { ...state.world, macroState: mutation.macroState },
      };
      logDetail = `Shifted global economy to ${mutation.macroState}.`;
      break;
    }
    case 'SET_NPC_METERS': {
      const exists = state.npcs.some((n) => n.id === mutation.npcId);
      if (!exists) return { state, applied: false, message: 'NPC not found.' };
      nextState = {
        ...state,
        npcs: state.npcs.map((n) =>
          n.id === mutation.npcId
            ? {
                ...n,
                affection: mutation.affection !== undefined ? clamp(mutation.affection, 0, 100) : n.affection,
                trust: mutation.trust !== undefined ? clamp(mutation.trust, 0, 100) : n.trust,
                respect: mutation.respect !== undefined ? clamp(mutation.respect, 0, 100) : n.respect,
                tension: mutation.tension !== undefined ? clamp(mutation.tension, 0, 100) : n.tension,
              }
            : n,
        ),
      };
      logDetail = `Updated NPC relationship meters.`;
      break;
    }
    case 'TRIGGER_EVENT': {
      const evt = pack.events.find((e) => e.id === mutation.eventId);
      if (!evt) return { state, applied: false, message: `Event "${mutation.eventId}" not found.` };
      nextState = {
        ...state,
        pendingEvent: evt,
      };
      logDetail = `Triggered event: ${evt.title}.`;
      break;
    }
    case 'CURE_ALL_CONDITIONS': {
      nextState = {
        ...state,
        player: { ...state.player, conditions: [] },
      };
      logDetail = 'Cleared all active medical conditions.';
      break;
    }
  }

  const entry = {
    id: `tl_dir_${nextState.turn}_${nextState.timeline.length}`,
    turn: nextState.turn,
    ageMonths: nextState.player.ageMonths,
    year: nextState.world.currentYear,
    category: 'director' as const,
    title: logTitle,
    detail: logDetail,
  };

  return {
    state: {
      ...nextState,
      timeline: [entry, ...nextState.timeline].slice(0, 250),
    },
    applied: true,
    message: logDetail,
  };
}
