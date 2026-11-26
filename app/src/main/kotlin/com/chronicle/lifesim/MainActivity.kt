package com.chronicle.lifesim

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.chronicle.lifesim.ui.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import com.chronicle.lifesim.ui.theme.ChronicleTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val preferences = getSharedPreferences(APPEARANCE_PREFS, MODE_PRIVATE)
    ChronicleColors.updateDarkMode(preferences.getBoolean(DARK_MODE_KEY, false))
    setContent {
      val dark = ChronicleColors.darkMode
      SideEffect {
        val style = if (dark) {
          SystemBarStyle.dark(AndroidColor.parseColor("#151B18"))
        } else {
          SystemBarStyle.light(AndroidColor.parseColor("#F4F1E7"), AndroidColor.parseColor("#151B18"))
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
      }
      ChronicleTheme {
        ChronicleApp()
      }
    }
  }
}

@Composable
fun ChronicleApp(vm: ChronicleViewModel = viewModel()) {
  if (vm.isLoading) {
    Box(
      Modifier.fillMaxSize().background(ChronicleColors.BgApp),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(color = ChronicleColors.Emerald)
    }
    return
  }
  var directorOpen by remember { mutableStateOf(false) }
  var savesOpen by remember { mutableStateOf(false) }
  var pendingNewLifeOptions by remember { mutableStateOf<com.chronicle.lifesim.engine.CreateLifeOptions?>(null) }

  val toast = vm.toastMessage
  LaunchedEffect(toast) {
    if (toast != null) {
      delay(if (toast.second) 5500L else 3800L)
      vm.dismissToast()
    }
  }

  CompositionLocalProvider(LocalChronicleNotice provides toast, LocalDismissChronicleNotice provides vm::dismissToast) {
    Column(Modifier.fillMaxSize().background(ChronicleColors.BgApp)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        val state = vm.gameState
        if (state == null) {
          SetupScreen(
            pack = vm.pack,
            hasSaves = vm.saveSlots.isNotEmpty(),
            onStartLife = start@{ opts ->
              if (vm.isBusy) return@start
              val empty = vm.findEmptySlot()
              if (empty != null) {
                vm.startNewLifeInSlot(opts, empty)
              } else {
                pendingNewLifeOptions = opts
              }
            },
            onOpenSaves = { savesOpen = true },
          )
        } else if (!state.player.alive && state.deathSummary != null) {
          DeathSummaryScreen(
            state = state,
            pack = vm.pack,
            onNewLife = vm::openNewLifeSetup,
            onOpenSaves = { savesOpen = true },
          )
        } else {
          ActiveGameShell(
            vm = vm,
            state = state,
            onOpenDirector = { directorOpen = true },
            onOpenSaves = { savesOpen = true },
          )

          state.pendingEvent?.let { evt ->
            EventDialog(
              state = state,
              event = evt,
              pack = vm.pack,
              onSelectChoice = vm::resolveEvent,
            )
          }

          if (directorOpen) {
            DirectorDialog(
              state = state,
              onMutate = vm::applyDirector,
              onClose = { directorOpen = false },
            )
          }
        }

        if (savesOpen) {
          SaveSlotsDialog(
            slots = vm.saveSlots,
            activeSlotId = vm.activeSlotId,
            currentState = vm.gameState,
            pack = vm.pack,
            onLoad = {
              vm.loadSlot(it)
              savesOpen = false
            },
            onSave = vm::saveToSlot,
            onDelete = vm::deleteSlot,
            onNewLife = {
              savesOpen = false
              directorOpen = false
              vm.openNewLifeSetup()
            },
            onClose = { savesOpen = false },
          )
        }

        pendingNewLifeOptions?.let { opts ->
          OverwriteSlotDialog(
            slots = vm.saveSlots,
            pack = vm.pack,
            onSelectSlot = { slotId ->
              pendingNewLifeOptions = null
              vm.startNewLifeInSlot(opts, slotId)
            },
            onCancel = { pendingNewLifeOptions = null },
          )
        }

      }
      val mainDialog = savesOpen || directorOpen || pendingNewLifeOptions != null || vm.gameState?.pendingEvent != null
      if (!mainDialog) ChronicleNotice(respectNavigationBar = true)
    }
  }
}
