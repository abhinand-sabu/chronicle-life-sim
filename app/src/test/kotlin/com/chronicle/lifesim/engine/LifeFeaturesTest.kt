package com.chronicle.lifesim.engine

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LifeFeaturesTest {
  private val json = Json { ignoreUnknownKeys = true }
  private inline fun <reified T> asset(name: String): T =
    json.decodeFromString(File("src/main/assets/eras/modern_2020s/$name.json").readText())
  private val pack = EraPack(asset("era"), asset("countries"), asset("education"), asset("careers"),
    asset("assets"), asset("conditions"), asset("events"), asset("names"))
  private val engine = SimulationEngine
  private val country = pack.country("usa")
  private val adult = engine.createNewLife(CreateLifeOptions(seed = 4197, countryId = country.id, granularity = TurnGranularity.MONTHLY), pack)
    .let { it.copy(player = it.player.copy(ageMonths = 300, actionPoints = 9, finance = it.player.finance.copy(cash = 32000))) }

  private fun act(state: GameState, action: PlayerAction) = engine.performPlayerAction(state, action, pack)
  private fun success(state: GameState, action: PlayerAction): GameState {
    val result = act(state, action)
    assertTrue(result.message, result.success)
    return result.state
  }
  private fun reload(state: GameState) = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))

  @Test
  fun loanDisbursementDoesNotIncreaseNetWorthAndOverdueLoanHurtsCredit() {
    val before = adult.copy(player = adult.player.copy(finance = adult.player.finance.copy(cash = -25000)))
    assertFalse(act(before, PlayerAction.Borrow(LoanType.bank)).success)
    val issued = success(before, PlayerAction.Borrow(LoanType.shady))
    assertEquals(engine.calculateNetWorth(before, pack), engine.calculateNetWorth(issued, pack))
    assertFalse(act(issued, PlayerAction.Borrow(LoanType.shady)).success)
    val aged = (1..3).fold(reload(issued)) { current, _ ->
      engine.advanceTurn(current.copy(pendingEvent = null), pack)
    }
    val debt = aged.player.finance.personalLoans.single()
    assertEquals(3, debt.lateMonths)
    assertTrue(debt.balance > LoanType.shady.amount(country))
    assertTrue(aged.player.finance.creditScore < issued.player.finance.creditScore)
    assertTrue(aged.timeline.any { it.title == "Loan Payments Overdue" && "harassing" in it.detail })
    val money = aged.copy(pendingEvent = null, player = aged.player.copy(finance = aged.player.finance.copy(cash = debt.balance + 10)))
    val paid = success(money, PlayerAction.RepayLoan(LoanType.shady, true))
    assertTrue(paid.player.finance.personalLoans.isEmpty())
    assertEquals(10L, paid.player.finance.cash)
  }

  @Test
  fun marketTracksOwnershipAndResaleWithoutCreatingCash() {
    val item = Goods.laptop
    val purchase = success(adult, PlayerAction.Shop(item))
    assertEquals(adult.player.finance.cash - item.cost(country), purchase.player.finance.cash)
    assertEquals(setOf(item), reload(purchase).player.finance.possessions)
    assertFalse(act(purchase, PlayerAction.Shop(item)).success)
    val sale = success(purchase, PlayerAction.Shop(item, sell = true))
    assertEquals(purchase.player.finance.cash + item.resaleValue(country), sale.player.finance.cash)
    assertTrue(sale.player.finance.possessions.isEmpty())
    assertTrue(sale.player.finance.cash < adult.player.finance.cash)
  }

  @Test
  fun familyHardshipCanLeadToHousingLossAndShelterReducesHealthHarm() {
    val strained = adult.copy(
      player = adult.player.copy(finance = adult.player.finance.copy(cash = -25000, hardshipMonths = 8)),
      npcs = adult.npcs.map { it.copy(tension = 64.0, affection = 46.0) },
    )
    val kickedOut = engine.advanceTurn(strained, pack).copy(pendingEvent = null)
    assertEquals("unhoused", kickedOut.player.finance.housingId)
    assertTrue(kickedOut.timeline.any { it.title == "Lost Family Housing" })
    assertFalse(act(kickedOut, PlayerAction.AskFamilyForShelter).success)
    val shelter = success(kickedOut, PlayerAction.SeekEmergencyAid)
    assertTrue(shelter.player.finance.receivedEmergencyAid)
    assertEquals("emergency_shelter", shelter.player.finance.housingId)
    assertFalse(act(shelter, PlayerAction.SeekEmergencyAid).success)
    val outside = engine.advanceTurn(kickedOut, pack)
    val protected = engine.advanceTurn(shelter, pack)
    assertTrue(protected.player.stats.physicalHealth > outside.player.stats.physicalHealth)
  }

  @Test
  fun settlementReducesTheOverdraftOnceAndLeavesOtherLoansInPlace() {
    val loans = listOf(PersonalLoan(LoanType.emergency, 500))
    val finance = adult.player.finance.copy(cash = -2_000_000, hardshipMonths = 7, personalLoans = loans)
    val indebted = adult.copy(player = adult.player.copy(finance = finance))
    val ineligible = indebted.copy(player = indebted.player.copy(finance = finance.copy(possessions = setOf(Goods.car))))
    assertFalse(act(ineligible, PlayerAction.SettleOverdraft).success)
    val result = success(indebted, PlayerAction.SettleOverdraft)
    assertEquals(-country.baseAnnualLivingCost / 12, result.player.finance.cash)
    assertEquals(300, result.player.finance.creditScore)
    assertEquals(loans, result.player.finance.personalLoans)
    val loaded = reload(result)
    val newDebt = loaded.copy(player = loaded.player.copy(finance = loaded.player.finance.copy(cash = -2_000_000, hardshipMonths = 7)))
    assertFalse(act(newDebt, PlayerAction.SettleOverdraft).success)
  }

  @Test
  fun courtshipTakesTwoTurnsAndSurvivesSaveReload() {
    val strong = adult.copy(player = adult.player.copy(stats = adult.player.stats.copy(charisma = 95.0)))
    val match = success(strong, PlayerAction.FindDate(Gender.female))
    assertEquals(Gender.female, match.player.dateProspect?.gender)
    assertFalse(act(match, PlayerAction.DefineRelationship).success)
    val chat = success(match, PlayerAction.ChatWithMatch)
    assertFalse(act(chat, PlayerAction.ChatWithMatch).success)
    val date = PlayerAction.GoOnFirstDate(engine.preferredFirstDate(chat.player.dateProspect!!))
    val first = success(chat, date)
    assertEquals(1, first.player.courtshipDates)
    assertFalse(act(first, date).success)
    val next = engine.advanceTurn(reload(first), pack).copy(pendingEvent = null)
    assertEquals(first.player.dateProspect?.id, next.player.dateProspect?.id)
    val second = success(next, date)
    val together = success(second, PlayerAction.DefineRelationship)
    assertEquals(null, together.player.dateProspect)
    assertEquals(first.player.dateProspect?.id, together.npcs.single(::isRomantic).id)
  }

  @Test
  fun interviewsConsumeOneAttemptAndFailurePreservesExistingJob() {
    val task = PlayerAction.ApplyJob("part_time", "barista", InterviewAnswer.dependable)
    val employed = adult.copy(player = adult.player.copy(job = JobState("part_time", "tutor", "Tutor", 22000, performance = 88.0, monthsInTier = 24)))
    val outcomes = (1L..60).map { act(employed.copy(rngState = it), task) }
    val rejection = outcomes.first { !it.success }
    assertEquals(employed.player.job, rejection.state.player.job)
    assertEquals(employed.player.actionPoints - 1, rejection.state.player.actionPoints)
    assertFalse(act(rejection.state, task).success)
    assertEquals("barista", outcomes.first { it.success }.state.player.job?.tierId)
    assertEquals(10, pack.careers.filter { it.isPartTime }.sumOf { it.tiers.size })
  }

  @Test
  fun bypassingDecisionsStillAppliesConsequencesAndConsumesTheEvent() {
    val event = pack.events.single { it.id == "first_bike" }
    val pending = adult.copy(pendingEvent = event, player = adult.player.copy(stats = adult.player.stats.copy(stress = 30.0, discipline = 50.0)))
    val skipped = engine.resolveEventChoice(pending, engine.SKIP_DECISION, pack)
    assertEquals(null, skipped.pendingEvent)
    assertEquals(28.0, skipped.player.stats.stress, 0.0)
    assertEquals(49.0, skipped.player.stats.discipline, 0.0)
    assertTrue(event.id in skipped.triggeredOneShots)
    assertEquals(skipped.turn, skipped.eventHistory[event.id])
    assertEquals(skipped, engine.resolveEventChoice(skipped, engine.SKIP_DECISION, pack))
  }

  @Test
  fun giftsAndWellnessRespectCostsTasteAndRecovery() {
    val friend = adult.npcs.first()
    val gift = PlayerAction.GiveGift(friend.id, Gift.letter)
    val gifted = success(adult, gift)
    assertEquals(adult.player.finance.cash, gifted.player.finance.cash)
    assertTrue(gifted.npcs.first().trust > friend.trust)
    assertFalse(act(gifted, gift).success)
    val habit = gifted.copy(player = gifted.player.copy(habits = listOf(ActiveHabit("smoking", 75.0))))
    assertFalse(act(habit, PlayerAction.QuitHabit("smoking")).success)
    val support = success(habit, PlayerAction.HabitSupport("smoking"))
    assertEquals(50.0, support.player.habits.single().strength, 0.0)
    val quit = success(support, PlayerAction.QuitHabit("smoking"))
    val routine = success(quit, PlayerAction.AdoptHabit("evening_walks"))
    assertEquals("evening_walks", routine.player.habits.single().id)
  }

  @Test
  fun addedDecisionsLoadAndDoNotBlockAcrossCountriesAndTurnSpeeds() {
    val distinct = mutableSetOf<String>()
    assertEquals(pack.events.size, pack.events.map { it.id }.distinct().size)
    for (c in pack.countries) for (gran in TurnGranularity.entries) for (chaos in listOf(0, 100)) {
      var state = engine.createNewLife(CreateLifeOptions(seed = 10035L + distinct.size, countryId = c.id,
        granularity = gran, chaosLevel = chaos), pack)
      repeat(120) {
        if (!state.player.alive) return@repeat
        state = engine.advanceTurn(state, pack)
        state.pendingEvent?.let { event ->
          distinct += event.id
          val available = event.choices.firstOrNull { engine.isChoiceAvailable(state, it) }
          state = engine.resolveEventChoice(state, available?.id ?: engine.SKIP_DECISION, pack)
          assertEquals(null, state.pendingEvent)
        }
      }
    }
    assertTrue(distinct.size >= 30)
  }
}
