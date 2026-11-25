export type TurnGranularity = 'YEARLY' | 'QUARTERLY' | 'BIMONTHLY' | 'MONTHLY';

export type LifeStage =
  | 'infancy'
  | 'childhood'
  | 'adolescence'
  | 'young_adult'
  | 'prime_adult'
  | 'mature_adult'
  | 'elder';

export type Gender = 'male' | 'female' | 'non_binary';
export type Difficulty = 'forgiving' | 'realistic' | 'hardcore';
export type FamilyWealthTier = 'struggling' | 'working' | 'middle' | 'upper_middle' | 'wealthy';
export type MacroStateId = 'boom' | 'normal' | 'recession';
export type ParentingStyle = 'authoritative' | 'permissive' | 'strict' | 'neglectful';

export type SkillId =
  | 'literacy'
  | 'numeracy'
  | 'critical_thinking'
  | 'communication'
  | 'coding'
  | 'analytics'
  | 'finance'
  | 'persuasion'
  | 'medicine'
  | 'law'
  | 'creativity'
  | 'leadership'
  | 'trades'
  | 'operations';

export interface PlayerStats {
  physicalHealth: number; // 0-100
  mentalHealth: number;   // 0-100
  happiness: number;      // 0-100
  energy: number;         // 0-100
  stress: number;         // 0-100 (lower is better)
  looks: number;          // 0-100
  intelligence: number;   // 0-100
  charisma: number;       // 0-100
  discipline: number;     // 0-100
}

export interface HiddenMetrics {
  burnout: number;          // 0-100
  sleepDebt: number;        // 0-100
  immuneResilience: number; // 0-100
  reputation: number;       // 0-100
}

export interface DnaProfile {
  longevity: number;        // 0-100
  intellectCeiling: number; // 0-100
  athleticism: number;      // 0-100
  mentalResilience: number; // 0-100
  appearance: number;       // 0-100
  predispositions: string[]; // heritable condition IDs
}

export interface WeeklySchedule {
  sleep: number;
  workOrSchool: number;
  hustleAndSkills: number;
  fitnessAndSelfCare: number;
  familyAndSocial: number;
  leisureAndRest: number;
}

export type SchedulePresetId = 'balanced' | 'hustle' | 'family_first' | 'health_kick';

export interface ActiveCondition {
  id: string;
  diagnosedAgeMonths: number;
  severity: number; // 0-100
  treated: boolean;
}

export interface ActiveHabit {
  id: string;
  strength: number; // 0-100
}

export interface EducationState {
  currentProgramId: string | null;
  monthsCompletedInProgram: number;
  gpa: number; // 0.0 - 4.0
  completedProgramIds: string[];
  studentLoanBalance: number;
}

export interface JobState {
  trackId: string;
  tierId: string;
  title: string;
  annualSalary: number;
  performance: number; // 0-100
  monthsInTrack: number;
  monthsInTier: number;
  bossNpcId: string | null;
}

export interface InvestmentHolding {
  assetId: string;
  balance: number;
  costBasis: number;
}

export interface FinanceState {
  cash: number;
  creditScore: number; // 300-850
  housingId: string;
  ownsHousing: boolean;
  mortgageBalance: number;
  investments: InvestmentHolding[];
  lastTurnIncome: number;
  lastTurnExpenses: number;
  lastTurnTax: number;
  netWorthHistory: Array<{ ageMonths: number; netWorth: number }>;
}

export interface NpcPersonality {
  warmth: number;     // 0-100
  ambition: number;   // 0-100
  loyalty: number;    // 0-100
  volatility: number; // 0-100
  honesty: number;    // 0-100
}

export interface NpcMemory {
  id: string;
  tag: string;
  label: string;
  sentimentDelta: number; // positive or negative
  ageMonths: number;
  decayRate: number;      // annual decay multiplier (e.g. 0.15)
}

export type NpcRole =
  | 'parent'
  | 'sibling'
  | 'friend'
  | 'partner'
  | 'spouse'
  | 'child'
  | 'boss'
  | 'coworker'
  | 'rival';

export interface Npc {
  id: string;
  firstName: string;
  lastName: string;
  gender: Gender;
  role: NpcRole;
  ageMonths: number;
  alive: boolean;
  health: number;
  wealth: number;
  personality: NpcPersonality;
  affection: number; // 0-100
  trust: number;     // 0-100
  respect: number;   // 0-100
  tension: number;   // 0-100
  memories: NpcMemory[];
  parentingStyle?: ParentingStyle;
  dna?: DnaProfile;
}

export interface TimelineEntry {
  id: string;
  turn: number;
  ageMonths: number;
  year: number;
  category: 'milestone' | 'career' | 'finance' | 'health' | 'relationship' | 'world' | 'chaos' | 'director' | 'death';
  title: string;
  detail: string;
}

export interface ScheduledFollowUp {
  eventId: string;
  triggerTurn: number;
}

export interface WorldConfig {
  eraId: string;
  countryId: string;
  granularity: TurnGranularity;
  difficulty: Difficulty;
  chaosLevel: number;        // 0-100
  economyVolatility: number; // 0-100
  ironman: boolean;
}

export interface WorldState {
  currentYear: number;
  currentMonth: number; // 1-12
  macroState: MacroStateId;
  inflationIndex: number;
}

export interface DeathSummary {
  ageMonths: number;
  causeOfDeath: string;
  finalNetWorth: number;
  peakNetWorth: number;
  highestJobTitle: string | null;
  childrenCount: number;
  legacyScore: number;
}

export interface PlayerState {
  firstName: string;
  lastName: string;
  gender: Gender;
  ageMonths: number;
  alive: boolean;
  retired: boolean;
  familyWealthTier: FamilyWealthTier;
  stats: PlayerStats;
  hidden: HiddenMetrics;
  dna: DnaProfile;
  skills: Record<SkillId, number>;
  focusSkill: SkillId;
  schedule: WeeklySchedule;
  actionPoints: number;
  maxActionPoints: number;
  conditions: ActiveCondition[];
  habits: ActiveHabit[];
  education: EducationState;
  job: JobState | null;
  highestJobTitle: string | null;
  finance: FinanceState;
  flags: Record<string, boolean>;
}

export interface GameState {
  version: string;
  seed: number;
  rngState: number;
  turn: number;
  config: WorldConfig;
  world: WorldState;
  player: PlayerState;
  npcs: Npc[];
  pendingEvent: GameEvent | null;
  eventHistory: Record<string, number>; // eventId -> last triggered turn
  triggeredOneShots: string[];
  scheduledFollowUps: ScheduledFollowUp[];
  timeline: TimelineEntry[];
  peakNetWorth: number;
  deathSummary: DeathSummary | null;
}

// ==========================================
// Era Pack JSON Schemas
// ==========================================

export interface MacroStateDef {
  label: string;
  annualInflation: number;
  annualMarketReturn: number;
  hiringMultiplier: number;
  layoffRiskMultiplier: number;
  salaryGrowthBonus: number;
}

export interface EraMeta {
  id: string;
  name: string;
  description: string;
  startYear: number;
  defaultCountryId: string;
  macroStates: Record<MacroStateId, MacroStateDef>;
}

export interface TaxBracket {
  upTo: number | null;
  rate: number;
}

export interface CountryDef {
  id: string;
  name: string;
  flag: string;
  currencyCode: string;
  currencySymbol: string;
  salaryMultiplier: number;
  costOfLivingMultiplier: number;
  healthcareCostMultiplier: number;
  tuitionMultiplier: number;
  baseAnnualLivingCost: number;
  legalAdultAge: number;
  legalMarriageAge: number;
  retirementAge: number;
  annualPensionBase: number;
  taxBrackets: TaxBracket[];
}

export interface EducationProgramDef {
  id: string;
  name: string;
  level: 'primary' | 'secondary' | 'vocational' | 'undergraduate' | 'graduate';
  durationYears: number;
  minAge: number;
  annualTuition: number;
  minIntelligence: number;
  weeklyStudyHoursRequired: number;
  prerequisite?: string;
  skillTags: SkillId[];
}

export interface CareerTierDef {
  id: string;
  title: string;
  level: number;
  minAge: number;
  baseAnnualSalary: number;
  weeklyHoursRequired: number;
  annualStressImpact: number;
  requiredEducation: string[];
  minSkills: Partial<Record<SkillId, number>>;
  minIntelligence?: number;
  minYearsInTrack?: number;
  skillGrowth: Partial<Record<SkillId, number>>;
}

export interface CareerTrackDef {
  id: string;
  name: string;
  industry: string;
  isPartTime: boolean;
  tiers: CareerTierDef[];
}

export interface InvestmentAssetDef {
  id: string;
  name: string;
  type: 'index_fund' | 'stock' | 'bond' | 'crypto';
  expectedAnnualReturn: number;
  annualVolatility: number;
  minInvestment: number;
}

export interface HousingDef {
  id: string;
  name: string;
  annualRent: number;
  purchasePrice: number;
  happinessBonus: number;
  stressReduction: number;
}

export interface AssetsCatalog {
  investments: InvestmentAssetDef[];
  housing: HousingDef[];
}

export interface ConditionDef {
  id: string;
  name: string;
  category: 'physical' | 'mental';
  chronic: boolean;
  severity: number;
  annualHealthDrain: number;
  annualMentalDrain: number;
  annualMortalityRisk: number;
  treatmentCost: number;
  cureChance: number;
  heritable: boolean;
}

export interface HabitDef {
  id: string;
  name: string;
  polarity: 'positive' | 'negative';
  annualHealthDelta: number;
  annualMentalDelta: number;
  annualStressDelta: number;
  annualIntelligenceDelta?: number;
  annualCost: number;
}

export interface ConditionsCatalog {
  conditions: ConditionDef[];
  habits: HabitDef[];
}

export interface CountryNamePool {
  male: string[];
  female: string[];
  neutral: string[];
  last: string[];
}

export interface EventConditions {
  minAge?: number;
  maxAge?: number;
  employed?: boolean;
  hasPartner?: boolean;
  minCash?: number;
  minStress?: number;
  minIntelligence?: number;
  minPhysicalHealth?: number;
  minChaos?: number;
  requiredFlag?: string;
}

export interface EventChoiceEffects {
  stats?: Partial<PlayerStats>;
  skills?: Partial<Record<SkillId, number>>;
  cashDelta?: number;
  creditScoreDelta?: number;
  gpaDelta?: number;
  jobPerformanceDelta?: number;
  parentAffection?: number;
  partnerAffection?: number;
  allNpcAffection?: number;
  addHabit?: string;
  removeHabit?: string;
  addCondition?: string;
  setHousing?: string;
  setFlag?: string;
  setMacro?: MacroStateId;
  retire?: boolean;
  spawnPartner?: boolean;
  spawnChild?: boolean;
  investIn?: { assetId: string; amount: number };
  memoryTag?: { tag: string; label: string; sentimentDelta: number };
  followUp?: { eventId: string; delayTurns: number };
}

export interface EventChoice {
  id: string;
  label: string;
  requirements?: {
    minCash?: number;
    minIntelligence?: number;
    minCharisma?: number;
  };
  effects: EventChoiceEffects;
}

export interface GameEvent {
  id: string;
  category: 'milestone' | 'career' | 'finance' | 'health' | 'relationship' | 'world' | 'chaos';
  weight: number;
  oneShot: boolean;
  cooldownTurns: number;
  conditions: EventConditions;
  title: string;
  body: string;
  choices: EventChoice[];
}

export interface EraPack {
  meta: EraMeta;
  countries: CountryDef[];
  education: EducationProgramDef[];
  careers: CareerTrackDef[];
  assets: AssetsCatalog;
  conditions: ConditionsCatalog;
  events: GameEvent[];
  names: Record<string, CountryNamePool>;
}

// ==========================================
// Player Actions & Director Mutations
// ==========================================

export type PlayerAction =
  | { type: 'ENROLL_EDUCATION'; programId: string }
  | { type: 'APPLY_JOB'; trackId: string; tierId: string }
  | { type: 'REQUEST_PROMOTION' }
  | { type: 'QUIT_JOB' }
  | { type: 'RETIRE' }
  | { type: 'INVEST'; assetId: string; amount: number }
  | { type: 'SELL_INVESTMENT'; assetId: string; amount: number }
  | { type: 'CHANGE_HOUSING'; housingId: string; buy?: boolean }
  | { type: 'INTERACT_NPC'; npcId: string; interaction: 'talk' | 'gift' | 'date' | 'propose' | 'apologize' | 'argue' | 'breakup' }
  | { type: 'SET_PARENTING_STYLE'; npcId: string; style: ParentingStyle }
  | { type: 'DOCTOR_VISIT'; conditionId?: string }
  | { type: 'THERAPY_SESSION' }
  | { type: 'QUIT_HABIT'; habitId: string }
  | { type: 'ADOPT_HABIT'; habitId: string };

export interface ActionResult {
  state: GameState;
  success: boolean;
  message: string;
}

export interface CreateLifeOptions {
  seed?: number;
  firstName?: string;
  lastName?: string;
  gender?: Gender;
  countryId?: string;
  granularity?: TurnGranularity;
  difficulty?: Difficulty;
  chaosLevel?: number;
  economyVolatility?: number;
  ironman?: boolean;
  familyWealthTier?: FamilyWealthTier;
  dnaOverrides?: Partial<DnaProfile>;
  statOverrides?: Partial<PlayerStats>;
}
