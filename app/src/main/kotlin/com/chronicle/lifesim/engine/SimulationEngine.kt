package com.chronicle.lifesim.engine

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

object SimulationEngine {
  const val HOURS_PER_WEEK = 168

  fun clubMeta(clubId: ClubId): Pair<String, SkillId> = when (clubId) {
    ClubId.robotics -> "Robotics Club" to SkillId.coding
    ClubId.theatre -> "Theatre Troupe" to SkillId.creativity
    ClubId.soccer -> "Youth Soccer" to SkillId.leadership
  }

  val SCHEDULE_PRESETS: Map<SchedulePresetId, WeeklySchedule> = mapOf(
    SchedulePresetId.balanced to WeeklySchedule(56, 40, 10, 8, 24, 30),
    SchedulePresetId.hustle to WeeklySchedule(46, 50, 32, 6, 14, 20),
    SchedulePresetId.family_first to WeeklySchedule(56, 36, 6, 8, 42, 20),
    SchedulePresetId.health_kick to WeeklySchedule(60, 38, 10, 20, 18, 22),
  )

  fun getDefaultScheduleForAge(ageMonths: Int): WeeklySchedule = when (getLifeStage(ageMonths)) {
    LifeStage.INFANCY -> WeeklySchedule(84, 0, 0, 8, 46, 30)
    LifeStage.CHILDHOOD -> WeeklySchedule(68, 28, 8, 10, 28, 26)
    LifeStage.ELDER -> WeeklySchedule(58, 0, 10, 14, 40, 46)
    else -> SCHEDULE_PRESETS.getValue(SchedulePresetId.balanced)
  }

  fun normalizeSchedule(input: WeeklySchedule): WeeklySchedule {
    var sleep = max(21, input.sleep)
    var work = max(0, input.workOrSchool)
    var hustle = max(0, input.hustleAndSkills)
    var fitness = max(0, input.fitnessAndSelfCare)
    var family = max(0, input.familyAndSocial)
    var leisure = max(0, input.leisureAndRest)

    val total = sleep + work + hustle + fitness + family + leisure
    val diff = HOURS_PER_WEEK - total
    if (diff > 0) {
      leisure += diff
    } else if (diff < 0) {
      var excess = -diff
      fun trim(cur: Int, floor: Int): Int {
        val take = min(cur - floor, excess)
        excess -= take
        return cur - take
      }
      leisure = trim(leisure, 0)
      hustle = trim(hustle, 0)
      family = trim(family, 0)
      fitness = trim(fitness, 0)
      work = trim(work, 0)
      sleep = trim(sleep, 21)
    }
    val finalTotal = sleep + work + hustle + fitness + family + leisure
    if (finalTotal != HOURS_PER_WEEK) {
      leisure = max(0, leisure + (HOURS_PER_WEEK - finalTotal))
    }
    return WeeklySchedule(sleep, work, hustle, fitness, family, leisure)
  }

  fun prepareManualSchedule(input: WeeklySchedule, requiredWork: Int): WeeklySchedule {
    return normalizeSchedule(input.copy(workOrSchool = max(input.workOrSchool, requiredWork.coerceIn(0, 70))))
  }

  fun fitManualSchedule(input: WeeklySchedule, requiredWork: Int = 0): WeeklySchedule? {
    val booked = listOf(input.sleep, input.workOrSchool, input.hustleAndSkills, input.fitnessAndSelfCare, input.familyAndSocial)
    if (input.sleep < 21 || input.workOrSchool < requiredWork || booked.any { it < 0 }) return null
    val remaining = HOURS_PER_WEEK - booked.sumOf { it.toLong() }
    if (remaining < 0) return null
    return input.copy(leisureAndRest = remaining.toInt())
  }

  fun pickNameForCountry(rng: SeededRng, pack: EraPack, countryId: String, gender: Gender): Pair<String, String> {
    val pool = pack.names[countryId] ?: pack.names[pack.meta.defaultCountryId] ?: return "Alex" to "Mercer"
    val firstList = when (gender) {
      Gender.male -> pool.male
      Gender.female -> pool.female
      Gender.non_binary -> pool.neutral
    }
    return rng.pick(firstList) to rng.pick(pool.last)
  }

  private fun generateDna(rng: SeededRng, pack: EraPack, override: DnaProfile?): DnaProfile {
    if (override != null) return override
    val heritable = pack.conditions.conditions.filter { it.heritable }
    val preds = heritable.filter { rng.chance(0.1) }.map { it.id }
    return DnaProfile(
      longevity = clampInt((55 + rng.nextGaussian() * 18).roundToInt(), 15, 100),
      intellectCeiling = clampInt((60 + rng.nextGaussian() * 16).roundToInt(), 25, 100),
      athleticism = clampInt((55 + rng.nextGaussian() * 18).roundToInt(), 15, 100),
      mentalResilience = clampInt((58 + rng.nextGaussian() * 17).roundToInt(), 15, 100),
      appearance = clampInt((55 + rng.nextGaussian() * 18).roundToInt(), 15, 100),
      predispositions = preds,
    )
  }

  private fun inheritDna(rng: SeededRng, parentA: DnaProfile, parentB: DnaProfile?): DnaProfile {
    val b = parentB ?: parentA
    fun blend(v1: Int, v2: Int) = clampInt(((v1 + v2) / 2.0 + rng.nextGaussian() * 8).roundToInt(), 15, 100)
    val preds = (parentA.predispositions + b.predispositions).distinct().filter { rng.chance(0.45) }
    return DnaProfile(
      longevity = blend(parentA.longevity, b.longevity),
      intellectCeiling = blend(parentA.intellectCeiling, b.intellectCeiling),
      athleticism = blend(parentA.athleticism, b.athleticism),
      mentalResilience = blend(parentA.mentalResilience, b.mentalResilience),
      appearance = blend(parentA.appearance, b.appearance),
      predispositions = preds,
    )
  }

  private fun createNpc(
    rng: SeededRng,
    pack: EraPack,
    countryId: String,
    role: NpcRole,
    ageMonths: Int,
    lastNameOverride: String? = null,
    genderOverride: Gender? = null,
    dna: DnaProfile? = null,
    wealthOverride: Long? = null,
  ): Npc {
    val gender = genderOverride ?: rng.pick(Gender.entries)
    val (first, genLast) = pickNameForCountry(rng, pack, countryId, gender)
    val id = "npc_${role.name}_${rng.nextInt(100000, 999999)}"
    val baseAff = when (role) {
      NpcRole.parent, NpcRole.child -> rng.nextInt(72, 92)
      NpcRole.partner, NpcRole.spouse -> rng.nextInt(68, 86)
      NpcRole.sibling, NpcRole.friend -> rng.nextInt(58, 80)
      else -> rng.nextInt(42, 65)
    }.toDouble()

    return Npc(
      id = id,
      firstName = first,
      lastName = lastNameOverride ?: genLast,
      gender = gender,
      role = role,
      ageMonths = ageMonths,
      alive = true,
      health = clamp(rng.nextInt(65, 95) - max(0, getAgeYears(ageMonths) - 50) * 0.5, 25.0, 100.0),
      wealth = wealthOverride ?: rng.nextInt(5000, 80000).toLong(),
      personality = NpcPersonality(
        warmth = rng.nextInt(25, 95),
        ambition = rng.nextInt(20, 95),
        loyalty = rng.nextInt(30, 98),
        volatility = rng.nextInt(10, 85),
        honesty = rng.nextInt(30, 95),
      ),
      affection = baseAff,
      trust = clamp(baseAff - rng.nextInt(-5, 8), 20.0, 100.0),
      respect = clamp(baseAff - rng.nextInt(-5, 12), 20.0, 100.0),
      tension = rng.nextInt(0, 18).toDouble(),
      memories = emptyList(),
      parentingStyle = if (role == NpcRole.child) ParentingStyle.authoritative else null,
      dna = dna,
    )
  }

  fun createNewLife(options: CreateLifeOptions, pack: EraPack): GameState {
    val seed = (options.seed ?: System.currentTimeMillis()) and 0xFFFFFFFFL
    val rng = SeededRng(seed)
    val country = pack.country(options.countryId)
    val gender = options.gender ?: rng.pick(Gender.entries)
    val (genFirst, genLast) = pickNameForCountry(rng, pack, country.id, gender)
    val firstName = options.firstName?.trim()?.takeIf { it.isNotEmpty() } ?: genFirst
    val lastName = options.lastName?.trim()?.takeIf { it.isNotEmpty() } ?: genLast

    val (parentWealthBase, startCashBase, tierLabel) = when (options.familyWealthTier) {
      FamilyWealthTier.struggling -> Triple(6000L, 0L, "struggling")
      FamilyWealthTier.working -> Triple(28000L, 250L, "working class")
      FamilyWealthTier.middle -> Triple(95000L, 1000L, "middle class")
      FamilyWealthTier.upper_middle -> Triple(340000L, 5000L, "upper-middle class")
      FamilyWealthTier.wealthy -> Triple(1800000L, 25000L, "wealthy")
    }

    val dna = generateDna(rng, pack, options.dnaOverrides)
    val stats = PlayerStats(
      physicalHealth = clamp(78.0 + (dna.athleticism - 50) * 0.25, 20.0, 100.0),
      mentalHealth = clamp(76.0 + (dna.mentalResilience - 50) * 0.25, 20.0, 100.0),
      happiness = clamp((80 + rng.nextInt(-8, 10)).toDouble(), 20.0, 100.0),
      energy = 90.0,
      stress = 5.0,
      looks = clamp((dna.appearance + rng.nextInt(-8, 8)).toDouble(), 10.0, 100.0),
      intelligence = clamp(dna.intellectCeiling * 0.8 + rng.nextInt(-6, 10), 15.0, 100.0),
      charisma = clamp((52 + rng.nextInt(-15, 18)).toDouble(), 10.0, 100.0),
      discipline = clamp((50 + rng.nextInt(-15, 18)).toDouble(), 10.0, 100.0),
    )

    val mother = createNpc(
      rng, pack, country.id, NpcRole.parent, rng.nextInt(23, 38) * 12,
      lastNameOverride = lastName, genderOverride = Gender.female,
      wealthOverride = (parentWealthBase * country.costOfLivingMultiplier).roundToLong(),
    )
    val father = createNpc(
      rng, pack, country.id, NpcRole.parent, rng.nextInt(24, 41) * 12,
      lastNameOverride = lastName, genderOverride = Gender.male,
      wealthOverride = (parentWealthBase * country.costOfLivingMultiplier).roundToLong(),
    )
    val npcs = mutableListOf(mother, father)
    if (rng.chance(0.55)) {
      npcs.add(createNpc(rng, pack, country.id, NpcRole.sibling, rng.nextInt(1, 6) * 12, lastNameOverride = lastName, wealthOverride = 0L))
    }

    val startingCash = (startCashBase * country.costOfLivingMultiplier).roundToLong()
    val maxAp = computeTurnActionPoints(options.granularity, stats.energy, 0.0)

    return GameState(
      seed = seed,
      rngState = rng.getState(),
      turn = 0,
      config = WorldConfig(
        eraId = pack.meta.id,
        countryId = country.id,
        granularity = options.granularity,
        difficulty = options.difficulty,
        chaosLevel = clampInt(options.chaosLevel, 0, 100),
        economyVolatility = clampInt(options.economyVolatility, 0, 100),
        ironman = options.ironman,
      ),
      world = WorldState(currentYear = pack.meta.startYear, currentMonth = 1),
      player = PlayerState(
        firstName = firstName,
        lastName = lastName,
        gender = gender,
        familyWealthTier = options.familyWealthTier,
        stats = stats,
        hidden = HiddenMetrics(immuneResilience = clamp(55.0 + (dna.longevity - 50) * 0.4, 20.0, 100.0)),
        dna = dna,
        skills = SkillId.entries.associateWith { 0.0 },
        schedule = getDefaultScheduleForAge(0),
        actionPoints = maxAp,
        maxActionPoints = maxAp,
        finance = FinanceState(
          cash = startingCash,
          netWorthHistory = listOf(NetWorthPoint(0, startingCash)),
        ),
      ),
      npcs = npcs,
      timeline = listOf(
        TimelineEntry(
          id = "tl_birth",
          turn = 0,
          ageMonths = 0,
          year = pack.meta.startYear,
          category = "milestone",
          title = "Born in ${country.flag} ${country.name}",
          detail = "$firstName $lastName was born into a $tierLabel family in ${country.name}.",
        ),
      ),
      peakNetWorth = startingCash,
    )
  }

  fun calculateAnnualIncomeTax(annualIncome: Long, country: CountryDef): Long {
    if (annualIncome <= 0L) return 0L
    var tax = 0.0
    var prevCap = 0L
    for (b in country.taxBrackets) {
      val cap = b.upTo ?: Long.MAX_VALUE
      if (annualIncome <= prevCap) break
      val taxable = min(annualIncome, cap) - prevCap
      tax += taxable * b.rate
      prevCap = cap
    }
    return max(0L, tax.roundToLong())
  }

  fun calculateNetWorth(state: GameState, pack: EraPack): Long {
    val invTotal = state.player.finance.investments.fold(0L) { acc, it -> safeAddMoney(acc, it.balance) }
    val country = pack.country(state.config.countryId)
    val housing = pack.assets.housing.firstOrNull { it.id == state.player.finance.housingId }
    val homeValue = if (state.player.finance.ownsHousing && housing != null) {
      clampMoney((housing.purchasePrice * country.costOfLivingMultiplier).roundToLong())
    } else 0L
    val finance = state.player.finance
    val debts = finance.personalLoans.fold(safeAddMoney(finance.mortgageBalance, state.player.education.studentLoanBalance)) { total, loan ->
      safeAddMoney(total, loan.balance)
    }
    val belongings = finance.possessions.fold(0L) { total, item -> safeAddMoney(total, item.resaleValue(country)) }
    val property = safeAddMoney(safeAddMoney(invTotal, homeValue), belongings)
    return safeAddMoney(safeAddMoney(finance.cash, property), -debts)
  }

  private fun severeFamilyPressure(housingId: String, hardshipMonths: Int) =
    housingId == "parents_home" && hardshipMonths >= 3

  private fun livingCosts(finance: FinanceState, country: CountryDef) = when (finance.housingId) {
    "emergency_shelter" -> country.baseAnnualLivingCost * 0.45
    "unhoused" -> country.baseAnnualLivingCost * 0.62
    else -> country.baseAnnualLivingCost.toDouble()
  }

  data class CashflowForecast(
    val income: Long,
    val cashExpenses: Long,
    val projectedCash: Long,
    val financedTuition: Long,
  )

  fun estimateNextTurnCashflow(state: GameState, pack: EraPack): CashflowForecast {
    val gran = state.config.granularity
    val dt = getDt(gran)
    val monthsToAdd = getMonthsPerTurn(gran)
    val nextAgeMonths = state.player.ageMonths + monthsToAdd
    val nextAgeYears = getAgeYears(nextAgeMonths)
    val country = pack.country(state.config.countryId)
    val diff = diffMods(state.config.difficulty)

    var turnGross = 0L
    var turnTax = 0L
    val job = state.player.job
    if (job != null) {
      turnGross = (job.annualSalary * dt).roundToLong()
      turnTax = (calculateAnnualIncomeTax(job.annualSalary, country) * dt).roundToLong()
    } else if (state.player.retired) {
      val retireThresholdMonths = country.retirementAge * 12
      val eligibleMonths = max(0, nextAgeMonths - max(state.player.ageMonths, retireThresholdMonths))
      if (eligibleMonths > 0) {
        val pensionGross = (country.annualPensionBase * (eligibleMonths / 12.0)).roundToLong()
        val pensionTax = if (pensionGross > 0L) {
          (calculateAnnualIncomeTax(country.annualPensionBase, country) * (pensionGross.toDouble() / max(1L, country.annualPensionBase).toDouble())).roundToLong()
        } else 0L
        turnGross = pensionGross
        turnTax = pensionTax
      }
    }
    val turnNet = turnGross - turnTax

    var turnBills = 0L
    val macro = pack.meta.macroStates.getValue(state.world.macroState)
    val inflation = (state.world.inflationIndex * scaleCompoundFactor(macro.annualInflation, gran)).round4()
    val housingDef = pack.assets.housing.firstOrNull { it.id == state.player.finance.housingId }
    if (nextAgeYears >= 18) {
      val baseLiving = livingCosts(state.player.finance, country) * inflation * diff.costMult * dt
      val rent = if (!state.player.finance.ownsHousing && housingDef != null) {
        housingDef.annualRent * country.costOfLivingMultiplier * inflation * diff.costMult * dt
      } else 0.0
      val depCount = state.npcs.count { it.alive && it.role == NpcRole.child && getAgeYears(it.ageMonths) < 18 }
      val depCost = depCount * 4500.0 * country.costOfLivingMultiplier * diff.costMult * dt
      val habAnnual = state.player.habits.sumOf { h ->
        pack.conditions.habits.firstOrNull { it.id == h.id }?.annualCost ?: 0L
      }
      val habCost = habAnnual * country.costOfLivingMultiplier * dt
      turnBills = safeAddMoney(turnBills, (baseLiving + rent + depCost + habCost).roundToLong())
    }
    val finance = state.player.finance
    val education = state.player.education
    val program = pack.education.firstOrNull { it.id == education.currentProgramId }
    val tuition = program?.let { (it.annualTuition * country.tuitionMultiplier * dt).roundToLong() } ?: 0L
    var cash = safeAddMoney(finance.cash, turnNet - turnBills)
    var financedTuition = 0L
    if (cash >= tuition && tuition > 0L) {
      cash = safeAddMoney(cash, -tuition)
      turnBills = safeAddMoney(turnBills, tuition)
    } else {
      financedTuition = tuition
    }
    val completesProgram = program != null &&
      education.monthsCompletedInProgram + monthsToAdd >= program.durationYears * 12
    val loan = safeAddMoney(education.studentLoanBalance, financedTuition)
    if (loan > 0L && (program == null || completesProgram) && turnNet > 0L) {
      val balance = clampMoney((loan * scaleCompoundFactor(0.055, gran)).roundToLong())
      val payment = min(balance, (turnNet * 0.12).roundToLong())
      if (cash >= payment) {
        cash = safeAddMoney(cash, -payment)
        turnBills = safeAddMoney(turnBills, payment)
      }
    }
    if (state.player.finance.mortgageBalance > 0L) {
      val mAfter = (state.player.finance.mortgageBalance * scaleCompoundFactor(0.06, gran)).roundToLong()
      val pay = min(mAfter, (mAfter * 0.08 * dt + 1200.0 * dt).roundToLong())
      cash = safeAddMoney(cash, -pay)
      turnBills = safeAddMoney(turnBills, pay)
    }
    val repayment = settleLoans(finance, cash, country, gran)
    cash = repayment.cash
    turnBills = safeAddMoney(turnBills, repayment.paid)
    if (nextAgeYears >= 18 && cash < 0L) {
      val withInterest = clampMoney((cash * scaleCompoundFactor(0.12 * diff.costMult, gran)).roundToLong())
      turnBills = safeAddMoney(turnBills, cash - withInterest)
      cash = withInterest
    }
    return CashflowForecast(turnNet, turnBills, cash, financedTuition)
  }

  data class WeeklyCommitments(
    val clubHours: Int,
    val projectHours: Int,
    val hustleHours: Int,
    val leisureHours: Int,
  )

  fun allocateCommitments(
    player: PlayerState,
    schedule: WeeklySchedule = player.schedule,
  ): WeeklyCommitments {
    val available = schedule.hustleAndSkills + schedule.leisureAndRest
    val clubHours = min(available, player.activeClub?.weeklyHours ?: 0)
    val projectRequest = player.activeProject?.takeIf { it.progress < 100.0 }?.weeklyHours ?: 0
    val projectHours = min(available - clubHours, projectRequest)
    val reserved = clubHours + projectHours
    return WeeklyCommitments(
      clubHours,
      projectHours,
      max(0, schedule.hustleAndSkills - reserved),
      schedule.leisureAndRest - max(0, reserved - schedule.hustleAndSkills),
    )
  }

  data class PaidActivity(val title: String, val reward: Long, val monthsUntilAvailable: Int)

  fun paidActivity(state: GameState, country: CountryDef): PaidActivity? {
    val player = state.player
    val age = getAgeYears(player.ageMonths)
    if (age < 6) return null
    val adult = age >= country.legalAdultAge
    val basePay = if (adult) 250L else 30L
    val lastAge = player.lastPaidActivityAgeMonths
    return PaidActivity(
      if (adult) "Take a paid odd job" else "Do chores for allowance",
      max(1L, (basePay * country.costOfLivingMultiplier).roundToLong()),
      if (lastAge == null) 0 else max(0, 3 - (player.ageMonths - lastAge)),
    )
  }

  data class ProjectOffer(val type: ProjectType, val title: String, val cost: Long)

  fun projectOffers(ageYears: Int, country: CountryDef): List<ProjectOffer> = buildList {
    if (ageYears in 6..14) add(ProjectOffer(ProjectType.lemonade_stall, "Neighborhood Lemonade Stall", 15L))
    if (ageYears in 13..18) add(ProjectOffer(ProjectType.passion_zine, "Student Zine & Podcast", 40L))
    if (ageYears in 18..120) add(ProjectOffer(ProjectType.portfolio_app, "Independent Portfolio Project", 200L))
  }.map { it.copy(cost = max(1L, (it.cost * country.costOfLivingMultiplier).roundToLong())) }

  private data class DiffMods(val costMult: Double, val stressMult: Double, val hireBonus: Double, val riskMult: Double)

  private fun diffMods(d: Difficulty): DiffMods = when (d) {
    Difficulty.forgiving -> DiffMods(0.85, 0.80, 0.10, 0.70)
    Difficulty.realistic -> DiffMods(1.00, 1.00, 0.00, 1.00)
    Difficulty.hardcore -> DiffMods(1.20, 1.25, -0.10, 1.35)
  }

  fun getRequiredWorkOrSchoolHours(state: GameState, pack: EraPack): Int {
    var hours = 0
    state.player.education.currentProgramId?.let { progId ->
      pack.education.firstOrNull { it.id == progId }?.let { hours += it.weeklyStudyHoursRequired }
    }
    state.player.job?.let { job ->
      val tier = pack.careers.firstOrNull { it.id == job.trackId }?.tiers?.firstOrNull { it.id == job.tierId }
      if (tier != null) hours += tier.weeklyHoursRequired
    }
    return min(70, hours)
  }

  private fun applyStatDeltas(s: PlayerStats, d: StatDeltaMap): PlayerStats = PlayerStats(
    physicalHealth = clamp(s.physicalHealth + (d.physicalHealth ?: 0.0), 0.0, 100.0),
    mentalHealth = clamp(s.mentalHealth + (d.mentalHealth ?: 0.0), 0.0, 100.0),
    happiness = clamp(s.happiness + (d.happiness ?: 0.0), 0.0, 100.0),
    energy = clamp(s.energy + (d.energy ?: 0.0), 0.0, 100.0),
    stress = clamp(s.stress + (d.stress ?: 0.0), 0.0, 100.0),
    looks = clamp(s.looks + (d.looks ?: 0.0), 0.0, 100.0),
    intelligence = clamp(s.intelligence + (d.intelligence ?: 0.0), 0.0, 100.0),
    charisma = clamp(s.charisma + (d.charisma ?: 0.0), 0.0, 100.0),
    discipline = clamp(s.discipline + (d.discipline ?: 0.0), 0.0, 100.0),
  )

  fun advanceTurn(state: GameState, pack: EraPack): GameState {
    if (!state.player.alive || state.pendingEvent != null) return state

    val rng = SeededRng(state.rngState)
    val gran = state.config.granularity
    val monthsToAdd = getMonthsPerTurn(gran)
    val dt = getDt(gran)
    val prevStage = getLifeStage(state.player.ageMonths)
    val nextAgeMonths = state.player.ageMonths + monthsToAdd
    val nextAgeYears = getAgeYears(nextAgeMonths)
    val nextStage = getLifeStage(nextAgeMonths)
    val nextTurn = state.turn + 1
    val (nextYear, nextMonth) = advanceCalendar(state.world.currentYear, state.world.currentMonth, monthsToAdd)
    val country = pack.country(state.config.countryId)

    val logs = mutableListOf<TimelineEntry>()
    fun pushLog(cat: String, title: String, detail: String) {
      logs.add(TimelineEntry("tl_${nextTurn}_${logs.size}", nextTurn, nextAgeMonths, nextYear, cat, title, detail))
    }

    val schedule = if ((prevStage == LifeStage.INFANCY && nextStage == LifeStage.CHILDHOOD) ||
      (prevStage == LifeStage.CHILDHOOD && nextStage == LifeStage.ADOLESCENCE)
    ) {
      getDefaultScheduleForAge(nextAgeMonths)
    } else {
      normalizeSchedule(state.player.schedule)
    }

    val commitments = allocateCommitments(
      if (nextAgeYears >= 18) state.player.copy(activeClub = null) else state.player,
      schedule,
    )

    // 1. Macro economy
    var nextMacro = state.world.macroState
    val macroShiftChance = scaleAnnualProbability(0.08 + (state.config.economyVolatility / 100.0) * 0.14, gran)
    if (rng.chance(macroShiftChance)) {
      nextMacro = when (nextMacro) {
        MacroStateId.normal -> rng.pick(listOf(MacroStateId.boom, MacroStateId.recession))
        MacroStateId.boom -> rng.pick(listOf(MacroStateId.normal, MacroStateId.recession))
        MacroStateId.recession -> rng.pick(listOf(MacroStateId.normal, MacroStateId.boom))
      }
      val label = pack.meta.macroStates.getValue(nextMacro).label
      pushLog("world", "Economy Shifted: $label", "Markets entered a ${label.lowercase()} phase.")
    }
    val macroDef = pack.meta.macroStates.getValue(nextMacro)
    val nextInflation = (state.world.inflationIndex * scaleCompoundFactor(macroDef.annualInflation, gran)).round4()

    // 2. Schedule tick
    val reqHours = getRequiredWorkOrSchoolHours(state, pack)
    val overtime = max(0, schedule.workOrSchool - reqHours)
    var sPhys = 0.0; var sMent = 0.0; var sHap = 0.0; var sEng = 0.0
    var sStress = 0.0; var sLooks = 0.0; var sInt = 0.0; var sDisc = 0.0
    var sleepDebtDelta = 0.0; var burnoutDelta = 0.0

    if (schedule.sleep < 49) {
      val def = 49 - schedule.sleep
      sleepDebtDelta += def * 1.8 * dt
      sStress += def * 1.4 * dt
      sPhys -= def * 0.7 * dt
      sMent -= def * 0.9 * dt
      sEng -= def * 1.6 * dt
    } else {
      val rec = min(14, schedule.sleep - 47)
      sleepDebtDelta -= rec * 2.2 * dt
      sEng += rec * 1.2 * dt
      sPhys += rec * 0.25 * dt
    }

    val totalGrind = schedule.workOrSchool + commitments.hustleHours + commitments.clubHours + commitments.projectHours
    if (totalGrind > 50) {
      val ex = totalGrind - 50
      sStress += ex * 0.95 * dt
      burnoutDelta += ex * 0.75 * dt
      sDisc += min(8.0, ex * 0.25) * dt
    } else {
      burnoutDelta -= 6.0 * dt
    }

    val focusXp = commitments.hustleHours * 0.85 * dt
    if (commitments.hustleHours >= 10) sInt += min(4.0, commitments.hustleHours * 0.12) * dt

    if (schedule.fitnessAndSelfCare >= 5) {
      val fit = min(18, schedule.fitnessAndSelfCare - 3)
      sPhys += fit * 0.75 * dt
      sMent += fit * 0.45 * dt
      sLooks += fit * 0.35 * dt
      sStress -= fit * 0.65 * dt
    } else if (schedule.fitnessAndSelfCare < 3) {
      sPhys -= 4.0 * dt
    }

    if (commitments.leisureHours >= 18) {
      val rest = min(25, commitments.leisureHours - 14)
      sStress -= rest * 0.85 * dt
      sHap += rest * 0.55 * dt
      burnoutDelta -= rest * 0.6 * dt
    } else if (commitments.leisureHours < 10) {
      val dep = 10 - commitments.leisureHours
      sHap -= dep * 1.4 * dt
      sStress += dep * 1.1 * dt
      burnoutDelta += dep * 1.2 * dt
    }

    val passiveNpcAffection = if (schedule.familyAndSocial >= 18) {
      sHap += 3.0 * dt
      sMent += 3.0 * dt
      min(12.0, (schedule.familyAndSocial - 14) * 0.4) * dt
    } else if (schedule.familyAndSocial < 10) {
      sMent -= 4.0 * dt
      -(10 - schedule.familyAndSocial) * 0.8 * dt
    } else 0.0

    if (state.player.hidden.burnout >= 65.0) {
      sMent -= 8.0 * dt; sHap -= 8.0 * dt; sEng -= 10.0 * dt
    }

    // 3. Health, habits, conditions, mortality
    var hPhys = 0.0; var hMent = 0.0; var hStress = 0.0; var hInt = 0.0
    var habitAnnualCost = 0L
    if (nextAgeYears >= 42) {
      hPhys -= (nextAgeYears - 40) * 0.14 * (1.25 - state.player.dna.longevity / 200.0) * dt
    }

    val nextHabits = state.player.habits.map { h ->
      pack.conditions.habits.firstOrNull { it.id == h.id }?.let { def ->
        val intensity = 0.5 + h.strength / 200.0
        hPhys += def.annualHealthDelta * intensity * dt
        hMent += def.annualMentalDelta * intensity * dt
        hStress += def.annualStressDelta * intensity * dt
        def.annualIntelligenceDelta?.let { hInt += it * intensity * dt }
        habitAnnualCost += def.annualCost
      }
      h.copy(strength = clamp(h.strength + 4.0 * dt, 10.0, 100.0))
    }.toMutableList()

    if (state.player.stats.stress >= 78.0 && nextAgeYears >= country.legalAdultAge) {
      if (rng.chance(scaleAnnualProbability(0.18, gran))) {
        val candidates = listOf("doomscrolling", "smoking", "heavy_drinking").filter { id ->
          nextHabits.none { it.id == id }
        }
        if (candidates.isNotEmpty()) {
          val picked = rng.pick(candidates)
          pack.conditions.habits.firstOrNull { it.id == picked }?.let { def ->
            nextHabits.add(ActiveHabit(picked, 30.0))
            pushLog("health", "Coping Habit Formed: ${def.name}", "Sustained high stress pushed you into ${def.name.lowercase()}.")
          }
        }
      }
    }

    val nextConditions = mutableListOf<ActiveCondition>()
    for (cond in state.player.conditions) {
      val def = pack.conditions.conditions.firstOrNull { it.id == cond.id } ?: continue
      val mit = if (cond.treated) 0.35 else 1.0
      hPhys -= def.annualHealthDrain * mit * dt
      hMent -= def.annualMentalDrain * mit * dt
      if (!def.chronic && rng.chance(scaleAnnualProbability(if (cond.treated) 0.9 else 0.65, gran))) {
        pushLog("health", "Recovered from ${def.name}", "Your ${def.name.lowercase()} has cleared up.")
        continue
      }
      nextConditions.add(cond)
    }

    if (nextAgeYears >= 35) {
      for (predId in state.player.dna.predispositions) {
        if (nextConditions.none { it.id == predId } && rng.chance(scaleAnnualProbability(0.025, gran))) {
          pack.conditions.conditions.firstOrNull { it.id == predId }?.let { def ->
            nextConditions.add(ActiveCondition(predId, nextAgeMonths, def.severity, false))
            pushLog("health", "Diagnosed: ${def.name}", "A routine check reveals ${def.name} (linked to family genetics).")
          }
        }
      }
    }

    var annualMort = 0.0004
    if (nextAgeYears >= 50) {
      val offset = (state.player.dna.longevity - 55) * 0.16
      val effAge = max(40.0, nextAgeYears - offset)
      if (effAge > 55.0) annualMort += 0.002 * 1.115.pow(effAge - 55.0)
    }
    when {
      state.player.stats.physicalHealth <= 10.0 -> annualMort += 0.45
      state.player.stats.physicalHealth <= 25.0 -> annualMort += 0.12
      state.player.stats.physicalHealth <= 40.0 -> annualMort += 0.025
    }
    for (c in nextConditions) {
      pack.conditions.conditions.firstOrNull { it.id == c.id }?.let { def ->
        annualMort += def.annualMortalityRisk * (if (c.treated) 0.35 else 1.0)
      }
    }
    if (nextAgeYears >= 105) annualMort = max(annualMort, 0.55)
    val diff = diffMods(state.config.difficulty)
    annualMort = clamp(annualMort * diff.riskMult, 0.0, 0.95)

    val projHealth = clamp(state.player.stats.physicalHealth + sPhys + hPhys, 0.0, 100.0)
    val died = projHealth <= 0.0 || rng.chance(scaleAnnualProbability(annualMort, gran))
    val causeOfDeath = if (!died) null else {
      val lethal = nextConditions.mapNotNull { c -> pack.conditions.conditions.firstOrNull { it.id == c.id } }
        .firstOrNull { it.annualMortalityRisk >= 0.01 }
      when {
        lethal != null -> "Complications from ${lethal.name}"
        nextAgeYears >= 72 -> "Peaceful natural causes"
        projHealth <= 5.0 -> "Severe health collapse"
        else -> "Sudden medical emergency"
      }
    }

    // 4. Education, Career, Finance
    val nextSkills = state.player.skills.toMutableMap()
    nextSkills[state.player.focusSkill] = clamp((nextSkills[state.player.focusSkill] ?: 0.0) + focusXp, 0.0, 100.0)

    var curProgId = state.player.education.currentProgramId
    var progMonths = state.player.education.monthsCompletedInProgram
    var gpa = state.player.education.gpa
    val completedProgs = state.player.education.completedProgramIds.toMutableList()
    var loanBal = state.player.education.studentLoanBalance

    if (curProgId == null) {
      if (nextAgeYears in 5..12 && !completedProgs.contains("primary")) {
        curProgId = "primary"; progMonths = 0
        pushLog("milestone", "Started Primary School", "Your school years officially begin.")
      } else if (nextAgeYears in 13..17 && completedProgs.contains("primary") && !completedProgs.contains("high_school")) {
        curProgId = "high_school"; progMonths = 0
        pushLog("milestone", "Started High School", "Welcome to adolescence, exams, and new ambitions.")
      }
    }

    var tuitionCost = 0L
    var eduHap = 0.0
    if (curProgId != null) {
      val prog = pack.education.firstOrNull { it.id == curProgId }
      if (prog != null) {
        progMonths += monthsToAdd
        tuitionCost = (prog.annualTuition * country.tuitionMultiplier * dt).roundToLong()
        val studyFactor = (state.player.stats.intelligence + state.player.stats.discipline) / 200.0
        gpa = clamp((gpa + (studyFactor - 0.45 + overtime * 0.01) * 0.4 * dt), 0.5, 4.0).round2()
        for (sk in prog.skillTags) {
          nextSkills[sk] = clamp((nextSkills[sk] ?: 0.0) + 7.0 * dt, 0.0, 100.0)
        }
        if (progMonths >= prog.durationYears * 12) {
          completedProgs.add(prog.id)
          curProgId = null; progMonths = 0; eduHap += 8.0
          pushLog("milestone", "Graduated: ${prog.name}", "Finished with a ${"%.2f".format(gpa)} GPA.")
        }
      }
    }

    var clubPhys = 0.0
    var teammateBoostNpcId: String? = null
    var nextClub = state.player.activeClub
    if (nextClub != null) {
      if (nextAgeYears >= 18) {
        pushLog("milestone", "Aged Out of ${nextClub.name}", "Finished ${nextClub.completedTerms} term(s) before adulthood.")
        nextClub = null
      } else {
        val (_, primarySkill) = clubMeta(nextClub.clubId)
        val nextTermMonths = nextClub.termMonthsCompleted + monthsToAdd
        val nextClubProgress = clamp(nextClub.progress + commitments.clubHours * 11.0 * dt, 0.0, 100.0)
        nextSkills[primarySkill] = clamp((nextSkills[primarySkill] ?: 0.0) + commitments.clubHours * 1.1 * dt, 0.0, 100.0)
        if (nextClub.clubId == ClubId.soccer && commitments.clubHours > 0) clubPhys += 1.2 * dt
        if (commitments.clubHours > 0) teammateBoostNpcId = nextClub.teammateNpcId
        val becameReady = !nextClub.showcaseReady && nextTermMonths >= nextClub.termLengthMonths
        if (becameReady) {
          pushLog("milestone", "${nextClub.name}: Term-End Showcase Ready!", "Open Activities to enter your term showcase.")
        }
        nextClub = nextClub.copy(
          termMonthsCompleted = nextTermMonths,
          progress = nextClubProgress,
          showcaseReady = nextClub.showcaseReady || becameReady,
        )
      }
    }

    var nextProject = state.player.activeProject
    if (nextProject != null) {
      val nextProjProg = clamp(nextProject.progress + commitments.projectHours * 6.0 * dt, 0.0, 100.0)
      val crossedHalf = nextProject.milestonesHit == 0 && nextProjProg >= 50.0
      if (crossedHalf) {
        pushLog("milestone", "${nextProject.title} Halfway Done", "Your project reached 50% progress.")
      }
      nextProject = nextProject.copy(
        progress = nextProjProg,
        milestonesHit = if (crossedHalf) 1 else nextProject.milestonesHit,
      )
    }

    var nextJob = state.player.job
    var grossAnnual = 0L
    var jobStress = 0.0; var jobHap = 0.0
    var pensionTurnGross = 0L
    if (nextJob != null) {
      val tier = pack.careers.firstOrNull { it.id == nextJob.trackId }?.tiers?.firstOrNull { it.id == nextJob.tierId }
      val perfDrift = (state.player.stats.discipline - 50.0) * 0.15 + overtime * 0.9 -
        (if (state.player.hidden.burnout > 65.0) 12.0 else 0.0)
      val nextPerf = clamp(nextJob.performance + perfDrift * dt, 10.0, 100.0)
      val nextSal = if (nextPerf >= 65.0) {
        (nextJob.annualSalary * scaleCompoundFactor(macroDef.salaryGrowthBonus + 0.01, gran)).roundToLong()
      } else nextJob.annualSalary
      grossAnnual += nextSal

      if (tier != null) {
        jobStress += tier.annualStressImpact * dt
        for ((sk, rate) in tier.skillGrowth) {
          nextSkills[sk] = clamp((nextSkills[sk] ?: 0.0) + rate * dt, 0.0, 100.0)
        }
      }

      val layoffRisk = (if (nextPerf < 30.0) 0.25 else 0.025) * macroDef.layoffRiskMultiplier * diff.riskMult
      if (rng.chance(scaleAnnualProbability(layoffRisk, gran))) {
        pushLog("career", "Laid Off from ${nextJob.title}", "Budget cuts and restructuring cost you your position.")
        nextJob = null; jobStress += 14.0; jobHap -= 12.0
      } else {
        nextJob = nextJob.copy(
          annualSalary = nextSal,
          performance = nextPerf,
          monthsInTrack = nextJob.monthsInTrack + monthsToAdd,
          monthsInTier = nextJob.monthsInTier + monthsToAdd,
        )
      }
    } else if (state.player.retired) {
      val retireThresholdMonths = country.retirementAge * 12
      val eligibleMonths = max(0, nextAgeMonths - max(state.player.ageMonths, retireThresholdMonths))
      if (eligibleMonths > 0) {
        pensionTurnGross = (country.annualPensionBase * (eligibleMonths / 12.0)).roundToLong()
      }
    }

    val annualTax = calculateAnnualIncomeTax(grossAnnual, country)
    val pensionTax = if (pensionTurnGross > 0L) {
      (calculateAnnualIncomeTax(country.annualPensionBase, country) * (pensionTurnGross.toDouble() / max(1L, country.annualPensionBase).toDouble())).roundToLong()
    } else 0L
    val turnGross = (grossAnnual * dt).roundToLong() + pensionTurnGross
    val turnTax = (annualTax * dt).roundToLong() + pensionTax
    val turnNet = turnGross - turnTax

    val housingDef = pack.assets.housing.firstOrNull { it.id == state.player.finance.housingId }
    if (housingDef != null) {
      jobHap += housingDef.happinessBonus * 0.35 * dt
      jobStress -= housingDef.stressReduction * 0.35 * dt
    }

    var turnExpenses = 0L
    if (nextAgeYears >= 18) {
      val baseLiving = livingCosts(state.player.finance, country) * nextInflation * diff.costMult * dt
      val rent = if (!state.player.finance.ownsHousing && housingDef != null) {
        housingDef.annualRent * country.costOfLivingMultiplier * nextInflation * diff.costMult * dt
      } else 0.0
      val depCount = state.npcs.count { it.alive && it.role == NpcRole.child && getAgeYears(it.ageMonths) < 18 }
      val depCost = depCount * 4500.0 * country.costOfLivingMultiplier * diff.costMult * dt
      val habCost = habitAnnualCost * country.costOfLivingMultiplier * dt
      turnExpenses = (baseLiving + rent + depCost + habCost).roundToLong()
    }

    var cash = safeAddMoney(state.player.finance.cash, turnNet - turnExpenses)
    if (tuitionCost > 0) {
      if (cash >= tuitionCost) {
        cash = safeAddMoney(cash, -tuitionCost); turnExpenses = safeAddMoney(turnExpenses, tuitionCost)
      } else {
        loanBal = safeAddMoney(loanBal, tuitionCost)
      }
    }
    if (loanBal > 0) {
      loanBal = clampMoney((loanBal * scaleCompoundFactor(0.055, gran)).roundToLong())
      if (curProgId == null && grossAnnual > 0) {
        val pay = min(loanBal, (turnNet * 0.12).roundToLong())
        if (pay > 0 && cash >= pay) {
          cash = safeAddMoney(cash, -pay); loanBal = safeAddMoney(loanBal, -pay); turnExpenses = safeAddMoney(turnExpenses, pay)
        }
      }
    }

    var mortgage = state.player.finance.mortgageBalance
    if (mortgage > 0) {
      mortgage = clampMoney((mortgage * scaleCompoundFactor(0.06, gran)).roundToLong())
      val pay = min(mortgage, (mortgage * 0.08 * dt + 1200.0 * dt).roundToLong())
      cash = safeAddMoney(cash, -pay); mortgage = safeAddMoney(mortgage, -pay); turnExpenses = safeAddMoney(turnExpenses, pay)
    }

    val volScale = (0.5 + state.config.economyVolatility / 100.0) * sqrt(dt)
    val nextInvestments = state.player.finance.investments.map { h ->
      val asset = pack.assets.investments.firstOrNull { it.id == h.assetId }
      if (asset == null) h else {
        val blended = (asset.expectedAnnualReturn + macroDef.annualMarketReturn) / 2.0
        val shock = rng.nextGaussian() * asset.annualVolatility * volScale
        val nextBal = clampMoney(max(0L, (h.balance * (1.0 + blended * dt + shock)).roundToLong()))
        h.copy(balance = nextBal)
      }
    }

    val finance = state.player.finance
    val repayment = settleLoans(finance, cash, country, gran)
    cash = repayment.cash
    turnExpenses = safeAddMoney(turnExpenses, repayment.paid)
    var credit = finance.creditScore - repayment.creditLoss
    var housingId = finance.housingId
    var ownsHousing = finance.ownsHousing
    val familyAvailable = hasFamilyHousing(state.npcs)
    var hardshipMonths = finance.hardshipMonths
    var debtStress = 0.0; var debtHap = 0.0; var debtMent = 0.0; var debtPhys = 0.0
    if (nextAgeYears >= 18) {
      if (repayment.collections) {
        val predatory = repayment.loans.any { it.type == LoanType.shady && it.lateMonths >= 3 }
        debtStress += if (predatory) 18.0 else 9.0
        val detail = if (predatory) "The unlicensed lender began harassing you about missed payments." else "A lender sent your overdue account to collections."
        pushLog("finance", "Loan Payments Overdue", detail)
      }
      val severe = cash < -(country.baseAnnualLivingCost * 0.5).roundToLong()
      hardshipMonths = if (severe) (hardshipMonths + monthsToAdd).coerceAtMost(1200) else 0
      if (cash < 0) {
        cash = clampMoney((cash * scaleCompoundFactor(0.12 * diff.costMult, gran)).roundToLong())
        credit -= (18 * diff.riskMult * dt).roundToInt()

        val debtRatio = kotlin.math.abs(cash).toDouble() / max(1L, country.baseAnnualLivingCost).toDouble()
        debtStress += min(34.0, 10.0 + debtRatio * 7.0) * dt
        debtHap -= min(28.0, 8.0 + debtRatio * 6.0) * dt
        debtMent -= min(20.0, 5.0 + debtRatio * 4.0) * dt
        if (debtRatio >= 1.5 && credit <= 350) debtPhys -= 8.0 * dt

        if (severe && hardshipMonths >= 3 && housingId !in setOf("parents_home", "unhoused", "emergency_shelter")) {
          val fallback = if (familyAvailable) "parents_home" else "unhoused"
          val after = if (familyAvailable) "Family took you in." else "With no family able to help, you lost stable housing."
          val title = if (ownsHousing) "Home Foreclosed" else "Evicted Due to Unpaid Rent"
          if (ownsHousing) {
            val value = housingDef?.let { (it.purchasePrice * country.costOfLivingMultiplier).roundToLong() } ?: 0L
            cash = safeAddMoney(cash, value - mortgage)
            mortgage = 0L
            ownsHousing = false
          }
          housingId = fallback
          debtStress += 12.0; debtHap -= 12.0
          pushLog("finance", title, after)
        }
      } else if (turnNet > turnExpenses) {
        credit += (8 * dt).roundToInt()
      }
      if (housingId == "parents_home" && severe && hardshipMonths >= 3) {
        if (finance.hardshipMonths < 3) {
          pushLog("relationship", "Pressure at Home", "Your household asked you to address the growing debt. Employment, selling assets, or emergency aid can help.")
        }
        debtStress += 12.0 * dt
        if (hardshipMonths >= 9 && !familyAvailable) {
          housingId = "unhoused"
          pushLog("finance", "Lost Family Housing", "You need somewhere to stay. Open Assets to find temporary shelter and emergency aid.")
        }
      }
      if (housingId == "unhoused" || housingId == "emergency_shelter") {
        val outdoors = housingId == "unhoused"
        debtStress += (if (outdoors) 33.0 else 14.0) * dt
        debtPhys -= (if (outdoors) 27.0 else 5.0) * dt
        debtMent -= (if (outdoors) 24.0 else 9.0) * dt
        sEng -= (if (outdoors) 19.0 else 6.0) * dt
      }
      credit = clampInt(credit, 300, 850)
    }

    // 5. NPCs tick
    var npcHap = 0.0; var npcMent = 0.0
    val nextNpcs = state.npcs.map { npc ->
      if (!npc.alive) return@map npc
      val nAgeM = npc.ageMonths + monthsToAdd
      val nAgeY = getAgeYears(nAgeM)
      val nextMems = npc.memories.mapNotNull { m ->
        val decayed = m.sentimentDelta * (1.0 - m.decayRate).pow(dt)
        if (kotlin.math.abs(decayed) < 0.5) null else m.copy(sentimentDelta = decayed)
      }
      val memInf = clamp(nextMems.sumOf { it.sentimentDelta } * 0.08, -6.0, 6.0) * dt
      val teammateAff = if (npc.id == teammateBoostNpcId) 2.5 * dt else 0.0
      val nAff = clamp(npc.affection + passiveNpcAffection + memInf + teammateAff, 0.0, 100.0)
      val financialFriction = if (severeFamilyPressure(housingId, hardshipMonths) && npc.role == NpcRole.parent) 26.0 * dt else 0.0
      val nTen = clamp(npc.tension - 2.0 * dt + (if (passiveNpcAffection < 0) 2.0 * dt else 0.0) + financialFriction, 0.0, 100.0)
      var nHealth = npc.health
      if (nAgeY >= 55) nHealth = clamp(nHealth - (nAgeY - 50) * 0.25 * dt, 0.0, 100.0)
      val npcDeathRisk = if (nAgeY < 60) 0.001 else clamp(0.004 * 1.11.pow(nAgeY - 60) + (if (nHealth < 20.0) 0.15 else 0.0), 0.0, 0.7)
      if (nHealth <= 0.0 || rng.chance(scaleAnnualProbability(npcDeathRisk, gran))) {
        pushLog("relationship", "${npc.firstName} ${npc.lastName} Passed Away", "Your ${npc.role.name}, ${npc.firstName}, passed away at age $nAgeY.")
        npcHap -= if (npc.affection >= 60.0) 18.0 else 8.0
        npcMent -= if (npc.affection >= 60.0) 12.0 else 4.0
        npc.copy(ageMonths = nAgeM, health = 0.0, alive = false, memories = nextMems)
      } else {
        npc.copy(ageMonths = nAgeM, health = nHealth, affection = nAff, tension = nTen, memories = nextMems)
      }
    }

    // 6. Merge stats
    val rawStress = sStress + hStress + jobStress + debtStress
    val scaledStress = if (rawStress > 0.0) rawStress * diff.stressMult else rawStress
    val scaledBurnout = if (burnoutDelta > 0.0) burnoutDelta * diff.stressMult else burnoutDelta
    val mergedStats = applyStatDeltas(
      state.player.stats,
      StatDeltaMap(
        physicalHealth = sPhys + hPhys + debtPhys + clubPhys,
        mentalHealth = sMent + hMent + debtMent + npcMent,
        happiness = sHap + eduHap + jobHap + debtHap + npcHap,
        energy = sEng,
        stress = scaledStress,
        looks = sLooks,
        intelligence = sInt + hInt,
        discipline = sDisc,
      ),
    ).let { st ->
      st.copy(intelligence = clamp(st.intelligence, 0.0, min(100.0, state.player.dna.intellectCeiling + 12.0)))
    }

    val nextHidden = state.player.hidden.copy(
      sleepDebt = clamp(state.player.hidden.sleepDebt + sleepDebtDelta, 0.0, 100.0),
      burnout = clamp(state.player.hidden.burnout + scaledBurnout, 0.0, 100.0),
    )
    val nextMaxAp = computeTurnActionPoints(gran, mergedStats.energy, nextHidden.burnout)

    var nextState = state.copy(
      turn = nextTurn,
      rngState = rng.getState(),
      world = WorldState(nextYear, nextMonth, nextMacro, nextInflation),
      player = state.player.copy(
        ageMonths = nextAgeMonths,
        alive = !died,
        stats = mergedStats,
        hidden = nextHidden,
        skills = nextSkills,
        schedule = schedule,
        actionPoints = nextMaxAp,
        maxActionPoints = nextMaxAp,
        conditions = nextConditions,
        habits = nextHabits,
        education = EducationState(curProgId, progMonths, gpa, completedProgs, loanBal),
        job = nextJob,
        dateProspect = state.player.dateProspect?.takeIf { !died && nextNpcs.none(::isRomantic) }
          ?.let { it.copy(ageMonths = it.ageMonths + monthsToAdd) },
        courtshipDates = if (died || nextNpcs.any(::isRomantic)) 0 else state.player.courtshipDates,
        interviewAttempts = emptySet(),
        activeClub = nextClub,
        activeProject = nextProject,
        finance = state.player.finance.copy(
          cash = cash,
          creditScore = credit,
          housingId = housingId,
          ownsHousing = ownsHousing,
          mortgageBalance = mortgage,
          personalLoans = repayment.loans,
          hardshipMonths = hardshipMonths,
          investments = nextInvestments,
          lastTurnIncome = turnGross - turnTax,
          lastTurnExpenses = turnExpenses,
          lastTurnTax = turnTax,
        ),
      ),
      npcs = nextNpcs,
      scheduledFollowUps = state.scheduledFollowUps.filter { nextTurn - it.triggerTurn <= 12 },
      timeline = (logs + state.timeline).take(250),
    )

    val nw = calculateNetWorth(nextState, pack)
    val peak = max(nextState.peakNetWorth, nw)
    nextState = nextState.copy(
      peakNetWorth = peak,
      player = nextState.player.copy(
        finance = nextState.player.finance.copy(
          netWorthHistory = (nextState.player.finance.netWorthHistory.takeLast(120) + NetWorthPoint(nextAgeMonths, nw)),
        ),
      ),
    )

    if (died) {
      val cause = causeOfDeath ?: "Natural causes"
      val childrenCount = nextState.npcs.count { it.role == NpcRole.child }
      val avgRel = if (nextState.npcs.isEmpty()) 50.0 else nextState.npcs.sumOf { it.affection } / nextState.npcs.size
      val wealthPts = min(350, (log10(max(10.0, peak.toDouble())) * 50.0).roundToInt())
      val score = max(10, (nextAgeYears * 4 + wealthPts + childrenCount * 45 + avgRel * 2).roundToInt())
      val summary = DeathSummary(nextAgeMonths, cause, nw, peak, nextState.player.highestJobTitle, childrenCount, score)
      val dLog = TimelineEntry("tl_death_$nextTurn", nextTurn, nextAgeMonths, nextYear, "death", "Passed Away at Age $nextAgeYears", "Cause: $cause. Legacy Score: $score.")
      return nextState.copy(
        deathSummary = summary,
        pendingEvent = null,
        timeline = (listOf(dLog) + nextState.timeline).take(250),
      )
    }

    val event = selectTurnEvent(nextState, pack, rng)
    return nextState.copy(rngState = rng.getState(), pendingEvent = event)
  }

  fun isEventEligible(state: GameState, event: GameEvent): Boolean {
    if (event.oneShot && state.triggeredOneShots.contains(event.id)) return false
    val last = state.eventHistory[event.id]
    if (last != null && state.turn - last < event.cooldownTurns) return false
    val c = event.conditions
    val age = getAgeYears(state.player.ageMonths)
    val hasPartner = state.npcs.any { it.alive && (it.role == NpcRole.partner || it.role == NpcRole.spouse) }

    if (c.minAge != null && age < c.minAge) return false
    if (c.maxAge != null && age > c.maxAge) return false
    if (c.employed != null && (state.player.job != null) != c.employed) return false
    if (c.hasPartner != null && hasPartner != c.hasPartner) return false
    if (c.minCash != null && state.player.finance.cash < c.minCash) return false
    if (c.minStress != null && state.player.stats.stress < c.minStress) return false
    if (c.minIntelligence != null && state.player.stats.intelligence < c.minIntelligence) return false
    if (c.minPhysicalHealth != null && state.player.stats.physicalHealth < c.minPhysicalHealth) return false
    if (c.minChaos != null && state.config.chaosLevel < c.minChaos) return false
    if (c.requiredFlag != null && state.player.flags[c.requiredFlag] != true) return false
    return true
  }

  private fun selectTurnEvent(state: GameState, pack: EraPack, rng: SeededRng): GameEvent? {
    for (due in state.scheduledFollowUps) {
      if (due.triggerTurn <= state.turn) {
        val found = pack.events.firstOrNull { it.id == due.eventId }
        if (found != null && isEventEligible(state, found)) return found
      }
    }

    val baseChance = clamp(0.76 + (state.config.chaosLevel / 100.0) * 0.16, 0.5, 0.94)
    if (!rng.chance(baseChance)) return null

    val eligible = pack.events.filter { it.weight > 0 && isEventEligible(state, it) }
    if (eligible.isEmpty()) return null

    return rng.weightedPick(eligible) { e ->
      val last = state.eventHistory[e.id]
      val novelty = when {
        last == null -> 1.6
        state.turn - last <= 8 -> 0.18
        state.turn - last <= 20 -> 0.55
        else -> 1.0
      }
      if (e.category == "chaos") e.weight * novelty * (0.55 + state.config.chaosLevel / 50.0)
      else e.weight * novelty
    }
  }

  fun isChoiceAvailable(state: GameState, choice: EventChoice): Boolean {
    val req = choice.requirements ?: return true
    if (req.minCash != null && state.player.finance.cash < req.minCash) return false
    if (req.minIntelligence != null && state.player.stats.intelligence < req.minIntelligence) return false
    if (req.minCharisma != null && state.player.stats.charisma < req.minCharisma) return false
    return true
  }

  const val SKIP_DECISION = "__skip_decision"
  val skipChoice = EventChoice(
    SKIP_DECISION, "Let it pass",
    effects = EventChoiceEffects(stats = StatDeltaMap(stress = -2.0, discipline = -1.0)),
  )

  fun resolveEventChoice(state: GameState, choiceId: String, pack: EraPack): GameState {
    val event = state.pendingEvent ?: return state
    val choice = if (choiceId == SKIP_DECISION) skipChoice else event.choices.firstOrNull { it.id == choiceId } ?: return state
    if (!isChoiceAvailable(state, choice)) return state

    val rng = SeededRng(state.rngState)
    val fx = choice.effects
    val country = pack.country(state.config.countryId)
    val receiptParts = mutableListOf<String>()

    var p = state.player
    fx.stats?.let { st ->
      p = p.copy(stats = applyStatDeltas(p.stats, st))
      st.happiness?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Happiness") }
      st.physicalHealth?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Health") }
      st.mentalHealth?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Mental") }
      st.stress?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Stress") }
      st.intelligence?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Intellect") }
      st.charisma?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Charisma") }
      st.discipline?.takeIf { it != 0.0 }?.let { receiptParts.add("${if (it > 0) "+" else ""}${it.roundToInt()} Discipline") }
    }
    fx.skills?.let { skMap ->
      val nextSk = p.skills.toMutableMap()
      for ((k, v) in skMap) nextSk[k] = clamp((nextSk[k] ?: 0.0) + v, 0.0, 100.0)
      p = p.copy(skills = nextSk)
    }
    if (fx.cashDelta != null || fx.creditScoreDelta != null) {
      val cashChange = fx.cashDelta ?: 0L
      p = p.copy(
        finance = p.finance.copy(
          cash = safeAddMoney(p.finance.cash, cashChange),
          creditScore = clampInt(p.finance.creditScore + (fx.creditScoreDelta ?: 0), 300, 850),
        ),
      )
      if (cashChange != 0L) {
        receiptParts.add("${if (cashChange > 0) "+" else ""}${formatMoney(cashChange, country)} cash")
      }
      fx.creditScoreDelta?.takeIf { it != 0 }?.let {
        receiptParts.add("${if (it > 0) "+" else ""}$it credit")
      }
    }
    fx.investIn?.let { inv ->
      val list = p.finance.investments.toMutableList()
      val idx = list.indexOfFirst { it.assetId == inv.assetId }
      if (idx >= 0) {
        val cur = list[idx]
        list[idx] = cur.copy(
          balance = safeAddMoney(cur.balance, inv.amount),
          costBasis = safeAddMoney(cur.costBasis, inv.amount),
        )
      } else {
        list.add(InvestmentHolding(inv.assetId, clampMoney(inv.amount), clampMoney(inv.amount)))
      }
      p = p.copy(finance = p.finance.copy(investments = list))
      receiptParts.add("${formatMoney(inv.amount, country)} invested")
    }
    fx.gpaDelta?.let {
      p = p.copy(education = p.education.copy(gpa = clamp(p.education.gpa + it, 0.5, 4.0).round2()))
    }
    fx.jobPerformanceDelta?.let { delta ->
      p.job?.let { j -> p = p.copy(job = j.copy(performance = clamp(j.performance + delta, 0.0, 100.0))) }
    }
    if (fx.retire == true) {
      p = p.copy(retired = true, job = null)
      receiptParts.add("Retired")
    }
    fx.addHabit?.let { id ->
      if (p.habits.none { it.id == id } && pack.conditions.habits.any { it.id == id }) {
        p = p.copy(habits = p.habits + ActiveHabit(id, 35.0))
      }
    }
    fx.removeHabit?.let { id -> p = p.copy(habits = p.habits.filterNot { it.id == id }) }
    fx.addCondition?.let { id ->
      if (p.conditions.none { it.id == id }) {
        pack.conditions.conditions.firstOrNull { it.id == id }?.let { def ->
          p = p.copy(conditions = p.conditions + ActiveCondition(id, p.ageMonths, def.severity, false))
        }
      }
    }
    fx.setHousing?.let { hId ->
      val saleProceeds = if (p.finance.ownsHousing) {
        val curHome = pack.assets.housing.firstOrNull { it.id == p.finance.housingId }
        val curVal = curHome?.let { (it.purchasePrice * country.costOfLivingMultiplier).roundToLong() } ?: 0L
        curVal - p.finance.mortgageBalance
      } else 0L
      p = p.copy(
        finance = p.finance.copy(
          cash = safeAddMoney(p.finance.cash, saleProceeds),
          housingId = hId,
          ownsHousing = false,
          mortgageBalance = 0L,
        ),
      )
    }
    fx.setFlag?.let { flag -> p = p.copy(flags = p.flags + (flag to true)) }

    var nextWorld = state.world
    fx.setMacro?.let { nextWorld = nextWorld.copy(macroState = it) }

    var nextNpcs = state.npcs.map { npc ->
      if (!npc.alive) return@map npc
      var upd = npc
      if (fx.parentAffection != null && npc.role == NpcRole.parent) {
        upd = upd.copy(affection = clamp(upd.affection + fx.parentAffection, 0.0, 100.0))
      }
      if (fx.partnerAffection != null && (npc.role == NpcRole.partner || npc.role == NpcRole.spouse)) {
        upd = upd.copy(affection = clamp(upd.affection + fx.partnerAffection, 0.0, 100.0))
      }
      if (fx.allNpcAffection != null) {
        upd = upd.copy(affection = clamp(upd.affection + fx.allNpcAffection, 0.0, 100.0))
      }
      if (fx.memoryTag != null && (npc.role == NpcRole.partner || npc.role == NpcRole.spouse || npc.role == NpcRole.friend)) {
        val mem = NpcMemory("mem_${fx.memoryTag.tag}_${p.ageMonths}", fx.memoryTag.tag, fx.memoryTag.label, fx.memoryTag.sentimentDelta, p.ageMonths)
        upd = upd.copy(
          memories = (listOf(mem) + upd.memories).take(20),
          affection = clamp(upd.affection + mem.sentimentDelta * 0.5, 0.0, 100.0),
          trust = clamp(upd.trust + mem.sentimentDelta * 0.35, 0.0, 100.0),
        )
      }
      upd
    }.toMutableList()

    if (fx.spawnPartner == true && nextNpcs.none(::isRomantic)) {
      val partnerAge = max(14 * 12, p.ageMonths + rng.nextInt(-24, 24))
      nextNpcs.add(createNpc(rng, pack, state.config.countryId, NpcRole.partner, partnerAge))
      receiptParts.add("Met someone new")
    }
    if (fx.spawnChild == true) {
      val partner = nextNpcs.firstOrNull { it.alive && (it.role == NpcRole.spouse || it.role == NpcRole.partner) }
      val childDna = inheritDna(rng, p.dna, partner?.dna)
      nextNpcs.add(createNpc(rng, pack, state.config.countryId, NpcRole.child, 0, lastNameOverride = p.lastName, dna = childDna, wealthOverride = 0L))
      receiptParts.add("Welcomed a child")
    }

    val nextFollowUps = state.scheduledFollowUps.filterNot { it.eventId == event.id }.toMutableList()
    fx.followUp?.let { nextFollowUps.add(ScheduledFollowUp(it.eventId, state.turn + it.delayTurns)) }

    val receiptSuffix = if (receiptParts.isNotEmpty()) " -> ${receiptParts.take(3).joinToString(" · ")}" else ""
    val entry = TimelineEntry(
      id = "tl_evt_${state.turn}_${event.id}",
      turn = state.turn,
      ageMonths = p.ageMonths,
      year = nextWorld.currentYear,
      category = event.category,
      title = event.title,
      detail = "Chose: \"${choice.label}\"$receiptSuffix",
    )

    return state.copy(
      rngState = rng.getState(),
      world = nextWorld,
      player = if (nextNpcs.any(::isRomantic)) p.copy(dateProspect = null, courtshipDates = 0, lastDateTurn = null, lastDateChatTurn = null) else p,
      npcs = nextNpcs,
      pendingEvent = null,
      eventHistory = state.eventHistory + (event.id to state.turn),
      triggeredOneShots = if (event.oneShot && !state.triggeredOneShots.contains(event.id)) state.triggeredOneShots + event.id else state.triggeredOneShots,
      scheduledFollowUps = nextFollowUps,
      timeline = (listOf(entry) + state.timeline).take(250),
    )
  }

  fun canEnrollInProgram(state: GameState, prog: EducationProgramDef): Pair<Boolean, String?> {
    val age = getAgeYears(state.player.ageMonths)
    if (state.player.education.completedProgramIds.contains(prog.id)) return false to "Already completed."
    if (state.player.education.currentProgramId != null) return false to "Already enrolled."
    if (age < prog.minAge) return false to "Min age ${prog.minAge}."
    if (prog.prerequisite != null && !state.player.education.completedProgramIds.contains(prog.prerequisite)) {
      return false to "Needs ${prog.prerequisite}."
    }
    if (state.player.stats.intelligence < prog.minIntelligence) return false to "Needs Intellect ${prog.minIntelligence}+."
    return true to null
  }

  fun canApplyForJob(state: GameState, track: CareerTrackDef, tier: CareerTierDef): Pair<Boolean, String?> {
    val age = getAgeYears(state.player.ageMonths)
    if (age < tier.minAge) return false to "Min age ${tier.minAge}."
    if ("${track.id}/${tier.id}" in state.player.interviewAttempts) return false to "Interviewed this turn."
    if (tier.minIntelligence != null && state.player.stats.intelligence < tier.minIntelligence) {
      return false to "Needs Intellect ${tier.minIntelligence}+."
    }
    if (tier.requiredEducation.isNotEmpty() && tier.requiredEducation.none { state.player.education.completedProgramIds.contains(it) }) {
      return false to "Needs degree."
    }
    for ((sk, req) in tier.minSkills) {
      if ((state.player.skills[sk] ?: 0.0) < req) return false to "Needs ${sk.name} ${req.toInt()}+."
    }
    if (tier.minYearsInTrack != null) {
      val job = state.player.job
      if (job == null || job.trackId != track.id) {
        return false to "Needs ${tier.minYearsInTrack}y in ${track.name}."
      }
      val yearsInTrack = job.monthsInTrack / 12.0
      if (yearsInTrack < tier.minYearsInTrack) {
        return false to "Needs ${tier.minYearsInTrack}y in ${track.name} (have ${"%.1f".format(yearsInTrack)}y)."
      }
    }
    return true to null
  }

  fun canSettleOverdraft(state: GameState, country: CountryDef): Boolean {
    val p = state.player
    val f = p.finance
    return getAgeYears(p.ageMonths) >= country.legalAdultAge && !f.usedDebtSettlement &&
      f.hardshipMonths >= 6 && f.cash < -country.baseAnnualLivingCost * 2 &&
      !f.ownsHousing && f.investments.none { it.balance > 0 } && f.possessions.isEmpty()
  }

  fun canBorrow(state: GameState, type: LoanType, country: CountryDef): Pair<Boolean, String> {
    val p = state.player
    val f = p.finance
    if (getAgeYears(p.ageMonths) < country.legalAdultAge) return false to "Available at age ${country.legalAdultAge}."
    if (f.personalLoans.any { it.type == type }) return false to "You already have this loan."
    if (f.personalLoans.size >= 2) return false to "Repay an open loan first."
    if (f.creditScore < type.minCredit) return false to "Credit score ${type.minCredit}+ required."
    if (type.requiresIncome && (p.job?.annualSalary ?: 0L) <= 0L && getAgeYears(p.ageMonths) < country.retirementAge) {
      return false to "A regular paycheck or pension is required."
    }
    if (type.requiresIncome && f.cash < -country.baseAnnualLivingCost / 2) return false to "The lender declined due to your overdraft."
    return true to ""
  }

  fun firstDateCost(plan: FirstDatePlan, country: CountryDef): Long = when (plan) {
    FirstDatePlan.walk -> 0L
    FirstDatePlan.coffee -> (18 * country.costOfLivingMultiplier).roundToLong()
    FirstDatePlan.dinner -> (65 * country.costOfLivingMultiplier).roundToLong()
    FirstDatePlan.museum -> (30 * country.costOfLivingMultiplier).roundToLong()
    FirstDatePlan.picnic -> (12 * country.costOfLivingMultiplier).roundToLong()
  }

  fun preferredFirstDate(npc: Npc): FirstDatePlan = when {
    npc.personality.ambition >= 78 -> FirstDatePlan.dinner
    npc.personality.ambition >= 64 -> FirstDatePlan.museum
    npc.personality.warmth >= 78 -> FirstDatePlan.picnic
    npc.personality.warmth >= 60 -> FirstDatePlan.walk
    else -> FirstDatePlan.coffee
  }

  fun performPlayerAction(state: GameState, action: PlayerAction, pack: EraPack): ActionResult {
    if (!state.player.alive) return ActionResult(state, false, "Cannot act after death.")
    if (state.pendingEvent != null) return ActionResult(state, false, "Resolve the pending event first.")

    val apCost = when (action) {
      is PlayerAction.SetParentingStyle,
      is PlayerAction.QuitJob,
      is PlayerAction.Retire,
      PlayerAction.PassDate,
      PlayerAction.DefineRelationship,
      is PlayerAction.RepayLoan,
      is PlayerAction.SetClubHours,
      is PlayerAction.LeaveClub,
      is PlayerAction.AbandonProject,
      -> 0
      is PlayerAction.DoctorVisit, is PlayerAction.TherapySession, is PlayerAction.ChangeHousing, PlayerAction.SettleOverdraft -> 2
      else -> 1
    }
    if (state.player.actionPoints < apCost) {
      return ActionResult(state, false, "Not enough Action Points (need $apCost AP).")
    }

    val country = pack.country(state.config.countryId)
    val ageYears = getAgeYears(state.player.ageMonths)
    val rng = SeededRng(state.rngState)

    val result: ActionResult = when (action) {
      is PlayerAction.EnrollEducation -> {
        val prog = pack.education.firstOrNull { it.id == action.programId }
          ?: return ActionResult(state, false, "Program not found.")
        val (ok, reason) = canEnrollInProgram(state, prog)
        if (!ok) return ActionResult(state, false, reason ?: "Not eligible.")
        ActionResult(
          state.copy(player = state.player.copy(education = state.player.education.copy(currentProgramId = prog.id, monthsCompletedInProgram = 0))),
          true,
          "Enrolled in ${prog.name}.",
        )
      }

      is PlayerAction.ApplyJob -> {
        val track = pack.careers.firstOrNull { it.id == action.trackId }
        val tier = track?.tiers?.firstOrNull { it.id == action.tierId }
        if (track == null || tier == null) return ActionResult(state, false, "Job not found.")
        val (ok, reason) = canApplyForJob(state, track, tier)
        if (!ok) return ActionResult(state, false, reason ?: "Not qualified.")
        val macro = pack.meta.macroStates.getValue(state.world.macroState)
        val diff = diffMods(state.config.difficulty)
        val portfolioBonus = min(0.15, state.player.portfolio.size * 0.05)
        val player = state.player
        if (player.job?.trackId == track.id && player.job.tierId == tier.id) return ActionResult(state, false, "You already hold this job.")
        val attempts = player.interviewAttempts + "${track.id}/${tier.id}"
        val skill = when (action.answer) {
          InterviewAnswer.practical -> tier.minSkills.keys.plus(tier.skillGrowth.keys).maxOfOrNull { player.skills[it] ?: 0.0 }
            ?: (player.skills[SkillId.operations] ?: 0.0)
          InterviewAnswer.teamwork -> ((player.skills[SkillId.communication] ?: 0.0) + player.stats.charisma) / 2.0
          InterviewAnswer.dependable -> player.stats.discipline
        }
        val preferred = if (track.isPartTime) InterviewAnswer.dependable
          else if (tier.minSkills.isNotEmpty()) InterviewAnswer.practical else InterviewAnswer.teamwork
        val fit = (skill - 45.0) * 0.0035 + if (action.answer == preferred) 0.12 else 0.0
        val chance = clamp((0.42 + player.stats.charisma * 0.002 + player.stats.intelligence * 0.002 + fit + portfolioBonus + diff.hireBonus) * macro.hiringMultiplier, 0.10, 0.96)
        if (!rng.chance(chance)) {
          return ActionResult(
            state.copy(
              rngState = rng.getState(),
              player = player.copy(actionPoints = max(0, player.actionPoints - apCost), interviewAttempts = attempts),
            ),
            false,
            "Interviewed for ${tier.title}, but they picked another candidate (-$apCost AP).",
          )
        }
        val sal = (tier.baseAnnualSalary * country.salaryMultiplier).roundToLong()
        val sameTrack = state.player.job?.trackId == track.id
        ActionResult(
          state.copy(
            player = state.player.copy(
              retired = false,
              interviewAttempts = attempts,
              job = JobState(track.id, tier.id, tier.title, sal, clamp(56.0 + skill / 8.0, 56.0, 69.0), if (sameTrack) (state.player.job?.monthsInTrack ?: 0) else 0, 0),
              highestJobTitle = tier.title,
            ),
          ),
          true,
          "Hired as ${tier.title} (${formatMoney(sal, country)}/yr)!",
        )
      }

      is PlayerAction.RequestPromotion -> {
        val job = state.player.job ?: return ActionResult(state, false, "You are unemployed.")
        val track = pack.careers.firstOrNull { it.id == job.trackId }
        val curTier = track?.tiers?.firstOrNull { it.id == job.tierId }
        if (track == null || curTier == null) return ActionResult(state, false, "Track missing.")
        val nextTier = track.tiers.firstOrNull { it.level == curTier.level + 1 }
        if (nextTier == null) {
          if (!rng.chance(clamp(job.performance / 120.0, 0.25, 0.85))) {
            return ActionResult(
              state.copy(
                rngState = rng.getState(),
                player = state.player.copy(actionPoints = max(0, state.player.actionPoints - apCost)),
              ),
              false,
              "Board declined a raise this cycle (-$apCost AP).",
            )
          }
          val newSal = (job.annualSalary * 1.08).roundToLong()
          ActionResult(state.copy(player = state.player.copy(job = job.copy(annualSalary = newSal))), true, "Negotiated an 8% raise!")
        } else {
          if (ageYears < nextTier.minAge) {
            return ActionResult(state, false, "Requires age ${nextTier.minAge}+ for ${nextTier.title}.")
          }
          val yrs = job.monthsInTrack / 12.0
          if (nextTier.minYearsInTrack != null && yrs < nextTier.minYearsInTrack) {
            return ActionResult(state, false, "Need ${nextTier.minYearsInTrack}y in track (have ${"%.1f".format(yrs)}y).")
          }
          for ((sk, req) in nextTier.minSkills) {
            if ((state.player.skills[sk] ?: 0.0) < req) return ActionResult(state, false, "Needs ${sk.name} ${req.toInt()}+.")
          }
          val diff = diffMods(state.config.difficulty)
          val portfolioBonus = min(0.15, state.player.portfolio.size * 0.05)
          val pChance = clamp((job.performance - 35.0) / 80.0 + state.player.stats.charisma * 0.002 + portfolioBonus + diff.hireBonus, 0.12, 0.92)
          if (!rng.chance(pChance)) {
            return ActionResult(
              state.copy(
                rngState = rng.getState(),
                player = state.player.copy(actionPoints = max(0, state.player.actionPoints - apCost)),
              ),
              false,
              "Passed over for ${nextTier.title} this cycle (-$apCost AP).",
            )
          }
          val newSal = (nextTier.baseAnnualSalary * country.salaryMultiplier).roundToLong()
          ActionResult(
            state.copy(player = state.player.copy(job = job.copy(tierId = nextTier.id, title = nextTier.title, annualSalary = newSal, monthsInTier = 0), highestJobTitle = nextTier.title)),
            true,
            "Promoted to ${nextTier.title} (${formatMoney(newSal, country)}/yr)!",
          )
        }
      }

      is PlayerAction.QuitJob -> {
        val job = state.player.job ?: return ActionResult(state, false, "No job to quit.")
        ActionResult(state.copy(player = state.player.copy(job = null)), true, "Resigned from ${job.title}.")
      }

      is PlayerAction.Retire -> {
        if (ageYears < country.legalAdultAge) ActionResult(state, false, "You are too young to retire.")
        else ActionResult(state.copy(player = state.player.copy(retired = true, job = null)), true, "You have officially retired.")
      }

      is PlayerAction.Invest -> {
        if (ageYears < country.legalAdultAge) {
          ActionResult(state, false, "Must be ${country.legalAdultAge}+ to open an investment account.")
        } else if (action.amount <= 0L) {
          ActionResult(state, false, "Investment amount must be positive.")
        } else {
          val asset = pack.assets.investments.firstOrNull { it.id == action.assetId }
            ?: return ActionResult(state, false, "Asset not found.")
          if (action.amount < asset.minInvestment) return ActionResult(state, false, "Min investment is ${asset.minInvestment}.")
          if (state.player.finance.cash < action.amount) return ActionResult(state, false, "Insufficient cash.")
          val list = state.player.finance.investments.toMutableList()
          val idx = list.indexOfFirst { it.assetId == action.assetId }
          if (idx >= 0) {
            list[idx] = list[idx].copy(
              balance = safeAddMoney(list[idx].balance, action.amount),
              costBasis = safeAddMoney(list[idx].costBasis, action.amount),
            )
          } else {
            list.add(InvestmentHolding(action.assetId, clampMoney(action.amount), clampMoney(action.amount)))
          }
          val nextCash = safeAddMoney(state.player.finance.cash, -action.amount)
          ActionResult(
            state.copy(player = state.player.copy(finance = state.player.finance.copy(cash = nextCash, investments = list))),
            true,
            "Invested ${formatMoney(action.amount, country)} in ${asset.name} (-$apCost AP, cash ${formatMoney(nextCash, country)}).",
          )
        }
      }

      is PlayerAction.SellInvestment -> {
        if (action.amount <= 0L) return ActionResult(state, false, "Sale amount must be positive.")
        val cur = state.player.finance.investments.firstOrNull { it.assetId == action.assetId }
        if (cur == null || cur.balance <= 0) return ActionResult(state, false, "No holdings to sell.")
        val sellAmt = min(action.amount, cur.balance)
        val rem = cur.balance - sellAmt
        val nextInv = if (rem <= 0) state.player.finance.investments.filterNot { it.assetId == action.assetId }
        else state.player.finance.investments.map {
          if (it.assetId == action.assetId) it.copy(balance = rem, costBasis = (it.costBasis * (rem.toDouble() / cur.balance)).roundToLong()) else it
        }
        val nextCash = safeAddMoney(state.player.finance.cash, sellAmt)
        ActionResult(
          state.copy(player = state.player.copy(finance = state.player.finance.copy(cash = nextCash, investments = nextInv))),
          true,
          "Sold ${formatMoney(sellAmt, country)} (-$apCost AP, cash ${formatMoney(nextCash, country)}).",
        )
      }

      is PlayerAction.Shop -> {
        val p = state.player
        val finance = p.finance
        val item = action.item
        val owns = item in finance.possessions
        if (action.sell != owns) return ActionResult(state, false, if (owns) "You already own this." else "You don’t own this yet.")
        if (!action.sell && item == Goods.car && ageYears < country.legalAdultAge) {
          return ActionResult(state, false, "You must be ${country.legalAdultAge}+ to buy a car.")
        }
        val amount = if (action.sell) item.resaleValue(country) else item.cost(country)
        if (!action.sell && finance.cash < amount) return ActionResult(state, false, "You need ${formatMoney(amount, country)}.")
        val holdings = if (action.sell) finance.possessions - item else finance.possessions + item
        val updated = finance.copy(possessions = holdings, cash = safeAddMoney(finance.cash, if (action.sell) amount else -amount))
        val verb = if (action.sell) "Sold" else "Bought"
        ActionResult(state.copy(player = p.copy(finance = updated)), true, "$verb ${item.label} for ${formatMoney(amount, country)}.")
      }

      is PlayerAction.Borrow -> {
        val (eligible, reason) = canBorrow(state, action.type, country)
        if (!eligible) return ActionResult(state, false, reason)
        val p = state.player
        val type = action.type
        val amount = type.amount(country)
        val updated = p.finance.copy(cash = safeAddMoney(p.finance.cash, amount),
          personalLoans = p.finance.personalLoans + PersonalLoan(type, amount))
        val stress = if (type == LoanType.shady) 7.0 else 0.0
        ActionResult(state.copy(player = p.copy(finance = updated, stats = applyStatDeltas(p.stats, StatDeltaMap(stress = stress)))),
          true, "Borrowed ${formatMoney(amount, country)} from ${type.label.lowercase()}.")
      }

      is PlayerAction.RepayLoan -> {
        val p = state.player
        val finance = p.finance
        val loan = finance.personalLoans.firstOrNull { it.type == action.type }
          ?: return ActionResult(state, false, "This loan is already closed.")
        val amount = if (action.payInFull) loan.balance else minOf(loan.balance, finance.cash.coerceAtLeast(0))
        if (amount <= 0 || finance.cash < amount) return ActionResult(state, false, "You need more cash to make that payment.")
        val loans = finance.personalLoans.mapNotNull {
          if (it != loan) it else if (amount == loan.balance) null else loan.copy(balance = loan.balance - amount)
        }
        ActionResult(state.copy(player = p.copy(finance = finance.copy(
          cash = safeAddMoney(finance.cash, -amount), personalLoans = loans,
        ))), true, "Paid ${formatMoney(amount, country)} toward your loan.")
      }

      PlayerAction.SeekEmergencyAid -> {
        val p = state.player
        val finance = p.finance
        val unhoused = finance.housingId == "unhoused"
        val atRisk = finance.cash < 0 && !finance.receivedEmergencyAid
        if (ageYears < country.legalAdultAge || (!unhoused && !atRisk)) {
          return ActionResult(state, false, "Emergency support is available to adults in financial or housing hardship.")
        }
        val aid = if (!finance.receivedEmergencyAid) (country.baseAnnualLivingCost * 0.25).roundToLong() else 0L
        val updated = finance.copy(
          cash = safeAddMoney(finance.cash, aid),
          receivedEmergencyAid = finance.receivedEmergencyAid || aid > 0,
          housingId = if (unhoused) "emergency_shelter" else finance.housingId,
        )
        val details = buildList {
          if (unhoused) add("Secured an emergency shelter bed.")
          if (aid > 0) add("Received ${formatMoney(aid, country)} in one-time assistance.")
        }.joinToString(" ")
        ActionResult(state.copy(player = p.copy(finance = updated, stats = applyStatDeltas(p.stats, StatDeltaMap(stress = -8.0)))),
          true, details)
      }

      PlayerAction.SettleOverdraft -> {
        if (!canSettleOverdraft(state, country)) return ActionResult(state, false, "You’re not eligible for overdraft settlement.")
        val p = state.player
        val updated = p.finance.copy(
          cash = -(country.baseAnnualLivingCost / 12), creditScore = 300, hardshipMonths = 0, usedDebtSettlement = true,
        )
        val entry = TimelineEntry("tl_settlement_${state.turn}", state.turn, p.ageMonths, state.world.currentYear,
          "finance", "Overdraft Settled", "Your overdraft was reduced to one month of basic costs. Credit fell to 300 and other debts remain.")
        ActionResult(state.copy(
          player = p.copy(finance = updated, stats = applyStatDeltas(p.stats, StatDeltaMap(stress = -10.0))),
          timeline = (listOf(entry) + state.timeline).take(250),
        ), true, "Overdraft reduced. Your credit fell to 300.")
      }

      PlayerAction.AskFamilyForShelter -> {
        val p = state.player
        if (p.finance.housingId !in setOf("unhoused", "emergency_shelter") ||
          !hasFamilyHousing(state.npcs)) {
          return ActionResult(state, false, "You need a parent with a strong bond and low tension who can take you in.")
        }
        ActionResult(state.copy(player = p.copy(finance = p.finance.copy(housingId = "parents_home", hardshipMonths = 0))),
          true, "Family agreed to take you in.")
      }

      is PlayerAction.GiveGift -> {
        val target = state.npcs.firstOrNull { it.id == action.npcId && it.alive }
          ?: return ActionResult(state, false, "Person not found.")
        val gift = action.gift
        val cost = gift.cost(country)
        val p = state.player
        if (cost > 0 && p.finance.cash < cost) return ActionResult(state, false, "You need ${formatMoney(cost, country)}.")
        val tag = "gift_${gift.name}"
        val repeated = target.memories.any { it.tag == tag && it.ageMonths == p.ageMonths }
        if (repeated) return ActionResult(state, false, "${target.firstName} already received that gift this turn.")
        val taste = when {
          target.personality.ambition >= 70 -> Gift.book
          target.personality.warmth >= 72 -> Gift.letter
          target.personality.loyalty >= 72 -> Gift.keepsake
          target.personality.volatility >= 60 -> Gift.headphones
          else -> Gift.flowers
        }
        val gain = (if (gift == taste) 8 else 4) + if (cost > 0) 1 else 0
        val mem = NpcMemory("mem_${tag}_${state.turn}", tag, "Received ${gift.label.lowercase()}", gain.toDouble(), p.ageMonths)
        val updated = target.copy(
          affection = clamp(target.affection + gain, 0.0, 100.0),
          trust = clamp(target.trust + if (gift == taste) 5.0 else 2.0, 0.0, 100.0),
          memories = (listOf(mem) + target.memories).take(20),
        )
        ActionResult(state.copy(
          player = p.copy(finance = p.finance.copy(cash = safeAddMoney(p.finance.cash, -cost))),
          npcs = state.npcs.map { if (it.id == target.id) updated else it },
        ), true, if (gift == taste) "${target.firstName} loved the ${gift.label.lowercase()} (+$gain Affection)." else "Gift given (+$gain Affection).")
      }

      is PlayerAction.PracticeWellness -> {
        if (ageYears < 6) return ActionResult(state, false, "Wellness activities open at age 6.")
        val p = state.player
        val result = when (action.kind) {
          Wellness.rest -> StatDeltaMap(energy = 8.0, stress = -4.0)
          Wellness.movement -> StatDeltaMap(physicalHealth = 4.0, stress = -3.0)
          Wellness.breathing -> StatDeltaMap(mentalHealth = 4.0, stress = -6.0)
        }
        ActionResult(state.copy(player = p.copy(stats = applyStatDeltas(p.stats, result))), true,
          "${action.kind.label} completed. ${action.kind.benefit}.")
      }

      is PlayerAction.HabitSupport -> {
        val p = state.player
        val habit = p.habits.firstOrNull { it.id == action.habitId }
          ?: return ActionResult(state, false, "Habit not active.")
        val def = pack.conditions.habits.firstOrNull { it.id == habit.id && it.polarity == "negative" }
          ?: return ActionResult(state, false, "Support is for harmful habits.")
        val strength = habit.strength - 25.0
        val habits = p.habits.mapNotNull {
          if (it.id != habit.id) it else if (strength > 0) habit.copy(strength = strength) else null
        }
        ActionResult(state.copy(player = p.copy(habits = habits,
          stats = applyStatDeltas(p.stats, StatDeltaMap(mentalHealth = 4.0, stress = -3.0)))),
          true, if (strength > 0) "Support session: ${def.name} intensity −25." else "You broke the habit with support.")
      }

      is PlayerAction.ChangeHousing -> {
        val h = pack.assets.housing.firstOrNull { it.id == action.housingId }
          ?: return ActionResult(state, false, "Housing not found.")
        if (h.id != "parents_home" && ageYears < country.legalAdultAge) {
          return ActionResult(state, false, "Must be at least ${country.legalAdultAge} to move out or sign a lease/mortgage.")
        }
        if (h.id == state.player.finance.housingId && action.buy == state.player.finance.ownsHousing) {
          return ActionResult(state, false, "You already live there.")
        }
        if (h.id == "unhoused" || h.id == "emergency_shelter") return ActionResult(state, false, "Use housing assistance in Assets.")
        if (h.id == "parents_home" && ageYears >= country.legalAdultAge && !hasFamilyHousing(state.npcs)) {
          return ActionResult(state, false, "No family member can house you right now.")
        }
        val saleProceeds = if (state.player.finance.ownsHousing) {
          val curH = pack.assets.housing.firstOrNull { it.id == state.player.finance.housingId }
          val curVal = if (curH != null) (curH.purchasePrice * country.costOfLivingMultiplier).roundToLong() else 0L
          curVal - state.player.finance.mortgageBalance
        } else 0L
        val cashAfterSale = safeAddMoney(state.player.finance.cash, saleProceeds)

        if (action.buy && h.purchasePrice > 0) {
          if (state.player.finance.housingId == h.id && state.player.finance.ownsHousing) {
            return ActionResult(state, false, "You already own ${h.name}.")
          }
          val price = (h.purchasePrice * country.costOfLivingMultiplier).roundToLong()
          val down = (price * 0.2).roundToLong()
          if (cashAfterSale < down) {
            return ActionResult(state, false, "Need ${formatMoney(down, country)} for 20% down payment.")
          }
          val nextCash = safeAddMoney(cashAfterSale, -down)
          ActionResult(
            state.copy(
              player = state.player.copy(
                finance = state.player.finance.copy(
                  cash = nextCash,
                  housingId = h.id,
                  ownsHousing = true,
                  mortgageBalance = clampMoney(price - down),
                ),
              ),
            ),
            true,
            if (saleProceeds != 0L) "Sold previous home and purchased ${h.name}!" else "Purchased ${h.name}!",
          )
        } else {
          val deposit = if (h.annualRent > 0) (h.annualRent * country.costOfLivingMultiplier / 12).roundToLong() else 0L
          if (deposit > 0 && cashAfterSale < deposit) {
            return ActionResult(state, false, "Moving in requires ${formatMoney(deposit, country)} up front.")
          }
          ActionResult(
            state.copy(
              player = state.player.copy(
                finance = state.player.finance.copy(
                  cash = safeAddMoney(cashAfterSale, -deposit),
                  housingId = h.id,
                  ownsHousing = false,
                  mortgageBalance = 0L,
                ),
              ),
            ),
            true,
            if (saleProceeds != 0L) "Sold home and moved into ${h.name}." else "Moved into ${h.name}.",
          )
        }
      }

      is PlayerAction.InteractNpc -> {
        val target = state.npcs.firstOrNull { it.id == action.npcId && it.alive }
          ?: return ActionResult(state, false, "Person not found.")
        val tAge = getAgeYears(target.ageMonths)
        var cashDelta = 0L; var hapD = 0.0; var stressD = 0.0
        var upd = target
        var msg = ""

        when (action.interaction) {
          NpcInteraction.talk -> {
            val gain = (4.0 + (state.player.stats.charisma + target.personality.warmth) / 40.0).roundToInt()
            upd = upd.copy(
              affection = clamp(upd.affection + gain, 0.0, 100.0),
              trust = clamp(upd.trust + gain * 0.6, 0.0, 100.0),
              tension = clamp(upd.tension - 4.0, 0.0, 100.0),
            )
            hapD = 3.0; msg = "Meaningful talk with ${target.firstName} (+$gain Affection)."
          }
          NpcInteraction.gift -> {
            if (state.player.finance.cash < 150) return ActionResult(state, false, "Need ${country.currencySymbol}150 for a gift.")
            cashDelta = -150
            val mem = NpcMemory("mem_gift_${state.player.ageMonths}_${upd.memories.size}", "gift", "Gave a thoughtful gift", 10.0, state.player.ageMonths)
            upd = upd.copy(memories = (listOf(mem) + upd.memories).take(20), affection = clamp(upd.affection + 5.0, 0.0, 100.0), trust = clamp(upd.trust + 3.5, 0.0, 100.0))
            hapD = 4.0; msg = "${target.firstName} loved the gift!"
          }
          NpcInteraction.date -> {
            if (ageYears < 13) return ActionResult(state, false, "Too young for romantic dates.")
            if (target.role != NpcRole.partner && target.role != NpcRole.spouse) return ActionResult(state, false, "Only for partners or spouses.")
            if (state.player.finance.cash < 120) return ActionResult(state, false, "Need ${country.currencySymbol}120 for date night.")
            cashDelta = -120
            val mem = NpcMemory("mem_date_${state.player.ageMonths}_${upd.memories.size}", "date", "Great date night together", 12.0, state.player.ageMonths)
            upd = upd.copy(memories = (listOf(mem) + upd.memories).take(20), affection = clamp(upd.affection + 6.0, 0.0, 100.0), trust = clamp(upd.trust + 4.0, 0.0, 100.0))
            hapD = 7.0; stressD = -5.0; msg = "Date night with ${target.firstName} was great."
          }
          NpcInteraction.propose -> {
            if (target.role != NpcRole.partner) return ActionResult(state, false, "Can only propose to your partner.")
            if (ageYears < country.legalMarriageAge || tAge < country.legalMarriageAge) {
              return ActionResult(state, false, "Both partners must be ${country.legalMarriageAge}+ to marry in ${country.name}.")
            }
            val score = (target.affection + target.trust - target.tension) / 2.0
            if (score >= 62.0 || rng.chance(clamp((score - 40.0) / 50.0, 0.05, 0.9))) {
              val mem = NpcMemory("mem_marry_${state.player.ageMonths}", "married", "Got married!", 25.0, state.player.ageMonths, 0.04)
              upd = upd.copy(role = NpcRole.spouse, memories = (listOf(mem) + upd.memories).take(20), affection = clamp(upd.affection + 12.5, 0.0, 100.0))
              hapD = 16.0; msg = "${target.firstName} said YES! You are married."
            } else {
              val mem = NpcMemory("mem_rej_${state.player.ageMonths}", "rejected", "Turned down proposal", -12.0, state.player.ageMonths)
              upd = upd.copy(memories = (listOf(mem) + upd.memories).take(20), affection = clamp(upd.affection - 6.0, 0.0, 100.0), tension = clamp(upd.tension + 8.0, 0.0, 100.0))
              hapD = -12.0; stressD = 10.0; msg = "${target.firstName} isn't ready yet."
            }
          }
          NpcInteraction.spend_time -> {
            upd = upd.copy(affection = clamp(upd.affection + 6.0, 0.0, 100.0), tension = clamp(upd.tension - 3.0, 0.0, 100.0))
            hapD = 5.0; stressD = -3.0; msg = "Spent time with ${target.firstName} (+6 Affection, −3 Tension)."
          }
          NpcInteraction.offer_help -> {
            upd = upd.copy(trust = clamp(upd.trust + 7.0, 0.0, 100.0), respect = clamp(upd.respect + 5.0, 0.0, 100.0))
            msg = "Helped ${target.firstName} (+7 Trust, +5 Respect)."
          }
          NpcInteraction.set_boundary -> {
            if (target.tension < 10.0) return ActionResult(state, false, "There is no conflict to address right now.")
            upd = upd.copy(tension = clamp(upd.tension - 12.0, 0.0, 100.0), respect = clamp(upd.respect + 5.0, 0.0, 100.0), affection = clamp(upd.affection - 2.0, 0.0, 100.0))
            stressD = -4.0; msg = "Set a boundary with ${target.firstName} (−12 Tension, +5 Respect, −2 Affection)."
          }
          NpcInteraction.apologize -> {
            upd = upd.copy(tension = clamp(upd.tension - 16.0, 0.0, 100.0), trust = clamp(upd.trust + 6.0, 0.0, 100.0), affection = clamp(upd.affection + 5.0, 0.0, 100.0))
            msg = "Cleared the air with ${target.firstName}."
          }
          NpcInteraction.argue -> {
            val mem = NpcMemory("mem_arg_${state.player.ageMonths}_${upd.memories.size}", "argue", "Had a heated argument", -14.0, state.player.ageMonths)
            upd = upd.copy(
              memories = (listOf(mem) + upd.memories).take(20),
              affection = clamp(upd.affection - 7.0, 0.0, 100.0),
              trust = clamp(upd.trust - 5.0, 0.0, 100.0),
              respect = clamp(upd.respect - 3.0, 0.0, 100.0),
              tension = clamp(upd.tension + 9.0, 0.0, 100.0),
            )
            stressD = 8.0; hapD = -6.0; msg = "Argument with ${target.firstName}: less affection, trust and respect, more tension."
          }
          NpcInteraction.breakup -> {
            if (target.role != NpcRole.partner && target.role != NpcRole.spouse) return ActionResult(state, false, "Not in a relationship.")
            val mem = NpcMemory("mem_brk_${state.player.ageMonths}", "breakup", "Ended our relationship", -25.0, state.player.ageMonths, 0.08)
            upd = upd.copy(role = NpcRole.friend, memories = (listOf(mem) + upd.memories).take(20), affection = clamp(upd.affection - 12.5, 0.0, 100.0))
            hapD = -10.0; stressD = 8.0; msg = "Ended relationship with ${target.firstName}."
          }
        }
        ActionResult(
          state.copy(
            player = state.player.copy(
              stats = applyStatDeltas(state.player.stats, StatDeltaMap(happiness = hapD, stress = stressD)),
              finance = state.player.finance.copy(cash = state.player.finance.cash + cashDelta),
            ),
            npcs = state.npcs.map { if (it.id == action.npcId) upd else it },
          ),
          true,
          msg,
        )
      }

      is PlayerAction.SetParentingStyle -> {
        val child = state.npcs.firstOrNull { it.id == action.npcId && it.role == NpcRole.child && it.alive }
          ?: return ActionResult(state, false, "Child not found.")
        ActionResult(
          state.copy(npcs = state.npcs.map { if (it.id == action.npcId) it.copy(parentingStyle = action.style) else it }),
          true,
          "Set parenting style for ${child.firstName} to ${action.style.name}.",
        )
      }

      is PlayerAction.DoctorVisit -> {
        val targetCond = action.conditionId?.let { id -> state.player.conditions.firstOrNull { it.id == id } }
          ?: state.player.conditions.firstOrNull()
        if (targetCond == null) {
          val cost = (150 * country.healthcareCostMultiplier).roundToLong()
          if (state.player.finance.cash < cost) return ActionResult(state, false, "Need ${formatMoney(cost, country)}.")
          ActionResult(
            state.copy(
              player = state.player.copy(
                stats = applyStatDeltas(state.player.stats, StatDeltaMap(physicalHealth = 5.0)),
                finance = state.player.finance.copy(cash = state.player.finance.cash - cost),
              ),
            ),
            true,
            "Checkup complete (+5 Health).",
          )
        } else {
          val def = pack.conditions.conditions.firstOrNull { it.id == targetCond.id }
          val cost = ((def?.treatmentCost ?: 500L) * country.healthcareCostMultiplier).roundToLong()
          if (state.player.finance.cash < cost) return ActionResult(state, false, "Treatment costs ${formatMoney(cost, country)}.")
          val cured = def?.let { rng.chance(it.cureChance) } ?: true
          val nextConds = if (cured) state.player.conditions.filterNot { it.id == targetCond.id }
          else state.player.conditions.map { if (it.id == targetCond.id) it.copy(treated = true) else it }
          ActionResult(
            state.copy(
              player = state.player.copy(
                conditions = nextConds,
                stats = applyStatDeltas(state.player.stats, StatDeltaMap(physicalHealth = if (cured) 10.0 else 4.0)),
                finance = state.player.finance.copy(cash = state.player.finance.cash - cost),
              ),
            ),
            true,
            if (cured) "Cured ${def?.name ?: targetCond.id}!" else "${def?.name ?: targetCond.id} is now under treatment.",
          )
        }
      }

      is PlayerAction.TherapySession -> {
        val cost = (180 * country.healthcareCostMultiplier).roundToLong()
        if (state.player.finance.cash < cost) return ActionResult(state, false, "Therapy costs ${formatMoney(cost, country)}.")
        ActionResult(
          state.copy(
            player = state.player.copy(
              stats = applyStatDeltas(state.player.stats, StatDeltaMap(mentalHealth = 8.0, stress = -10.0, happiness = 4.0)),
              hidden = state.player.hidden.copy(burnout = clamp(state.player.hidden.burnout - 8.0, 0.0, 100.0)),
              finance = state.player.finance.copy(cash = state.player.finance.cash - cost),
            ),
          ),
          true,
          "Therapy complete (+8 Mental, -10 Stress).",
        )
      }

      is PlayerAction.AdoptHabit -> {
        val habit = pack.conditions.habits.firstOrNull { it.id == action.habitId }
          ?: return ActionResult(state, false, "Habit not found.")
        val adultHabits = setOf("smoking", "heavy_drinking", "gambling")
        if (adultHabits.contains(action.habitId) && ageYears < country.legalAdultAge) {
          return ActionResult(state, false, "Must be ${country.legalAdultAge}+ to adopt this habit.")
        }
        if (habit.polarity == "positive" && ageYears < 6) return ActionResult(state, false, "Habits open at age 6.")
        if (state.player.habits.any { it.id == action.habitId }) {
          return ActionResult(state, false, "Habit already active.")
        }
        ActionResult(state.copy(player = state.player.copy(habits = state.player.habits + ActiveHabit(action.habitId, 35.0))), true, "Started ${habit.name}.")
      }

      is PlayerAction.QuitHabit -> {
        val habit = state.player.habits.firstOrNull { it.id == action.habitId }
          ?: return ActionResult(state, false, "Habit not active.")
        val harmful = pack.conditions.habits.any { it.id == habit.id && it.polarity == "negative" }
        if (harmful && habit.strength > 55.0) return ActionResult(state, false, "Build support before quitting a strong habit.")
        ActionResult(
          state.copy(
            player = state.player.copy(
              habits = state.player.habits.filterNot { it.id == action.habitId },
              stats = applyStatDeltas(state.player.stats, StatDeltaMap(discipline = if (harmful) 3.0 else 0.0, stress = if (harmful) 4.0 else 0.0)),
            ),
          ),
          true,
          if (harmful) "Quit habit (+3 Discipline, +4 Stress)." else "Stopped routine.",
        )
      }

      is PlayerAction.JoinClub -> {
        if (ageYears !in 6..17) return ActionResult(state, false, "Clubs are for ages 6 to 17.")
        if (state.player.activeClub != null) return ActionResult(state, false, "Already enrolled in ${state.player.activeClub.name}.")
        val (clubName, _) = clubMeta(action.clubId)
        val peerAge = max(6 * 12, state.player.ageMonths + rng.nextInt(-12, 13))
        val rawPeer = createNpc(rng, pack, state.config.countryId, NpcRole.friend, peerAge, wealthOverride = 0L)
        val peerMem = NpcMemory("mem_club_${state.turn}_${action.clubId.name}", "club_teammate", "Met in $clubName", 8.0, state.player.ageMonths)
        val peer = rawPeer.copy(memories = listOf(peerMem))
        val club = ActiveClub(
          clubId = action.clubId,
          name = clubName,
          weeklyHours = clampInt(action.weeklyHours, 2, 8),
          teammateNpcId = peer.id,
        )
        val log = TimelineEntry(
          "tl_club_join_${state.turn}_${state.timeline.size}",
          state.turn,
          state.player.ageMonths,
          state.world.currentYear,
          "milestone",
          "Joined $clubName",
          "Started practicing ${club.weeklyHours}h/wk alongside ${peer.firstName} ${peer.lastName}.",
        )
        ActionResult(
          state.copy(
            player = state.player.copy(activeClub = club),
            npcs = state.npcs + peer,
            timeline = (listOf(log) + state.timeline).take(250),
          ),
          true,
          "Joined $clubName and met ${peer.firstName}!",
        )
      }

      is PlayerAction.SetClubHours -> {
        val club = state.player.activeClub ?: return ActionResult(state, false, "Not enrolled in a club.")
        val clampedHrs = clampInt(action.weeklyHours, 2, 8)
        ActionResult(
          state.copy(player = state.player.copy(activeClub = club.copy(weeklyHours = clampedHrs))),
          true,
          "${club.name} commitment set to ${clampedHrs}h/wk.",
        )
      }

      is PlayerAction.PracticeClub -> {
        val club = state.player.activeClub ?: return ActionResult(state, false, "Join a club first.")
        val (_, primarySkill) = clubMeta(club.clubId)
        val nextSkills = state.player.skills.toMutableMap()
        nextSkills[primarySkill] = clamp((nextSkills[primarySkill] ?: 0.0) + 3.5, 0.0, 100.0)
        val nextClub = club.copy(progress = clamp(club.progress + 22.0, 0.0, 100.0))
        val mem = NpcMemory("mem_prac_${state.turn}_${state.timeline.size}", "club_practice", "Practiced together for ${club.name}", 6.0, state.player.ageMonths)
        val nextNpcs = state.npcs.map { n ->
          if (n.id == club.teammateNpcId && n.alive) {
            n.copy(affection = clamp(n.affection + 6.0, 0.0, 100.0), memories = (listOf(mem) + n.memories).take(20))
          } else n
        }
        val teammateName = nextNpcs.firstOrNull { it.id == club.teammateNpcId }?.firstName
        ActionResult(
          state.copy(player = state.player.copy(activeClub = nextClub, skills = nextSkills), npcs = nextNpcs),
          true,
          "Practiced ${club.name} (+22% prep, +3.5 ${primarySkill.name.replace('_', ' ')}${if (teammateName != null) ", +bond with $teammateName" else ""}).",
        )
      }

      is PlayerAction.EnterClubShowcase -> {
        val club = state.player.activeClub ?: return ActionResult(state, false, "Not in a club.")
        if (!club.showcaseReady) {
          val rem = max(1, club.termLengthMonths - club.termMonthsCompleted)
          return ActionResult(state, false, "Next term: ${rem}m until showcase.")
        }
        val (_, primarySkill) = clubMeta(club.clubId)
        val skill = state.player.skills[primarySkill] ?: 0.0
        val familyBonus = if (state.npcs.any { it.alive && (it.role == NpcRole.parent || it.role == NpcRole.sibling) && it.affection >= 70.0 }) 12.0 else 0.0
        val score = club.progress * 0.6 + skill * 0.4 + familyBonus + rng.nextInt(-8, 13) + (if (action.effortHigh) 6.0 else 0.0)
        val won = score >= 65.0
        val nextSkills = state.player.skills.toMutableMap()
        val nextPortfolio = state.player.portfolio.toMutableList()
        var nextStats = state.player.stats
        var nextNpcs = state.npcs
        val logTitle: String
        val logDetail: String
        val msg: String

        if (won) {
          nextPortfolio.add(
            PortfolioItem(
              id = "port_club_${state.turn}_${nextPortfolio.size}",
              title = "${club.name} Showcase Award",
              category = "club",
              ageMonths = state.player.ageMonths,
              impactLabel = "+5% future interviews & scholarships",
            ),
          )
          nextStats = applyStatDeltas(nextStats, StatDeltaMap(happiness = 8.0, discipline = 3.0))
          val proudMem = NpcMemory("mem_win_${state.turn}", "showcase_win", "Celebrated ${club.name} showcase win", 10.0, state.player.ageMonths)
          nextNpcs = state.npcs.map { n ->
            if (n.id == club.teammateNpcId && n.alive) {
              n.copy(affection = clamp(n.affection + 8.0, 0.0, 100.0), memories = (listOf(proudMem) + n.memories).take(20))
            } else n
          }
          logTitle = "Won ${club.name} Showcase!"
          logDetail = "Earned the ${club.name} Showcase Award (+8 Happiness, +3 Discipline, added to Portfolio)."
          msg = "Won ${club.name} Showcase Award! Added to Portfolio."
        } else {
          nextSkills[primarySkill] = clamp(skill + 2.0, 0.0, 100.0)
          logTitle = "Completed ${club.name} Showcase"
          logDetail = "Gained real-stage experience (+2 ${primarySkill.name.replace('_', ' ')})."
          msg = "Finished ${club.name} Showcase (+2 ${primarySkill.name.replace('_', ' ')})."
        }

        val resetClub = club.copy(
          termMonthsCompleted = 0,
          progress = 15.0,
          showcaseReady = false,
          completedTerms = club.completedTerms + 1,
        )
        val log = TimelineEntry("tl_show_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "milestone", logTitle, logDetail)
        ActionResult(
          state.copy(
            player = state.player.copy(activeClub = resetClub, skills = nextSkills, stats = nextStats, portfolio = nextPortfolio),
            npcs = nextNpcs,
            timeline = (listOf(log) + state.timeline).take(250),
          ),
          true,
          msg,
        )
      }

      is PlayerAction.LeaveClub -> {
        val club = state.player.activeClub ?: return ActionResult(state, false, "Not enrolled in a club.")
        ActionResult(state.copy(player = state.player.copy(activeClub = null)), true, "Left ${club.name}.")
      }

      PlayerAction.DoPaidActivity -> {
        val activity = paidActivity(state, country)
          ?: return ActionResult(state, false, "Paid activities open at age 6.")
        if (activity.monthsUntilAvailable > 0) {
          return ActionResult(state, false, "Available in ${activity.monthsUntilAvailable} months.")
        }
        val reward = formatMoney(activity.reward, country)
        val log = TimelineEntry(
          "tl_paid_${state.turn}", state.turn, state.player.ageMonths, state.world.currentYear,
          "finance", "Earned $reward", activity.title,
        )
        ActionResult(
          state.copy(
            player = state.player.copy(
              lastPaidActivityAgeMonths = state.player.ageMonths,
              finance = state.player.finance.copy(cash = safeAddMoney(state.player.finance.cash, activity.reward)),
            ),
            timeline = (listOf(log) + state.timeline).take(250),
          ),
          true,
          "Earned $reward.",
        )
      }

      is PlayerAction.StartProject -> {
        if (state.player.activeProject != null) return ActionResult(state, false, "Finish or abandon your current project first.")
        val offer = projectOffers(ageYears, country).firstOrNull { it.type == action.type }
          ?: return ActionResult(state, false, "This project is unavailable at your age.")
        val pTitle = offer.title
        val cost = offer.cost
        if (state.player.finance.cash < cost) return ActionResult(state, false, "Needs ${formatMoney(cost, country)}.")
        val proj = ActiveProject(
          id = "proj_${action.type.name}_${state.turn}",
          type = action.type,
          title = pTitle,
          budgetSpent = cost,
          progress = 15.0,
          weeklyHours = 4,
        )
        val log = TimelineEntry("tl_proj_start_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "finance", "Started $pTitle", "Invested ${formatMoney(cost, country)} to launch your project.")
        ActionResult(
          state.copy(
            player = state.player.copy(
              activeProject = proj,
              finance = state.player.finance.copy(cash = safeAddMoney(state.player.finance.cash, -cost)),
            ),
            timeline = (listOf(log) + state.timeline).take(250),
          ),
          true,
          "Started $pTitle (-${formatMoney(cost, country)}).",
        )
      }

      is PlayerAction.WorkOnProject -> {
        val proj = state.player.activeProject ?: return ActionResult(state, false, "No active project.")
        val nextProg = clamp(proj.progress + 35.0, 0.0, 100.0)
        if (nextProg >= 100.0) {
          val baseReward = when (proj.type) {
            ProjectType.lemonade_stall -> 65L
            ProjectType.passion_zine -> 180L
            ProjectType.portfolio_app -> 1200L
          }
          val reward = max(1L, (baseReward * country.costOfLivingMultiplier).roundToLong())
          val item = PortfolioItem(
            id = "port_proj_${state.turn}_${state.player.portfolio.size}",
            title = proj.title,
            category = "project",
            ageMonths = state.player.ageMonths,
            impactLabel = "+5% hiring & promotion chance",
          )
          val log = TimelineEntry(
            "tl_proj_done_${state.turn}_${state.timeline.size}",
            state.turn,
            state.player.ageMonths,
            state.world.currentYear,
            "milestone",
            "Completed ${proj.title}!",
            "Earned ${formatMoney(reward, country)} and added ${proj.title} to your Portfolio.",
          )
          ActionResult(
            state.copy(
              player = state.player.copy(
                activeProject = null,
                portfolio = state.player.portfolio + item,
                stats = applyStatDeltas(state.player.stats, StatDeltaMap(happiness = 6.0, discipline = 3.0)),
                finance = state.player.finance.copy(cash = safeAddMoney(state.player.finance.cash, reward)),
              ),
              timeline = (listOf(log) + state.timeline).take(250),
            ),
            true,
            "Completed ${proj.title}! Earned ${formatMoney(reward, country)}.",
          )
        } else {
          ActionResult(
            state.copy(player = state.player.copy(activeProject = proj.copy(progress = nextProg))),
            true,
            "Worked on ${proj.title} (${nextProg.roundToInt()}% done).",
          )
        }
      }

      is PlayerAction.AbandonProject -> {
        val proj = state.player.activeProject ?: return ActionResult(state, false, "No active project.")
        val log = TimelineEntry("tl_proj_ab_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "milestone", "Abandoned ${proj.title}", "Set the project aside to focus elsewhere.")
        ActionResult(
          state.copy(
            player = state.player.copy(activeProject = null),
            timeline = (listOf(log) + state.timeline).take(250),
          ),
          true,
          "Abandoned ${proj.title}.",
        )
      }

      is PlayerAction.FindDate -> {
        if (ageYears < country.legalAdultAge) return ActionResult(state, false, "Dating opens at age ${country.legalAdultAge}.")
        if (state.player.dateProspect != null) return ActionResult(state, false, "You already have a match.")
        if (state.npcs.any(::isRomantic)) return ActionResult(state, false, "You are already in a relationship.")
        val peerAge = max(country.legalAdultAge * 12, state.player.ageMonths + rng.nextInt(-36, 36))
        val prospect = createNpc(rng, pack, country.id, NpcRole.partner, peerAge, genderOverride = action.preference)
          .copy(affection = 35.0, trust = 35.0, respect = 48.0)
        ActionResult(state.copy(player = state.player.copy(dateProspect = prospect, courtshipDates = 0, lastDateTurn = null, lastDateChatTurn = null)), true, "Matched with ${prospect.firstName}.")
      }

      PlayerAction.PassDate -> {
        val prospect = state.player.dateProspect ?: return ActionResult(state, false, "No match to pass on.")
        val npcs = if (state.player.courtshipDates > 0) state.npcs + prospect.copy(role = NpcRole.friend) else state.npcs
        ActionResult(state.copy(npcs = npcs, player = state.player.copy(dateProspect = null, courtshipDates = 0, lastDateTurn = null, lastDateChatTurn = null)), true,
          if (state.player.courtshipDates > 0) "You agreed to stay friends." else "Passed on the match.")
      }

      PlayerAction.ChatWithMatch -> {
        val player = state.player
        val prospect = player.dateProspect ?: return ActionResult(state, false, "Find a match first.")
        if (player.lastDateChatTurn == state.turn) return ActionResult(state, false, "You already caught up this turn.")
        val updated = prospect.copy(trust = clamp(prospect.trust + 7.0, 0.0, 100.0), affection = clamp(prospect.affection + 4.0, 0.0, 100.0))
        ActionResult(state.copy(player = player.copy(dateProspect = updated, lastDateChatTurn = state.turn)), true,
          "Got to know ${prospect.firstName} (+7 Trust, +4 Affection). They’d enjoy a ${preferredFirstDate(prospect).name}.")
      }

      is PlayerAction.GoOnFirstDate -> {
        val player = state.player
        val prospect = player.dateProspect ?: return ActionResult(state, false, "Find a match first.")
        if (ageYears < country.legalAdultAge || getAgeYears(prospect.ageMonths) < country.legalAdultAge || state.npcs.any(::isRomantic)) {
          return ActionResult(state, false, "This date is no longer available.")
        }
        if (player.lastDateTurn == state.turn) return ActionResult(state, false, "You can go on another date next turn.")
        val cost = firstDateCost(action.plan, country)
        if (cost > 0 && player.finance.cash < cost) return ActionResult(state, false, "You need ${formatMoney(cost, country)}.")
        val matches = action.plan == preferredFirstDate(prospect)
        val score = 37 + player.stats.charisma * 0.24 + prospect.personality.warmth * 0.15 + prospect.trust * 0.25 +
          (if (matches) 17 else 0) + rng.nextInt(-12, 12)
        val goodDate = score >= 65
        val detail = if (goodDate) "You both want to see each other again." else "The date felt awkward. A different plan or a conversation could help."
        val title = "A date with ${prospect.firstName}"
        val memory = NpcMemory("mem_date_${state.turn}", "courtship", title, if (goodDate) 12.0 else -4.0, player.ageMonths)
        val updated = prospect.copy(
          affection = clamp(prospect.affection + if (goodDate) 13.0 else -4.0, 0.0, 100.0),
          trust = clamp(prospect.trust + if (goodDate) 9.0 else 0.0, 0.0, 100.0),
          memories = (listOf(memory) + prospect.memories).take(20),
        )
        val log = TimelineEntry("tl_date_${state.turn}_${prospect.id}", state.turn, player.ageMonths, state.world.currentYear, "relationship", title, detail)
        ActionResult(state.copy(
          player = player.copy(dateProspect = updated, courtshipDates = player.courtshipDates + if (goodDate) 1 else 0, lastDateTurn = state.turn,
            finance = player.finance.copy(cash = safeAddMoney(player.finance.cash, -cost))),
          timeline = (listOf(log) + state.timeline).take(250),
        ), true, detail)
      }

      PlayerAction.DefineRelationship -> {
        val player = state.player
        val prospect = player.dateProspect ?: return ActionResult(state, false, "Find a match first.")
        if (state.npcs.any(::isRomantic) || player.courtshipDates < 2 || prospect.trust < 50.0 || prospect.affection < 58.0) {
          return ActionResult(state, false, "Build trust and share two good dates first.")
        }
        val log = TimelineEntry("tl_exclusive_${state.turn}_${prospect.id}", state.turn, player.ageMonths,
          state.world.currentYear, "relationship", "Started dating ${prospect.firstName}", "You agreed to become partners.")
        ActionResult(state.copy(
          player = player.copy(dateProspect = null, courtshipDates = 0, lastDateTurn = null, lastDateChatTurn = null),
          npcs = state.npcs + prospect.copy(role = NpcRole.partner),
          timeline = (listOf(log) + state.timeline).take(250),
        ), true, "You and ${prospect.firstName} are now partners.")
      }

      is PlayerAction.RelationshipDate -> {
        val npc = state.npcs.firstOrNull { it.id == action.npcId && isRomantic(it) }
          ?: return ActionResult(state, false, "Choose a partner or spouse.")
        if (ageYears < 13 || getAgeYears(npc.ageMonths) < 13) return ActionResult(state, false, "Too young for a date.")
        val p = state.player
        val cost = firstDateCost(action.plan, country)
        if (cost > 0 && p.finance.cash < cost) return ActionResult(state, false, "You need ${formatMoney(cost, country)}.")
        val favorite = action.plan == preferredFirstDate(npc)
        val gain = if (favorite) 8.0 else 5.0
        val label = "Made time for a ${action.plan.name} date"
        val memory = NpcMemory("mem_partner_date_${state.turn}_${npc.memories.size}", "partner_date", label, gain, p.ageMonths)
        val update = npc.copy(
          affection = clamp(npc.affection + gain, 0.0, 100.0),
          trust = clamp(npc.trust + if (favorite) 5.0 else 3.0, 0.0, 100.0),
          tension = clamp(npc.tension - 4.0, 0.0, 100.0),
          memories = (listOf(memory) + npc.memories).take(20),
        )
        ActionResult(state.copy(
          player = p.copy(
            finance = p.finance.copy(cash = safeAddMoney(p.finance.cash, -cost)),
            stats = applyStatDeltas(p.stats, StatDeltaMap(happiness = 5.0, stress = -4.0)),
          ),
          npcs = state.npcs.map { if (it.id == npc.id) update else it },
        ), true, "$label with ${npc.firstName} (+${gain.toInt()} Affection).")
      }

      is PlayerAction.MeetSomeone -> {
        if (action.romantic) {
          if (state.npcs.any(::isRomantic) || state.player.dateProspect != null) return ActionResult(state, false, "You already have a relationship or match.")
          if (ageYears < country.legalAdultAge) return ActionResult(state, false, "Must be ${country.legalAdultAge}+ to date.")
          if (state.npcs.any { it.alive && (it.role == NpcRole.partner || it.role == NpcRole.spouse) }) {
            return ActionResult(state, false, "You are already in a committed relationship.")
          }
          val pAge = max(country.legalAdultAge * 12, state.player.ageMonths + rng.nextInt(-36, 37))
          val rawPartner = createNpc(rng, pack, state.config.countryId, NpcRole.partner, pAge)
          val mem = NpcMemory("mem_date_${state.turn}", "intentional_date", "Met on an intentional date", 9.0, state.player.ageMonths)
          val partner = rawPartner.copy(memories = listOf(mem))
          val log = TimelineEntry("tl_meet_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "relationship", "Met ${partner.firstName} ${partner.lastName}", "Hit it off on a first date.")
          ActionResult(
            state.copy(npcs = state.npcs + partner, timeline = (listOf(log) + state.timeline).take(250)),
            true,
            "Met ${partner.firstName} ${partner.lastName} on a date!",
          )
        } else {
          if (ageYears < 6) return ActionResult(state, false, "Too young to socialize independently.")
          val fAge = max(6 * 12, state.player.ageMonths + rng.nextInt(-24, 25))
          val where = when {
            ageYears < 18 -> "Met after school"
            state.player.job != null -> "Met through work"
            else -> "Met at a local community event"
          }
          val rawFriend = createNpc(rng, pack, state.config.countryId, NpcRole.friend, fAge)
          val mem = NpcMemory("mem_friend_${state.turn}_${state.timeline.size}", "new_friend", where, 7.0, state.player.ageMonths)
          val friend = rawFriend.copy(memories = listOf(mem))
          val log = TimelineEntry("tl_friend_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "relationship", "Befriended ${friend.firstName} ${friend.lastName}", "$where.")
          ActionResult(
            state.copy(npcs = state.npcs + friend, timeline = (listOf(log) + state.timeline).take(250)),
            true,
            "Made a new friend: ${friend.firstName} ${friend.lastName}!",
          )
        }
      }

      is PlayerAction.InviteFriendOver -> {
        val target = state.npcs.firstOrNull { it.id == action.npcId && it.alive && it.role == NpcRole.friend }
          ?: return ActionResult(state, false, "Make a friend first before inviting someone over.")
        val mem = NpcMemory("mem_hang_${state.turn}_${state.timeline.size}", "hangout", "Invited over to hang out", 7.0, state.player.ageMonths)
        val nextNpcs = state.npcs.map { n ->
          if (n.id == target.id) {
            n.copy(
              affection = clamp(n.affection + 10.0, 0.0, 100.0),
              trust = clamp(n.trust + 6.0, 0.0, 100.0),
              memories = (listOf(mem) + n.memories).take(20),
            )
          } else n
        }
        ActionResult(
          state.copy(
            player = state.player.copy(stats = applyStatDeltas(state.player.stats, StatDeltaMap(happiness = 5.0))),
            npcs = nextNpcs,
          ),
          true,
          "Hung out with ${target.firstName} (+10 bond, +5 Happiness).",
        )
      }

      is PlayerAction.DiscussFamily -> {
        if (ageYears < country.legalAdultAge) return ActionResult(state, false, "Must be ${country.legalAdultAge}+ to plan a family.")
        val partner = state.npcs.firstOrNull { it.alive && (it.role == NpcRole.spouse || it.role == NpcRole.partner) }
          ?: return ActionResult(state, false, "You need a partner or spouse first.")
        if (action.plan !in listOf("wait", "adopt", "try")) return ActionResult(state, false, "Unknown family plan.")
        if (action.plan == "wait") {
          val mem = NpcMemory("mem_famwait_${state.turn}", "family_plan", "Agreed to wait before starting a family", 4.0, state.player.ageMonths)
          val nextNpcs = state.npcs.map { n -> if (n.id == partner.id) n.copy(memories = (listOf(mem) + n.memories).take(20)) else n }
          ActionResult(state.copy(npcs = nextNpcs), true, "Agreed with ${partner.firstName} to wait for now.")
        } else {
          if (partner.affection < 55.0) return ActionResult(state, false, "${partner.firstName} wants a stronger bond first (55%+ affection).")
          val childDna = if (action.plan == "adopt") generateDna(rng, pack, null) else inheritDna(rng, state.player.dna, partner.dna)
          val child = createNpc(rng, pack, state.config.countryId, NpcRole.child, 0, lastNameOverride = state.player.lastName, dna = childDna, wealthOverride = 0L)
          val mem = NpcMemory("mem_fam_${state.turn}", "started_family", "Started a family together", 12.0, state.player.ageMonths)
          val nextNpcs = state.npcs.map { n ->
            if (n.id == partner.id) {
              n.copy(affection = clamp(n.affection + 8.0, 0.0, 100.0), memories = (listOf(mem) + n.memories).take(20))
            } else n
          } + child
          val title = if (action.plan == "adopt") "Adopted ${child.firstName} ${child.lastName}" else "Welcomed ${child.firstName} ${child.lastName}"
          val log = TimelineEntry("tl_child_${state.turn}_${state.timeline.size}", state.turn, state.player.ageMonths, state.world.currentYear, "relationship", title, "You and ${partner.firstName} expanded your household.")
          ActionResult(
            state.copy(npcs = nextNpcs, timeline = (listOf(log) + state.timeline).take(250)),
            true,
            "$title into your family!",
          )
        }
      }

      is PlayerAction.ElderActivity -> {
        if (ageYears < 60 && !state.player.retired) return ActionResult(state, false, "Elder roles open at 60+ or retirement.")
        when (action.kind) {
          "volunteer" -> {
            val livingFriends = state.npcs.count { it.alive && it.role == NpcRole.friend }
            val extraNpcs = if (livingFriends < 2) {
              val fAge = max(50 * 12, state.player.ageMonths + rng.nextInt(-48, 36))
              val rawF = createNpc(rng, pack, state.config.countryId, NpcRole.friend, fAge)
              listOf(rawF.copy(memories = listOf(NpcMemory("mem_vol_${state.turn}", "volunteer", "Met volunteering locally", 7.0, state.player.ageMonths))))
            } else emptyList()
            ActionResult(
              state.copy(
                player = state.player.copy(stats = applyStatDeltas(state.player.stats, StatDeltaMap(happiness = 7.0, mentalHealth = 4.0))),
                npcs = state.npcs + extraNpcs,
              ),
              true,
              if (extraNpcs.isNotEmpty()) "Volunteered locally and met ${extraNpcs.first().firstName}!" else "Volunteered locally (+7 Happiness, +4 Mental).",
            )
          }
          "mentor" -> {
            val hasMentorItem = state.player.portfolio.any { it.title == "Community Mentor" }
            val nextPort = if (!hasMentorItem) {
              state.player.portfolio + PortfolioItem("port_mentor_${state.turn}", "Community Mentor", "community", state.player.ageMonths, "+5% community leadership legacy")
            } else state.player.portfolio
            ActionResult(
              state.copy(
                player = state.player.copy(
                  stats = applyStatDeltas(state.player.stats, StatDeltaMap(mentalHealth = 6.0, happiness = 4.0)),
                  portfolio = nextPort,
                ),
              ),
              true,
              "Mentored younger neighbors (+6 Mental${if (!hasMentorItem) ", added Community Mentor" else ""}).",
            )
          }
          else -> {
            val famRoles = setOf(NpcRole.parent, NpcRole.sibling, NpcRole.spouse, NpcRole.partner, NpcRole.child)
            val nextNpcs = state.npcs.map { n ->
              if (n.alive && famRoles.contains(n.role)) n.copy(affection = clamp(n.affection + 7.0, 0.0, 100.0)) else n
            }
            ActionResult(
              state.copy(
                player = state.player.copy(stats = applyStatDeltas(state.player.stats, StatDeltaMap(happiness = 6.0))),
                npcs = nextNpcs,
              ),
              true,
              "Hosted a warm family gathering (+7 family bond, +6 Happiness).",
            )
          }
        }
      }
    }

    return if (!result.success) {
      result.copy(state = result.state.copy(rngState = rng.getState()))
    } else {
      result.copy(
        state = result.state.copy(
          rngState = rng.getState(),
          player = result.state.player.copy(actionPoints = max(0, result.state.player.actionPoints - apCost)),
        ),
      )
    }
  }

  fun applyDirectorMutation(state: GameState, mutation: DirectorMutation): DirectorResult {
    if (state.config.ironman && mutation !is DirectorMutation.SetGranularity) {
      return DirectorResult(state, false, "Director Mode is locked in Ironman runs.")
    }

    var next = state
    var title = "Director Adjustment"
    var detail = ""

    when (mutation) {
      is DirectorMutation.SetPlayerStat -> {
        val v = clamp(mutation.value, 0.0, 100.0)
        val s = state.player.stats
        val upd = when (mutation.stat) {
          "physicalHealth" -> s.copy(physicalHealth = v)
          "mentalHealth" -> s.copy(mentalHealth = v)
          "happiness" -> s.copy(happiness = v)
          "stress" -> s.copy(stress = v)
          "intelligence" -> s.copy(intelligence = v)
          "charisma" -> s.copy(charisma = v)
          "looks" -> s.copy(looks = v)
          "discipline" -> s.copy(discipline = v)
          else -> s
        }
        next = state.copy(player = state.player.copy(stats = upd))
        detail = "Set ${mutation.stat} to ${v.roundToInt()}."
      }
      is DirectorMutation.SetCash -> {
        val clamped = clampMoney(mutation.amount)
        next = state.copy(player = state.player.copy(finance = state.player.finance.copy(cash = clamped)))
        detail = "Set cash balance to $clamped."
      }
      is DirectorMutation.SetCreditScore -> {
        val sc = clampInt(mutation.score, 300, 850)
        next = state.copy(player = state.player.copy(finance = state.player.finance.copy(creditScore = sc)))
        detail = "Set credit score to $sc."
      }
      is DirectorMutation.SetGranularity -> {
        next = state.copy(config = state.config.copy(granularity = mutation.granularity))
        title = "Turn Speed Updated"
        detail = "Turn granularity set to ${mutation.granularity.name}."
      }
      is DirectorMutation.SetChaosLevel -> {
        val c = clampInt(mutation.chaosLevel, 0, 100)
        next = state.copy(config = state.config.copy(chaosLevel = c))
        detail = "Chaos slider set to $c."
      }
      is DirectorMutation.SetMacroState -> {
        next = state.copy(world = state.world.copy(macroState = mutation.macroState))
        detail = "Economy shifted to ${mutation.macroState.name}."
      }
      is DirectorMutation.SetNpcAffection -> {
        val a = clamp(mutation.affection, 0.0, 100.0)
        next = state.copy(npcs = state.npcs.map { if (it.id == mutation.npcId) it.copy(affection = a) else it })
        detail = "Updated NPC affection to ${a.roundToInt()}."
      }
      is DirectorMutation.CureAllConditions -> {
        next = state.copy(player = state.player.copy(conditions = emptyList()))
        detail = "Cleared all medical conditions."
      }
    }

    val entry = TimelineEntry("tl_dir_${next.turn}_${next.timeline.size}", next.turn, next.player.ageMonths, next.world.currentYear, "director", title, detail)
    return DirectorResult(next.copy(timeline = (listOf(entry) + next.timeline).take(250)), true, detail)
  }
}
