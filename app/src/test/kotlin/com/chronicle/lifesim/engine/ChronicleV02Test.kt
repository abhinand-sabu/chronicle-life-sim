package com.chronicle.lifesim.engine

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleV02Test {
  private inline fun <reified T> asset(name: String): T =
    Json { ignoreUnknownKeys = true }.decodeFromString(File("src/main/assets/eras/modern_2020s/$name.json").readText())

  private val pack = EraPack(
    meta = asset("era"),
    countries = asset("countries"),
    education = asset("education"),
    careers = asset("careers"),
    assets = asset("assets"),
    conditions = asset("conditions"),
    events = asset("events"),
    names = asset("names"),
  )
  private val engine = SimulationEngine
  private val child = engine.createNewLife(
    CreateLifeOptions(seed = 10035, countryId = "usa", granularity = TurnGranularity.MONTHLY),
    pack,
  ).let { it.copy(player = it.player.copy(ageMonths = 96, finance = it.player.finance.copy(cash = -55))) }

  @Test
  fun commitmentsCannotSpendTheSameHoursTwice() {
    val player = child.player.copy(
      activeClub = ActiveClub(ClubId.robotics, "Robotics Club", weeklyHours = 8),
      activeProject = ActiveProject("stall", ProjectType.lemonade_stall, "Stall", 15),
      schedule = WeeklySchedule(84, 70, 3, 3, 3, 5),
    )
    assertEquals(SimulationEngine.WeeklyCommitments(8, 0, 0, 0), engine.allocateCommitments(player))
    val next = engine.advanceTurn(child.copy(player = player), pack)
    assertEquals(0.0, next.player.activeProject!!.progress, 0.0)
    assertTrue(next.player.activeClub!!.progress > player.activeClub!!.progress)
  }

  @Test
  fun forecastIncludesOpeningCashAndSeparatesFinancedTuition() {
    assertEquals(SimulationEngine.CashflowForecast(0, 0, -55, 0), engine.estimateNextTurnCashflow(child, pack))
    val student = child.copy(player = child.player.copy(ageMonths = 250, education = EducationState(currentProgramId = "uni_cs")))
    val forecast = engine.estimateNextTurnCashflow(student, pack)
    val currentMacro = pack.meta.macroStates.getValue(student.world.macroState)
    val stablePack = pack.copy(meta = pack.meta.copy(macroStates = pack.meta.macroStates.mapValues { currentMacro }))
    assertTrue(forecast.financedTuition > 0)
    assertEquals(-55 + forecast.income - forecast.cashExpenses, forecast.projectedCash)
    assertEquals(engine.advanceTurn(student, stablePack).player.finance.cash, forecast.projectedCash)
  }

  @Test
  fun paidActivitySpendsAPAndItsCooldownSurvivesSerialization() {
    val earned = engine.performPlayerAction(child, PlayerAction.DoPaidActivity, pack)
    assertTrue(earned.success)
    assertEquals(-25L, earned.state.player.finance.cash)
    assertEquals(child.player.actionPoints - 1, earned.state.player.actionPoints)
    val serializer = GameState.serializer()
    val loaded = Json.decodeFromString(serializer, Json.encodeToString(serializer, earned.state))
    assertFalse(engine.performPlayerAction(loaded, PlayerAction.DoPaidActivity, pack).success)
    val later = loaded.copy(player = loaded.player.copy(ageMonths = loaded.player.ageMonths + 3))
    assertTrue(engine.performPlayerAction(later, PlayerAction.DoPaidActivity, pack).success)
  }

  @Test
  fun projectOffersCoverOverlappingAgeRangesAndFamilyWaitSpendsAP() {
    val country = pack.country("usa")
    assertEquals(
      listOf(ProjectType.lemonade_stall, ProjectType.passion_zine),
      engine.projectOffers(13, country).map { it.type },
    )
    assertEquals(
      listOf(ProjectType.passion_zine, ProjectType.portfolio_app),
      engine.projectOffers(18, country).map { it.type },
    )
    val adult = child.copy(player = child.player.copy(ageMonths = 300))
    val partnered = engine.performPlayerAction(adult, PlayerAction.MeetSomeone(true), pack).state
    val wait = engine.performPlayerAction(partnered, PlayerAction.DiscussFamily("wait"), pack)
    assertTrue(wait.success)
    assertEquals(partnered.player.actionPoints - 1, wait.state.player.actionPoints)
    assertEquals(partnered.npcs.size, wait.state.npcs.size)
  }

  @Test
  fun datingSavesAProspectAndChargesOnlyForAvailableChoices() {
    assertFalse(engine.performPlayerAction(child, PlayerAction.FindDate(), pack).success)
    val adult = child.copy(player = child.player.copy(ageMonths = 300, actionPoints = 5))
    val search = engine.performPlayerAction(adult, PlayerAction.FindDate(), pack)
    assertTrue(search.success)
    assertEquals(4, search.state.player.actionPoints)
    val prospect = search.state.player.dateProspect!!
    assertTrue(getAgeYears(prospect.ageMonths) >= pack.country("usa").legalAdultAge)
    assertFalse(engine.performPlayerAction(search.state, PlayerAction.FindDate(), pack).success)
    assertFalse(engine.performPlayerAction(search.state, PlayerAction.GoOnFirstDate(FirstDatePlan.dinner), pack).success)
    val serializer = GameState.serializer()
    val saved = Json.decodeFromString(serializer, Json.encodeToString(serializer, search.state))
    assertEquals(prospect, saved.player.dateProspect)
    val walk = engine.performPlayerAction(saved, PlayerAction.GoOnFirstDate(FirstDatePlan.walk), pack)
    assertTrue(walk.success)
    assertEquals(3, walk.state.player.actionPoints)
    assertEquals(-55L, walk.state.player.finance.cash)
    assertEquals(prospect.id, walk.state.player.dateProspect?.id)
    assertEquals(saved.npcs, walk.state.npcs)
    assertEquals(prospect.id, engine.advanceTurn(saved, pack).player.dateProspect?.id)
  }

  @Test
  fun argumentChangesAllApplicableRelationshipMetrics() {
    val target = child.npcs.first().copy(affection = 70.0, trust = 60.0, respect = 55.0, tension = 25.0)
    val before = child.copy(npcs = listOf(target))
    val result = engine.performPlayerAction(before, PlayerAction.InteractNpc(target.id, NpcInteraction.argue), pack)
    assertTrue(result.success)
    val bond = result.state.npcs.single()
    assertEquals(63.0, bond.affection, 0.0)
    assertEquals(55.0, bond.trust, 0.0)
    assertEquals(52.0, bond.respect, 0.0)
    assertEquals(34.0, bond.tension, 0.0)
    assertEquals(before.player.actionPoints - 1, result.state.player.actionPoints)
    assertEquals("argue", bond.memories.first().tag)
  }

  @Test
  fun manualWeeklyEditUsesOnlyRemainingHours() {
    val input = WeeklySchedule(60, 40, 20, 10, 23, 15)
    assertEquals(input.copy(sleep = 75, leisureAndRest = 0), engine.fitManualSchedule(input.copy(sleep = 75)))
    assertEquals(input.copy(sleep = 55, leisureAndRest = 20), engine.fitManualSchedule(input.copy(sleep = 55)))
    assertEquals(null, engine.fitManualSchedule(input.copy(sleep = 76)))
    assertEquals(null, engine.fitManualSchedule(input.copy(hustleAndSkills = -1)))
  }

  @Test
  fun manualScheduleDraftRepairsLegacyWorkShortfall() {
    val original = WeeklySchedule(75, 40, 20, 10, 23, 0)
    val ready = engine.prepareManualSchedule(original, 70)
    assertEquals(70, ready.workOrSchool)
    assertEquals(168, ready.sleep + ready.workOrSchool + ready.hustleAndSkills +
      ready.fitnessAndSelfCare + ready.familyAndSocial + ready.leisureAndRest)
    assertEquals(null, engine.fitManualSchedule(ready.copy(workOrSchool = 69), 70))
    assertEquals(ready, engine.fitManualSchedule(ready, 70))
  }
}
