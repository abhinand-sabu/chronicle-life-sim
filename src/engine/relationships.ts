import type {
  DnaProfile,
  EraPack,
  GameState,
  Gender,
  Npc,
  NpcMemory,
  NpcRole,
  ParentingStyle,
} from './types';
import { clamp, SeededRng } from './rng';
import { getAgeYears, getDt, getMonthsPerTurn, scaleAnnualProbability } from './time';
import { getCountry, loadEraPack } from './eraLoader';
import { inheritDna } from './health';

export function pickNameForCountry(
  rng: SeededRng,
  pack: EraPack,
  countryId: string,
  gender: Gender,
): { firstName: string; lastName: string } {
  const pool = pack.names[countryId] ?? pack.names[pack.meta.defaultCountryId];
  if (!pool) return { firstName: 'Alex', lastName: 'Mercer' };
  const firstPool =
    gender === 'male' ? pool.male : gender === 'female' ? pool.female : pool.neutral;
  return {
    firstName: rng.pick(firstPool),
    lastName: rng.pick(pool.last),
  };
}

export function createNpc(
  rng: SeededRng,
  pack: EraPack,
  countryId: string,
  role: NpcRole,
  ageMonths: number,
  options?: { lastName?: string; gender?: Gender; dna?: DnaProfile; wealth?: number },
): Npc {
  const gender: Gender = options?.gender ?? rng.pick(['male', 'female', 'non_binary']);
  const generatedName = pickNameForCountry(rng, pack, countryId, gender);
  const id = `npc_${role}_${rng.nextInt(100000, 999999)}`;

  const baseAffection =
    role === 'parent' || role === 'child'
      ? rng.nextInt(72, 92)
      : role === 'partner' || role === 'spouse'
        ? rng.nextInt(68, 86)
        : role === 'sibling' || role === 'friend'
          ? rng.nextInt(58, 80)
          : rng.nextInt(42, 65);

  return {
    id,
    firstName: generatedName.firstName,
    lastName: options?.lastName ?? generatedName.lastName,
    gender,
    role,
    ageMonths,
    alive: true,
    health: clamp(rng.nextInt(65, 95) - Math.max(0, getAgeYears(ageMonths) - 50) * 0.5, 25, 100),
    wealth: options?.wealth ?? rng.nextInt(5000, 80000),
    personality: {
      warmth: rng.nextInt(25, 95),
      ambition: rng.nextInt(20, 95),
      loyalty: rng.nextInt(30, 98),
      volatility: rng.nextInt(10, 85),
      honesty: rng.nextInt(30, 95),
    },
    affection: baseAffection,
    trust: clamp(baseAffection - rng.nextInt(-5, 8), 20, 100),
    respect: clamp(baseAffection - rng.nextInt(-5, 12), 20, 100),
    tension: rng.nextInt(0, 18),
    memories: [],
    parentingStyle: role === 'child' ? 'authoritative' : undefined,
    dna: options?.dna,
  };
}

export function addNpcMemory(
  npc: Npc,
  tag: string,
  label: string,
  sentimentDelta: number,
  ageMonths: number,
  decayRate = 0.12,
): Npc {
  const memory: NpcMemory = {
    id: `mem_${tag}_${ageMonths}_${npc.memories.length}`,
    tag,
    label,
    sentimentDelta,
    ageMonths,
    decayRate,
  };
  const nextMemories = [memory, ...npc.memories].slice(0, 20);
  return {
    ...npc,
    memories: nextMemories,
    affection: clamp(npc.affection + sentimentDelta * 0.5, 0, 100),
    trust: clamp(npc.trust + sentimentDelta * 0.35, 0, 100),
    tension: clamp(npc.tension + (sentimentDelta < 0 ? -sentimentDelta * 0.6 : -sentimentDelta * 0.2), 0, 100),
  };
}

export function getNpcMemorySentiment(npc: Npc): number {
  return npc.memories.reduce((sum, m) => sum + m.sentimentDelta, 0);
}

export interface NpcTickOutput {
  nextNpcs: Npc[];
  happinessDelta: number;
  mentalDelta: number;
  logs: Array<{ title: string; detail: string }>;
}

export function tickNpcs(
  state: GameState,
  rng: SeededRng,
  passiveAffectionDelta: number,
): NpcTickOutput {
  const dt = getDt(state.config.granularity);
  const monthsToAdd = getMonthsPerTurn(state.config.granularity);
  const logs: Array<{ title: string; detail: string }> = [];
  let happinessDelta = 0;
  let mentalDelta = 0;

  const nextNpcs = state.npcs.map((npc) => {
    if (!npc.alive) return npc;

    const nextAgeMonths = npc.ageMonths + monthsToAdd;
    const ageYears = getAgeYears(nextAgeMonths);

    // Decay old memories gently over time
    const nextMemories = npc.memories
      .map((m) => ({
        ...m,
        sentimentDelta: Number((m.sentimentDelta * Math.pow(1 - m.decayRate, dt)).toFixed(2)),
      }))
      .filter((m) => Math.abs(m.sentimentDelta) >= 0.5);

    // Passive relationship drift from player's weekly social hours + lingering memories
    const memoryInfluence = clamp(getNpcMemorySentiment({ ...npc, memories: nextMemories }) * 0.08, -6, 6) * dt;
    const nextAffection = clamp(npc.affection + passiveAffectionDelta + memoryInfluence, 0, 100);
    const nextTension = clamp(npc.tension - 2 * dt + (passiveAffectionDelta < 0 ? 2 * dt : 0), 0, 100);

    // NPC aging & mortality
    let nextHealth = npc.health;
    if (ageYears >= 55) {
      nextHealth = clamp(nextHealth - (ageYears - 50) * 0.25 * dt, 0, 100);
    }
    const annualNpcDeathRisk =
      ageYears < 60 ? 0.001 : clamp(0.004 * Math.pow(1.11, ageYears - 60) + (nextHealth < 20 ? 0.15 : 0), 0, 0.7);

    if (nextHealth <= 0 || rng.chance(scaleAnnualProbability(annualNpcDeathRisk, state.config.granularity))) {
      logs.push({
        title: `${npc.firstName} ${npc.lastName} Passed Away`,
        detail: `Your ${npc.role}, ${npc.firstName}, passed away at age ${ageYears}.`,
      });
      happinessDelta -= npc.affection >= 60 ? 18 : 8;
      mentalDelta -= npc.affection >= 60 ? 12 : 4;
      return {
        ...npc,
        ageMonths: nextAgeMonths,
        health: 0,
        alive: false,
        memories: nextMemories,
      };
    }

    return {
      ...npc,
      ageMonths: nextAgeMonths,
      health: nextHealth,
      affection: nextAffection,
      tension: nextTension,
      memories: nextMemories,
    };
  });

  return { nextNpcs, happinessDelta, mentalDelta, logs };
}

export function interactWithNpc(
  state: GameState,
  npcId: string,
  interaction: 'talk' | 'gift' | 'date' | 'propose' | 'apologize' | 'argue' | 'breakup',
  rng: SeededRng,
  eraPack?: EraPack,
): { state: GameState; success: boolean; message: string } {
  const target = state.npcs.find((n) => n.id === npcId && n.alive);
  if (!target) return { state, success: false, message: 'Person not found.' };

  const pack = eraPack ?? loadEraPack(state.config.eraId);
  const country = getCountry(pack, state.config.countryId);
  const playerAgeYears = getAgeYears(state.player.ageMonths);
  const targetAgeYears = getAgeYears(target.ageMonths);

  let cashDelta = 0;
  let happinessDelta = 0;
  let stressDelta = 0;
  let updatedNpc: Npc = { ...target };
  let message = '';

  switch (interaction) {
    case 'talk': {
      const gain = Math.round(4 + (state.player.stats.charisma + target.personality.warmth) / 40);
      updatedNpc.affection = clamp(updatedNpc.affection + gain, 0, 100);
      updatedNpc.trust = clamp(updatedNpc.trust + Math.round(gain * 0.6), 0, 100);
      updatedNpc.tension = clamp(updatedNpc.tension - 4, 0, 100);
      happinessDelta = 3;
      message = `Had a meaningful conversation with ${target.firstName} (+${gain} Affection).`;
      break;
    }
    case 'gift': {
      const cost = 150;
      if (state.player.finance.cash < cost) {
        return { state, success: false, message: 'Not enough cash for a thoughtful gift ($150).' };
      }
      cashDelta = -cost;
      updatedNpc = addNpcMemory(
        updatedNpc,
        'thoughtful_gift',
        'Gave a thoughtful gift',
        10,
        state.player.ageMonths,
      );
      happinessDelta = 4;
      message = `${target.firstName} loved the gift!`;
      break;
    }
    case 'date': {
      if (playerAgeYears < 13) {
        return { state, success: false, message: 'You are too young to go on romantic dates.' };
      }
      if (target.role !== 'partner' && target.role !== 'spouse') {
        return { state, success: false, message: 'You can only go on a date with a partner or spouse.' };
      }
      const cost = 120;
      if (state.player.finance.cash < cost) {
        return { state, success: false, message: 'Not enough cash for date night ($120).' };
      }
      cashDelta = -cost;
      updatedNpc = addNpcMemory(
        updatedNpc,
        'date_night',
        'Great date night together',
        12,
        state.player.ageMonths,
      );
      happinessDelta = 7;
      stressDelta = -5;
      message = `Date night with ${target.firstName} was a hit.`;
      break;
    }
    case 'propose': {
      if (target.role !== 'partner') {
        return { state, success: false, message: 'You can only propose to your partner.' };
      }
      if (playerAgeYears < country.legalMarriageAge || targetAgeYears < country.legalMarriageAge) {
        return {
          state,
          success: false,
          message: `Both partners must be at least ${country.legalMarriageAge} to marry in ${country.name}.`,
        };
      }
      const acceptScore = (target.affection + target.trust - target.tension) / 2;
      if (acceptScore >= 62 || rng.chance(clamp((acceptScore - 40) / 50, 0.05, 0.9))) {
        updatedNpc.role = 'spouse';
        updatedNpc = addNpcMemory(
          updatedNpc,
          'married',
          'Got married!',
          25,
          state.player.ageMonths,
          0.04,
        );
        happinessDelta = 16;
        message = `${target.firstName} said YES! You are now married.`;
      } else {
        updatedNpc = addNpcMemory(
          updatedNpc,
          'rejected_proposal',
          'Turned down proposal',
          -12,
          state.player.ageMonths,
        );
        happinessDelta = -12;
        stressDelta = 10;
        message = `${target.firstName} isn't ready for marriage yet.`;
      }
      break;
    }
    case 'apologize': {
      updatedNpc.tension = clamp(updatedNpc.tension - 16, 0, 100);
      updatedNpc.trust = clamp(updatedNpc.trust + 6, 0, 100);
      updatedNpc.affection = clamp(updatedNpc.affection + 5, 0, 100);
      message = `Cleared the air with ${target.firstName}.`;
      break;
    }
    case 'argue': {
      updatedNpc = addNpcMemory(
        updatedNpc,
        'heated_argument',
        'Had a heated argument',
        -14,
        state.player.ageMonths,
      );
      stressDelta = 8;
      happinessDelta = -6;
      message = `Things got heated with ${target.firstName}.`;
      break;
    }
    case 'breakup': {
      if (target.role !== 'partner' && target.role !== 'spouse') {
        return { state, success: false, message: 'Can only break up with a partner or spouse.' };
      }
      updatedNpc.role = 'friend';
      updatedNpc = addNpcMemory(
        updatedNpc,
        'breakup',
        'Ended our relationship',
        -25,
        state.player.ageMonths,
        0.08,
      );
      happinessDelta = -10;
      stressDelta = 8;
      message = `You ended your relationship with ${target.firstName}.`;
      break;
    }
  }

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        stats: {
          ...state.player.stats,
          happiness: clamp(state.player.stats.happiness + happinessDelta, 0, 100),
          stress: clamp(state.player.stats.stress + stressDelta, 0, 100),
        },
        finance: {
          ...state.player.finance,
          cash: state.player.finance.cash + cashDelta,
        },
      },
      npcs: state.npcs.map((n) => (n.id === npcId ? updatedNpc : n)),
    },
    success: true,
    message,
  };
}

export function setChildParentingStyle(
  state: GameState,
  npcId: string,
  style: ParentingStyle,
): { state: GameState; success: boolean; message: string } {
  const child = state.npcs.find((n) => n.id === npcId && n.role === 'child' && n.alive);
  if (!child) return { state, success: false, message: 'Child not found.' };
  return {
    state: {
      ...state,
      npcs: state.npcs.map((n) => (n.id === npcId ? { ...n, parentingStyle: style } : n)),
    },
    success: true,
    message: `Set parenting style for ${child.firstName} to ${style}.`,
  };
}

export function spawnChildNpc(state: GameState, pack: EraPack, rng: SeededRng): Npc {
  const partner = state.npcs.find((n) => n.alive && (n.role === 'spouse' || n.role === 'partner'));
  const childDna = inheritDna(rng, state.player.dna, partner?.dna);
  return createNpc(rng, pack, state.config.countryId, 'child', 0, {
    lastName: state.player.lastName,
    dna: childDna,
    wealth: 0,
  });
}
