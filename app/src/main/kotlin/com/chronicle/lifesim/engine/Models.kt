package com.chronicle.lifesim.engine

import kotlinx.serialization.Serializable

@Serializable
enum class TurnGranularity {
  YEARLY,
  QUARTERLY,
  BIMONTHLY,
  MONTHLY,
}

@Serializable
enum class LifeStage {
  INFANCY,
  CHILDHOOD,
  ADOLESCENCE,
  YOUNG_ADULT,
  PRIME_ADULT,
  MATURE_ADULT,
  ELDER,
}

@Serializable
enum class Gender {
  male,
  female,
  non_binary,
}

@Serializable
enum class Difficulty {
  forgiving,
  realistic,
  hardcore,
}

@Serializable
enum class FamilyWealthTier {
  struggling,
  working,
  middle,
  upper_middle,
  wealthy,
}

@Serializable
enum class MacroStateId {
  boom,
  normal,
  recession,
}

@Serializable
enum class ParentingStyle {
  authoritative,
  permissive,
  strict,
  neglectful,
}

@Serializable
enum class SkillId {
  literacy,
  numeracy,
  critical_thinking,
  communication,
  coding,
  analytics,
  finance,
  persuasion,
  medicine,
  law,
  creativity,
  leadership,
  trades,
  operations,
}

enum class SchedulePresetId {
  balanced,
  hustle,
  family_first,
  health_kick,
}

@Serializable
data class PlayerStats(
  val physicalHealth: Double = 78.0,
  val mentalHealth: Double = 76.0,
  val happiness: Double = 80.0,
  val energy: Double = 90.0,
  val stress: Double = 5.0,
  val looks: Double = 55.0,
  val intelligence: Double = 55.0,
  val charisma: Double = 52.0,
  val discipline: Double = 50.0,
)

@Serializable
data class HiddenMetrics(
  val burnout: Double = 0.0,
  val sleepDebt: Double = 0.0,
  val immuneResilience: Double = 55.0,
  val reputation: Double = 50.0,
)

@Serializable
data class DnaProfile(
  val longevity: Int = 60,
  val intellectCeiling: Int = 65,
  val athleticism: Int = 60,
  val mentalResilience: Int = 60,
  val appearance: Int = 60,
  val predispositions: List<String> = emptyList(),
)

@Serializable
data class WeeklySchedule(
  val sleep: Int = 56,
  val workOrSchool: Int = 40,
  val hustleAndSkills: Int = 10,
  val fitnessAndSelfCare: Int = 8,
  val familyAndSocial: Int = 24,
  val leisureAndRest: Int = 30,
)

@Serializable
data class ActiveCondition(
  val id: String,
  val diagnosedAgeMonths: Int,
  val severity: Double,
  val treated: Boolean = false,
)

@Serializable
data class ActiveHabit(
  val id: String,
  val strength: Double = 35.0,
)

@Serializable
data class EducationState(
  val currentProgramId: String? = null,
  val monthsCompletedInProgram: Int = 0,
  val gpa: Double = 3.2,
  val completedProgramIds: List<String> = emptyList(),
  val studentLoanBalance: Long = 0,
)

@Serializable
data class JobState(
  val trackId: String,
  val tierId: String,
  val title: String,
  val annualSalary: Long,
  val performance: Double = 60.0,
  val monthsInTrack: Int = 0,
  val monthsInTier: Int = 0,
  val bossNpcId: String? = null,
)

@Serializable
data class InvestmentHolding(
  val assetId: String,
  val balance: Long,
  val costBasis: Long,
)

@Serializable
data class NetWorthPoint(
  val ageMonths: Int,
  val netWorth: Long,
)

@Serializable
data class FinanceState(
  val cash: Long = 0,
  val creditScore: Int = 660,
  val housingId: String = "parents_home",
  val ownsHousing: Boolean = false,
  val mortgageBalance: Long = 0,
  val investments: List<InvestmentHolding> = emptyList(),
  val lastTurnIncome: Long = 0,
  val lastTurnExpenses: Long = 0,
  val lastTurnTax: Long = 0,
  val netWorthHistory: List<NetWorthPoint> = emptyList(),
  val possessions: Set<Goods> = emptySet(),
  val personalLoans: List<PersonalLoan> = emptyList(),
  val hardshipMonths: Int = 0,
  val receivedEmergencyAid: Boolean = false,
  val usedDebtSettlement: Boolean = false,
)

@Serializable
data class NpcPersonality(
  val warmth: Int,
  val ambition: Int,
  val loyalty: Int,
  val volatility: Int,
  val honesty: Int,
)

@Serializable
data class NpcMemory(
  val id: String,
  val tag: String,
  val label: String,
  val sentimentDelta: Double,
  val ageMonths: Int,
  val decayRate: Double = 0.12,
)

@Serializable
enum class NpcRole {
  parent,
  sibling,
  friend,
  partner,
  spouse,
  child,
  boss,
  coworker,
  rival,
}

@Serializable
data class Npc(
  val id: String,
  val firstName: String,
  val lastName: String,
  val gender: Gender,
  val role: NpcRole,
  val ageMonths: Int,
  val alive: Boolean = true,
  val health: Double = 80.0,
  val wealth: Long = 20000,
  val personality: NpcPersonality,
  val affection: Double = 70.0,
  val trust: Double = 70.0,
  val respect: Double = 70.0,
  val tension: Double = 5.0,
  val memories: List<NpcMemory> = emptyList(),
  val parentingStyle: ParentingStyle? = null,
  val dna: DnaProfile? = null,
)

@Serializable
data class TimelineEntry(
  val id: String,
  val turn: Int,
  val ageMonths: Int,
  val year: Int,
  val category: String,
  val title: String,
  val detail: String,
)

@Serializable
data class ScheduledFollowUp(
  val eventId: String,
  val triggerTurn: Int,
)

@Serializable
data class WorldConfig(
  val eraId: String = "modern_2020s",
  val countryId: String = "usa",
  val granularity: TurnGranularity = TurnGranularity.BIMONTHLY,
  val difficulty: Difficulty = Difficulty.realistic,
  val chaosLevel: Int = 35,
  val economyVolatility: Int = 40,
  val ironman: Boolean = false,
)

@Serializable
data class WorldState(
  val currentYear: Int = 2025,
  val currentMonth: Int = 1,
  val macroState: MacroStateId = MacroStateId.normal,
  val inflationIndex: Double = 1.0,
)

@Serializable
data class DeathSummary(
  val ageMonths: Int,
  val causeOfDeath: String,
  val finalNetWorth: Long,
  val peakNetWorth: Long,
  val highestJobTitle: String? = null,
  val childrenCount: Int = 0,
  val legacyScore: Int = 0,
)

@Serializable
enum class ClubId {
  robotics,
  theatre,
  soccer,
}

@Serializable
data class ActiveClub(
  val clubId: ClubId,
  val name: String,
  val weeklyHours: Int = 4,
  val termMonthsCompleted: Int = 0,
  val termLengthMonths: Int = 6,
  val progress: Double = 10.0,
  val teammateNpcId: String? = null,
  val showcaseReady: Boolean = false,
  val completedTerms: Int = 0,
)

@Serializable
enum class ProjectType {
  lemonade_stall,
  passion_zine,
  portfolio_app,
}

@Serializable
data class ActiveProject(
  val id: String,
  val type: ProjectType,
  val title: String,
  val budgetSpent: Long,
  val progress: Double = 0.0,
  val weeklyHours: Int = 4,
  val milestonesHit: Int = 0,
)

@Serializable
data class PortfolioItem(
  val id: String,
  val title: String,
  val category: String,
  val ageMonths: Int,
  val impactLabel: String,
)

@Serializable
data class PlayerState(
  val firstName: String,
  val lastName: String,
  val gender: Gender,
  val ageMonths: Int = 0,
  val alive: Boolean = true,
  val retired: Boolean = false,
  val familyWealthTier: FamilyWealthTier = FamilyWealthTier.middle,
  val stats: PlayerStats,
  val hidden: HiddenMetrics,
  val dna: DnaProfile,
  val skills: Map<SkillId, Double>,
  val focusSkill: SkillId = SkillId.coding,
  val schedule: WeeklySchedule,
  val actionPoints: Int,
  val maxActionPoints: Int,
  val conditions: List<ActiveCondition> = emptyList(),
  val habits: List<ActiveHabit> = emptyList(),
  val education: EducationState = EducationState(),
  val job: JobState? = null,
  val highestJobTitle: String? = null,
  val finance: FinanceState = FinanceState(),
  val flags: Map<String, Boolean> = emptyMap(),
  val dateProspect: Npc? = null,
  val courtshipDates: Int = 0,
  val lastDateTurn: Int? = null,
  val lastDateChatTurn: Int? = null,
  val interviewAttempts: Set<String> = emptySet(),
  val lastPaidActivityAgeMonths: Int? = null,
  val activeClub: ActiveClub? = null,
  val activeProject: ActiveProject? = null,
  val portfolio: List<PortfolioItem> = emptyList(),
)

@Serializable
data class GameState(
  val version: String = "0.1.0",
  val seed: Long,
  val rngState: Long,
  val turn: Int = 0,
  val config: WorldConfig,
  val world: WorldState,
  val player: PlayerState,
  val npcs: List<Npc>,
  val pendingEvent: GameEvent? = null,
  val eventHistory: Map<String, Int> = emptyMap(),
  val triggeredOneShots: List<String> = emptyList(),
  val scheduledFollowUps: List<ScheduledFollowUp> = emptyList(),
  val timeline: List<TimelineEntry> = emptyList(),
  val peakNetWorth: Long = 0,
  val deathSummary: DeathSummary? = null,
)

// ==========================================
// Era Pack JSON Models
// ==========================================

@Serializable
data class MacroStateDef(
  val label: String,
  val annualInflation: Double,
  val annualMarketReturn: Double,
  val hiringMultiplier: Double,
  val layoffRiskMultiplier: Double,
  val salaryGrowthBonus: Double,
)

@Serializable
data class EraMeta(
  val id: String,
  val name: String,
  val description: String,
  val startYear: Int,
  val defaultCountryId: String,
  val macroStates: Map<MacroStateId, MacroStateDef>,
)

@Serializable
data class TaxBracket(
  val upTo: Long? = null,
  val rate: Double,
)

@Serializable
data class CountryDef(
  val id: String,
  val name: String,
  val flag: String,
  val currencyCode: String,
  val currencySymbol: String,
  val salaryMultiplier: Double,
  val costOfLivingMultiplier: Double,
  val healthcareCostMultiplier: Double,
  val tuitionMultiplier: Double,
  val baseAnnualLivingCost: Long,
  val legalAdultAge: Int = 18,
  val legalMarriageAge: Int = 18,
  val retirementAge: Int = 65,
  val annualPensionBase: Long,
  val taxBrackets: List<TaxBracket>,
)

@Serializable
data class EducationProgramDef(
  val id: String,
  val name: String,
  val level: String,
  val durationYears: Int,
  val minAge: Int,
  val annualTuition: Long,
  val minIntelligence: Int,
  val weeklyStudyHoursRequired: Int,
  val prerequisite: String? = null,
  val skillTags: List<SkillId> = emptyList(),
)

@Serializable
data class CareerTierDef(
  val id: String,
  val title: String,
  val level: Int,
  val minAge: Int,
  val baseAnnualSalary: Long,
  val weeklyHoursRequired: Int,
  val annualStressImpact: Double,
  val requiredEducation: List<String> = emptyList(),
  val minSkills: Map<SkillId, Double> = emptyMap(),
  val minIntelligence: Int? = null,
  val minYearsInTrack: Int? = null,
  val skillGrowth: Map<SkillId, Double> = emptyMap(),
)

@Serializable
data class CareerTrackDef(
  val id: String,
  val name: String,
  val industry: String,
  val isPartTime: Boolean = false,
  val tiers: List<CareerTierDef>,
)

@Serializable
data class InvestmentAssetDef(
  val id: String,
  val name: String,
  val type: String,
  val expectedAnnualReturn: Double,
  val annualVolatility: Double,
  val minInvestment: Long,
)

@Serializable
data class HousingDef(
  val id: String,
  val name: String,
  val annualRent: Long,
  val purchasePrice: Long,
  val happinessBonus: Double,
  val stressReduction: Double,
)

@Serializable
data class AssetsCatalog(
  val investments: List<InvestmentAssetDef>,
  val housing: List<HousingDef>,
)

@Serializable
data class ConditionDef(
  val id: String,
  val name: String,
  val category: String,
  val chronic: Boolean,
  val severity: Double,
  val annualHealthDrain: Double,
  val annualMentalDrain: Double,
  val annualMortalityRisk: Double,
  val treatmentCost: Long,
  val cureChance: Double,
  val heritable: Boolean,
)

@Serializable
data class HabitDef(
  val id: String,
  val name: String,
  val polarity: String,
  val annualHealthDelta: Double,
  val annualMentalDelta: Double,
  val annualStressDelta: Double,
  val annualIntelligenceDelta: Double? = null,
  val annualCost: Long,
)

@Serializable
data class ConditionsCatalog(
  val conditions: List<ConditionDef>,
  val habits: List<HabitDef>,
)

@Serializable
data class CountryNamePool(
  val male: List<String>,
  val female: List<String>,
  val neutral: List<String>,
  val last: List<String>,
)

@Serializable
data class EventConditions(
  val minAge: Int? = null,
  val maxAge: Int? = null,
  val employed: Boolean? = null,
  val hasPartner: Boolean? = null,
  val minCash: Long? = null,
  val minStress: Double? = null,
  val minIntelligence: Double? = null,
  val minPhysicalHealth: Double? = null,
  val minChaos: Int? = null,
  val requiredFlag: String? = null,
)

@Serializable
data class StatDeltaMap(
  val physicalHealth: Double? = null,
  val mentalHealth: Double? = null,
  val happiness: Double? = null,
  val energy: Double? = null,
  val stress: Double? = null,
  val looks: Double? = null,
  val intelligence: Double? = null,
  val charisma: Double? = null,
  val discipline: Double? = null,
)

@Serializable
data class InvestEffect(
  val assetId: String,
  val amount: Long,
)

@Serializable
data class MemoryTagEffect(
  val tag: String,
  val label: String,
  val sentimentDelta: Double,
)

@Serializable
data class FollowUpEffect(
  val eventId: String,
  val delayTurns: Int,
)

@Serializable
data class EventChoiceEffects(
  val stats: StatDeltaMap? = null,
  val skills: Map<SkillId, Double>? = null,
  val cashDelta: Long? = null,
  val creditScoreDelta: Int? = null,
  val gpaDelta: Double? = null,
  val jobPerformanceDelta: Double? = null,
  val parentAffection: Double? = null,
  val partnerAffection: Double? = null,
  val allNpcAffection: Double? = null,
  val addHabit: String? = null,
  val removeHabit: String? = null,
  val addCondition: String? = null,
  val setHousing: String? = null,
  val setFlag: String? = null,
  val setMacro: MacroStateId? = null,
  val retire: Boolean? = null,
  val spawnPartner: Boolean? = null,
  val spawnChild: Boolean? = null,
  val investIn: InvestEffect? = null,
  val memoryTag: MemoryTagEffect? = null,
  val followUp: FollowUpEffect? = null,
)

@Serializable
data class ChoiceRequirements(
  val minCash: Long? = null,
  val minIntelligence: Double? = null,
  val minCharisma: Double? = null,
)

@Serializable
data class EventChoice(
  val id: String,
  val label: String,
  val requirements: ChoiceRequirements? = null,
  val effects: EventChoiceEffects = EventChoiceEffects(),
)

@Serializable
data class GameEvent(
  val id: String,
  val category: String,
  val weight: Double,
  val oneShot: Boolean,
  val cooldownTurns: Int,
  val conditions: EventConditions = EventConditions(),
  val title: String,
  val body: String,
  val choices: List<EventChoice>,
)

data class EraPack(
  val meta: EraMeta,
  val countries: List<CountryDef>,
  val education: List<EducationProgramDef>,
  val careers: List<CareerTrackDef>,
  val assets: AssetsCatalog,
  val conditions: ConditionsCatalog,
  val events: List<GameEvent>,
  val names: Map<String, CountryNamePool>,
) {
  fun country(id: String): CountryDef =
    countries.firstOrNull { it.id == id }
      ?: countries.first()
}

data class CreateLifeOptions(
  val seed: Long? = null,
  val firstName: String? = null,
  val lastName: String? = null,
  val gender: Gender? = null,
  val countryId: String = "usa",
  val granularity: TurnGranularity = TurnGranularity.BIMONTHLY,
  val difficulty: Difficulty = Difficulty.realistic,
  val chaosLevel: Int = 35,
  val economyVolatility: Int = 40,
  val ironman: Boolean = false,
  val familyWealthTier: FamilyWealthTier = FamilyWealthTier.middle,
  val dnaOverrides: DnaProfile? = null,
)

enum class NpcInteraction {
  talk,
  gift,
  date,
  propose,
  apologize,
  argue,
  breakup,
  spend_time,
  offer_help,
  set_boundary,
}

enum class FirstDatePlan { walk, coffee, dinner, museum, picnic }

sealed interface PlayerAction {
  data class EnrollEducation(val programId: String) : PlayerAction
  data class ApplyJob(val trackId: String, val tierId: String, val answer: InterviewAnswer) : PlayerAction
  data object RequestPromotion : PlayerAction
  data object QuitJob : PlayerAction
  data object Retire : PlayerAction
  data class Invest(val assetId: String, val amount: Long) : PlayerAction
  data class SellInvestment(val assetId: String, val amount: Long) : PlayerAction
  data class Shop(val item: Goods, val sell: Boolean = false) : PlayerAction
  data class Borrow(val type: LoanType) : PlayerAction
  data class RepayLoan(val type: LoanType, val payInFull: Boolean) : PlayerAction
  data object SeekEmergencyAid : PlayerAction
  data object SettleOverdraft : PlayerAction
  data object AskFamilyForShelter : PlayerAction
  data class GiveGift(val npcId: String, val gift: Gift) : PlayerAction
  data class PracticeWellness(val kind: Wellness) : PlayerAction
  data class HabitSupport(val habitId: String) : PlayerAction
  data class ChangeHousing(val housingId: String, val buy: Boolean = false) : PlayerAction
  data class InteractNpc(val npcId: String, val interaction: NpcInteraction) : PlayerAction
  data class SetParentingStyle(val npcId: String, val style: ParentingStyle) : PlayerAction
  data class DoctorVisit(val conditionId: String? = null) : PlayerAction
  data object TherapySession : PlayerAction
  data class QuitHabit(val habitId: String) : PlayerAction
  data class AdoptHabit(val habitId: String) : PlayerAction
  data class JoinClub(val clubId: ClubId, val weeklyHours: Int = 4) : PlayerAction
  data class SetClubHours(val weeklyHours: Int) : PlayerAction
  data object PracticeClub : PlayerAction
  data class EnterClubShowcase(val effortHigh: Boolean = true) : PlayerAction
  data object LeaveClub : PlayerAction
  data object DoPaidActivity : PlayerAction
  data class StartProject(val type: ProjectType) : PlayerAction
  data object WorkOnProject : PlayerAction
  data object AbandonProject : PlayerAction
  data class MeetSomeone(val romantic: Boolean = false) : PlayerAction
  data class FindDate(val preference: Gender? = null) : PlayerAction
  data object ChatWithMatch : PlayerAction
  data object DefineRelationship : PlayerAction
  data class RelationshipDate(val npcId: String, val plan: FirstDatePlan) : PlayerAction
  data object PassDate : PlayerAction
  data class GoOnFirstDate(val plan: FirstDatePlan) : PlayerAction
  data class InviteFriendOver(val npcId: String) : PlayerAction
  data class DiscussFamily(val plan: String) : PlayerAction
  data class ElderActivity(val kind: String) : PlayerAction
}

data class ActionResult(
  val state: GameState,
  val success: Boolean,
  val message: String,
)

sealed interface DirectorMutation {
  data class SetPlayerStat(val stat: String, val value: Double) : DirectorMutation
  data class SetCash(val amount: Long) : DirectorMutation
  data class SetCreditScore(val score: Int) : DirectorMutation
  data class SetGranularity(val granularity: TurnGranularity) : DirectorMutation
  data class SetChaosLevel(val chaosLevel: Int) : DirectorMutation
  data class SetMacroState(val macroState: MacroStateId) : DirectorMutation
  data class SetNpcAffection(val npcId: String, val affection: Double) : DirectorMutation
  data object CureAllConditions : DirectorMutation
}

data class DirectorResult(
  val state: GameState,
  val applied: Boolean,
  val message: String,
)
