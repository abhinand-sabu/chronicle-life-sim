import type {
  CreateLifeOptions,
  EraPack,
  FamilyWealthTier,
  GameState,
  Gender,
  PlayerStats,
  SkillId,
} from './types';
import { clamp, SeededRng } from './rng';
import { getCountry, loadEraPack } from './eraLoader';
import { computeTurnActionPoints } from './time';
import { getDefaultScheduleForAge } from './schedule';
import { generateDna } from './health';
import { createNpc, pickNameForCountry } from './relationships';

const ALL_SKILLS: SkillId[] = [
  'literacy',
  'numeracy',
  'critical_thinking',
  'communication',
  'coding',
  'analytics',
  'finance',
  'persuasion',
  'medicine',
  'law',
  'creativity',
  'leadership',
  'trades',
  'operations',
];

const WEALTH_TIER_CONFIG: Record<
  FamilyWealthTier,
  { parentWealth: number; startingCash: number; label: string }
> = {
  struggling: { parentWealth: 6000, startingCash: 0, label: 'Struggling' },
  working: { parentWealth: 28000, startingCash: 250, label: 'Working Class' },
  middle: { parentWealth: 95000, startingCash: 1000, label: 'Middle Class' },
  upper_middle: { parentWealth: 340000, startingCash: 5000, label: 'Upper-Middle Class' },
  wealthy: { parentWealth: 1800000, startingCash: 25000, label: 'Wealthy' },
};

export function createNewLife(options: CreateLifeOptions = {}, eraPack?: EraPack): GameState {
  const pack = eraPack ?? loadEraPack('modern_2020s');
  const seed = (options.seed ?? Date.now()) >>> 0;
  const rng = new SeededRng(seed);

  const countryId = options.countryId ?? pack.meta.defaultCountryId;
  const country = getCountry(pack, countryId);
  const granularity = options.granularity ?? 'BIMONTHLY';
  const difficulty = options.difficulty ?? 'realistic';
  const chaosLevel = clamp(options.chaosLevel ?? 30, 0, 100);
  const economyVolatility = clamp(options.economyVolatility ?? 40, 0, 100);
  const ironman = options.ironman ?? false;

  const gender: Gender = options.gender ?? rng.pick(['male', 'female', 'non_binary']);
  const generatedName = pickNameForCountry(rng, pack, countryId, gender);
  const firstName = options.firstName?.trim() || generatedName.firstName;
  const lastName = options.lastName?.trim() || generatedName.lastName;

  const familyWealthTier: FamilyWealthTier =
    options.familyWealthTier ?? rng.pick(['struggling', 'working', 'middle', 'upper_middle', 'wealthy']);
  const wealthCfg = WEALTH_TIER_CONFIG[familyWealthTier];

  const dna = generateDna(rng, pack, options.dnaOverrides);

  const stats: PlayerStats = {
    physicalHealth: clamp(Math.round(options.statOverrides?.physicalHealth ?? 78 + (dna.athleticism - 50) * 0.25), 20, 100),
    mentalHealth: clamp(Math.round(options.statOverrides?.mentalHealth ?? 76 + (dna.mentalResilience - 50) * 0.25), 20, 100),
    happiness: clamp(Math.round(options.statOverrides?.happiness ?? 80 + rng.nextInt(-8, 10)), 20, 100),
    energy: clamp(Math.round(options.statOverrides?.energy ?? 90), 20, 100),
    stress: clamp(Math.round(options.statOverrides?.stress ?? 5), 0, 100),
    looks: clamp(Math.round(options.statOverrides?.looks ?? dna.appearance + rng.nextInt(-8, 8)), 10, 100),
    intelligence: clamp(Math.round(options.statOverrides?.intelligence ?? dna.intellectCeiling * 0.8 + rng.nextInt(-6, 10)), 15, 100),
    charisma: clamp(Math.round(options.statOverrides?.charisma ?? 52 + rng.nextInt(-15, 18)), 10, 100),
    discipline: clamp(Math.round(options.statOverrides?.discipline ?? 50 + rng.nextInt(-15, 18)), 10, 100),
  };

  const skills = Object.fromEntries(ALL_SKILLS.map((s) => [s, 0])) as Record<SkillId, number>;

  // Spawn parents and optional sibling
  const motherAge = rng.nextInt(23, 38) * 12;
  const fatherAge = rng.nextInt(24, 41) * 12;
  const mother = createNpc(rng, pack, countryId, 'parent', motherAge, {
    lastName,
    gender: 'female',
    wealth: Math.round(wealthCfg.parentWealth * country.costOfLivingMultiplier),
  });
  const father = createNpc(rng, pack, countryId, 'parent', fatherAge, {
    lastName,
    gender: 'male',
    wealth: Math.round(wealthCfg.parentWealth * country.costOfLivingMultiplier),
  });
  const npcs = [mother, father];
  if (rng.chance(0.55)) {
    const siblingAge = rng.nextInt(1, 6) * 12;
    npcs.push(createNpc(rng, pack, countryId, 'sibling', siblingAge, { lastName, wealth: 0 }));
  }

  const startingCash = Math.round(wealthCfg.startingCash * country.costOfLivingMultiplier);
  const maxAp = computeTurnActionPoints(granularity, stats.energy, 0);

  return {
    version: '0.1.0',
    seed,
    rngState: rng.getState(),
    turn: 0,
    config: {
      eraId: pack.meta.id,
      countryId,
      granularity,
      difficulty,
      chaosLevel,
      economyVolatility,
      ironman,
    },
    world: {
      currentYear: pack.meta.startYear,
      currentMonth: 1,
      macroState: 'normal',
      inflationIndex: 1.0,
    },
    player: {
      firstName,
      lastName,
      gender,
      ageMonths: 0,
      alive: true,
      retired: false,
      familyWealthTier,
      stats,
      hidden: {
        burnout: 0,
        sleepDebt: 0,
        immuneResilience: clamp(Math.round(55 + (dna.longevity - 50) * 0.4), 20, 100),
        reputation: 50,
      },
      dna,
      skills,
      focusSkill: 'coding',
      schedule: getDefaultScheduleForAge(0),
      actionPoints: maxAp,
      maxActionPoints: maxAp,
      conditions: [],
      habits: [],
      education: {
        currentProgramId: null,
        monthsCompletedInProgram: 0,
        gpa: 3.2,
        completedProgramIds: [],
        studentLoanBalance: 0,
      },
      job: null,
      highestJobTitle: null,
      finance: {
        cash: startingCash,
        creditScore: 660,
        housingId: 'parents_home',
        ownsHousing: false,
        mortgageBalance: 0,
        investments: [],
        lastTurnIncome: 0,
        lastTurnExpenses: 0,
        lastTurnTax: 0,
        netWorthHistory: [{ ageMonths: 0, netWorth: startingCash }],
      },
      flags: {},
    },
    npcs,
    pendingEvent: null,
    eventHistory: {},
    triggeredOneShots: [],
    scheduledFollowUps: [],
    timeline: [
      {
        id: 'tl_birth',
        turn: 0,
        ageMonths: 0,
        year: pack.meta.startYear,
        category: 'milestone',
        title: `Born in ${country.name}`,
        detail: `${firstName} ${lastName} was born into a ${wealthCfg.label.toLowerCase()} family in ${country.name}.`,
      },
    ],
    peakNetWorth: startingCash,
    deathSummary: null,
  };
}
