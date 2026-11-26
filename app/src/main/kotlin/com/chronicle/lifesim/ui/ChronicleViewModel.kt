package com.chronicle.lifesim.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chronicle.lifesim.engine.*
import java.io.IOException
import kotlin.math.roundToInt

enum class ActiveTab { LIFE, CAREER, ASSETS, PEOPLE, SCHEDULE }

class ChronicleViewModel(app: Application) : AndroidViewModel(app) {
  lateinit var pack: EraPack
    private set
  private val saveManager = SaveManager(app)

  var saveSlots by mutableStateOf<Map<Int, SaveSlot>>(emptyMap())
    private set
  var activeSlotId by mutableIntStateOf(1)
    private set
  var gameState by mutableStateOf<GameState?>(null)
    private set
  var activeTab by mutableStateOf(ActiveTab.LIFE)
    private set
  var statDeltas by mutableStateOf<Map<String, Int>>(emptyMap())
    private set
  var toastMessage by mutableStateOf<Pair<String, Boolean>?>(null)
    private set

  var isLoading by mutableStateOf(true)
    private set

  var isBusy by mutableStateOf(false)
    private set

  init {
    viewModelScope.launch {
      val (loadedPack, saves) = withContext(Dispatchers.IO) {
        EraLoader.loadModern2020s(app) to saveManager.loadAll()
      }
      val (lastId, loaded) = saves
      pack = loadedPack
      saveSlots = loaded
      activeSlotId = lastId
      gameState = loaded[lastId]?.state
      isLoading = false
    }
  }

  private fun gameTask(work: suspend () -> Unit) {
    if (isBusy) return
    isBusy = true
    viewModelScope.launch {
      try {
        work()
      } catch (_: IOException) {
        showToast("Could not save. Check device storage and try again.", true)
      } finally {
        isBusy = false
      }
    }
  }

  private suspend fun commitState(next: GameState, prev: GameState? = null, slotId: Int = activeSlotId) {
    saveSlots = withContext(Dispatchers.IO) { saveManager.saveToSlot(slotId, next) }
    activeSlotId = slotId
    if (prev != null) {
      val a = prev.player.stats
      val b = next.player.stats
      statDeltas = mapOf(
        "physicalHealth" to (b.physicalHealth - a.physicalHealth).roundToInt(),
        "mentalHealth" to (b.mentalHealth - a.mentalHealth).roundToInt(),
        "happiness" to (b.happiness - a.happiness).roundToInt(),
        "stress" to (b.stress - a.stress).roundToInt(),
      ).filterValues { it != 0 }
    }
    gameState = next
  }

  fun showToast(msg: String, isError: Boolean = false) {
    toastMessage = msg to isError
  }

  fun dismissToast() {
    toastMessage = null
  }

  fun findEmptySlot(): Int? = saveManager.findEmptySlot(saveSlots)

  fun startNewLifeInSlot(options: CreateLifeOptions, slotId: Int) = gameTask {
    val fresh = withContext(Dispatchers.Default) { SimulationEngine.createNewLife(options, pack) }
    commitState(fresh, slotId = slotId)
    statDeltas = emptyMap()
    activeTab = ActiveTab.LIFE
    showToast("Welcome, ${fresh.player.firstName}! (Slot $slotId)")
  }

  fun startNewLife(options: CreateLifeOptions) {
    val slot = saveManager.pickSlotForNewLife(saveSlots, activeSlotId)
    startNewLifeInSlot(options, slot)
  }

  var ageInputLocked by mutableStateOf(false)
    private set
  private var ageUnlockAt = 0L

  fun noteBlockedAgeTap() {
    if (ageInputLocked) holdAgeInput()
  }

  private fun holdAgeInput() {
    ageUnlockAt = SystemClock.elapsedRealtime() + 1100L
    if (ageInputLocked) return
    ageInputLocked = true
    viewModelScope.launch {
      var wait = ageUnlockAt - SystemClock.elapsedRealtime()
      while (wait > 0L) {
        delay(wait)
        wait = ageUnlockAt - SystemClock.elapsedRealtime()
      }
      ageInputLocked = false
    }
  }

  fun advanceTurn() {
    if (ageInputLocked) {
      noteBlockedAgeTap()
      return
    }
    gameTask {
      val cur = gameState ?: return@gameTask
      if (!cur.player.alive || cur.pendingEvent != null) return@gameTask
      holdAgeInput()
      dismissToast()
      val next = withContext(Dispatchers.Default) { SimulationEngine.advanceTurn(cur, pack) }
      commitState(next, cur)
      activeTab = ActiveTab.LIFE
      if (next.player.alive && next.pendingEvent == null) holdAgeInput()
    }
  }

  fun resolveEvent(choiceId: String) = gameTask {
    val cur = gameState ?: return@gameTask
    val next = withContext(Dispatchers.Default) { SimulationEngine.resolveEventChoice(cur, choiceId, pack) }
    if (next != cur) {
      commitState(next, cur)
      holdAgeInput()
    }
  }

  fun performAction(action: PlayerAction) = gameTask {
    val cur = gameState ?: return@gameTask
    val res = withContext(Dispatchers.Default) { SimulationEngine.performPlayerAction(cur, action, pack) }
    if (res.success) {
      commitState(res.state, cur)
      showToast(res.message, false)
    } else {
      if (res.state != cur) commitState(res.state)
      showToast(res.message, true)
    }
  }

  fun updateSchedule(schedule: WeeklySchedule) = gameTask {
    val cur = gameState ?: return@gameTask
    val requiredWork = SimulationEngine.getRequiredWorkOrSchoolHours(cur, pack)
    val updated = SimulationEngine.fitManualSchedule(schedule, requiredWork) ?: return@gameTask
    if (updated != cur.player.schedule) {
      commitState(cur.copy(player = cur.player.copy(schedule = updated)))
      showToast("Weekly schedule saved.")
    }
  }

  fun applyPreset(preset: SchedulePresetId) = gameTask {
    val cur = gameState ?: return@gameTask
    val base = SimulationEngine.SCHEDULE_PRESETS.getValue(preset)
    val req = SimulationEngine.getRequiredWorkOrSchoolHours(cur, pack)
    val sched = SimulationEngine.normalizeSchedule(
      base.copy(workOrSchool = maxOf(base.workOrSchool, req)),
    )
    commitState(cur.copy(player = cur.player.copy(schedule = sched)))
    showToast("Applied ${preset.name.replace('_', ' ')} schedule.")
  }

  fun setFocusSkill(skill: SkillId) = gameTask {
    val cur = gameState ?: return@gameTask
    commitState(cur.copy(player = cur.player.copy(focusSkill = skill)))
    showToast("Focus skill: ${skill.name.replace('_', ' ')}")
  }

  fun applyDirector(mutation: DirectorMutation) = gameTask {
    val cur = gameState ?: return@gameTask
    val res = SimulationEngine.applyDirectorMutation(cur, mutation)
    if (res.applied) {
      commitState(res.state, cur)
      showToast(res.message, false)
    } else {
      showToast(res.message, true)
    }
  }

  fun loadSlot(slotId: Int) = gameTask {
    val target = saveSlots[slotId] ?: return@gameTask
    withContext(Dispatchers.IO) { saveManager.setActiveSlotId(slotId) }
    activeSlotId = slotId
    statDeltas = emptyMap()
    activeTab = ActiveTab.LIFE
    gameState = target.state
    showToast("Loaded ${target.state.player.firstName} (Slot $slotId)")
  }

  fun saveToSlot(slotId: Int) = gameTask {
    val cur = gameState ?: return@gameTask
    commitState(cur, slotId = slotId)
    showToast("Saved to Slot $slotId")
  }

  fun deleteSlot(slotId: Int) = gameTask {
    saveSlots = withContext(Dispatchers.IO) { saveManager.deleteSlot(slotId) }
    if (slotId == activeSlotId && gameState != null) {
      gameState = null
    }
    showToast("Cleared Slot $slotId")
  }

  fun openNewLifeSetup() {
    if (!isBusy) gameState = null
  }

  fun selectTab(tab: ActiveTab) {
    activeTab = tab
  }
}
