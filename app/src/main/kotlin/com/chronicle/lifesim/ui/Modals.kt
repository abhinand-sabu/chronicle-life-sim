package com.chronicle.lifesim.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt

@Composable
fun EventDialog(
  event: GameEvent,
  state: GameState,
  pack: EraPack,
  onSelectChoice: (String) -> Unit,
) {
  val country = pack.country(state.config.countryId)
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
  ) {
    CardBox(
      bgColor = ChronicleColors.BgCard,
      borderColor = ChronicleColors.BorderStrong,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .heightIn(max = 580.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          event.category.uppercase(),
          color = ChronicleColors.Emerald,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.7.sp,
        )
        Text(
          "${country.flag} Age ${formatAge(state.player.ageMonths)}",
          color = ChronicleColors.TextMuted,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(event.title, color = ChronicleColors.TextPrimary, fontFamily = ChronicleColors.Display, fontSize = 27.sp, lineHeight = 32.sp)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          event.body,
          color = ChronicleColors.TextSecondary,
          fontSize = 14.sp,
          lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          event.choices.forEach { choice ->
            val available = SimulationEngine.isChoiceAvailable(state, choice)
            val reqHint = choice.requirements?.let { r ->
              buildList {
                r.minCash?.let { add("Cash ${formatMoney(it, country)}+") }
                r.minIntelligence?.let { add("Intellect ${it.roundToInt()}+") }
                r.minCharisma?.let { add("Charisma ${it.roundToInt()}+") }
              }.joinToString(" · ").ifEmpty { null }
            }

            Surface(
              onClick = { if (available) onSelectChoice(choice.id) },
              enabled = available,
              shape = RoundedCornerShape(11.dp),
              color = if (available) ChronicleColors.BgElevated else ChronicleColors.BgInput,
              border = BorderStroke(1.dp, if (available) ChronicleColors.Emerald.copy(alpha = 0.55f) else ChronicleColors.BorderSubtle),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                Text(
                  choice.label,
                  color = if (available) ChronicleColors.TextPrimary else ChronicleColors.TextMuted,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Bold,
                )
                if (reqHint != null) {
                  Text(
                    "Requires: $reqHint",
                    color = if (available) ChronicleColors.Sky else ChronicleColors.Rose,
                    fontSize = 11.sp,
                  )
                }
              }
            }
          }
          TextButton(onClick = { onSelectChoice(SimulationEngine.SKIP_DECISION) }, modifier = Modifier.fillMaxWidth()) {
            Text("Let it pass · −2 Stress, −1 Discipline")
          }
        }
      }
    }
  }
}

private enum class DirectorStat(val key: String, val label: String, val read: (PlayerStats) -> Double) {
  happiness("happiness", "Happiness", PlayerStats::happiness),
  physicalHealth("physicalHealth", "Physical health", PlayerStats::physicalHealth),
  stress("stress", "Stress", PlayerStats::stress),
  intelligence("intelligence", "Intelligence", PlayerStats::intelligence),
  charisma("charisma", "Charisma", PlayerStats::charisma),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectorDialog(
  state: GameState,
  onMutate: (DirectorMutation) -> Unit,
  onClose: () -> Unit,
) {
  var cashText by remember(state.player.finance.cash) { mutableStateOf(state.player.finance.cash.toString()) }
  var selectedStat by remember { mutableStateOf(DirectorStat.happiness) }
  val currentValue = selectedStat.read(state.player.stats).toFloat()
  var statVal by remember(selectedStat, currentValue) { mutableFloatStateOf(currentValue) }
  val ironman = state.config.ironman

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    CardBox(
      borderColor = ChronicleColors.BorderSubtle,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .heightIn(max = 580.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Director Mode", color = ChronicleColors.TextPrimary, fontFamily = ChronicleColors.Display, fontSize = 24.sp)
        IconButton(onClick = onClose) {
          Icon(Icons.Outlined.Close, contentDescription = "Close director mode", tint = ChronicleColors.TextSecondary)
        }
      }
      ChronicleNotice()
      ThemeToggle()
      if (ironman) {
        Text("Ironman active — only Turn Speed can be adjusted.", color = ChronicleColors.Rose, fontSize = 12.sp)
      }
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("TURN SPEED", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        TurnGranularity.entries.chunked(2).forEach { pair ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { g ->
              ChoiceChipButton(
                label = formatGranularityLabel(g),
                selected = state.config.granularity == g,
                onClick = { onMutate(DirectorMutation.SetGranularity(g)) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        if (!ironman) {
          Text("SET CASH", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = cashText,
              onValueChange = { cashText = it },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Button(
              onClick = { cashText.toLongOrNull()?.let { onMutate(DirectorMutation.SetCash(it)) } },
              shape = RoundedCornerShape(9.dp),
            ) { Text("Set", fontSize = 12.sp) }
          }

          Text("MACRO ECONOMY", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroStateId.entries.forEach { m ->
              ChoiceChipButton(
                label = m.name.replaceFirstChar { it.uppercase() },
                selected = state.world.macroState == m,
                onClick = { onMutate(DirectorMutation.SetMacroState(m)) },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Text("ADJUST STAT", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DirectorStat.entries.forEach { stat ->
              ChoiceChipButton(
                label = stat.label,
                selected = selectedStat == stat,
                onClick = { selectedStat = stat },
              )
            }
          }
          SliderRow(selectedStat.label, statVal.roundToInt(), 0f..100f, "${statVal.roundToInt()}%") { statVal = it }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { onMutate(DirectorMutation.SetPlayerStat(selectedStat.key, statVal.toDouble())) },
              modifier = Modifier.weight(1f),
            ) { Text("Apply Stat", fontSize = 12.sp) }
            OutlinedButton(
              onClick = { onMutate(DirectorMutation.CureAllConditions) },
              modifier = Modifier.weight(1f),
            ) { Text("Cure All", fontSize = 12.sp) }
          }
        }
      }
    }
  }
}

@Composable
fun SaveSlotsDialog(
  slots: Map<Int, SaveSlot>,
  activeSlotId: Int,
  currentState: GameState?,
  pack: EraPack,
  onLoad: (Int) -> Unit,
  onSave: (Int) -> Unit,
  onDelete: (Int) -> Unit,
  onNewLife: () -> Unit,
  onClose: () -> Unit,
) {
  var pendingDeleteSlot by remember { mutableStateOf<Int?>(null) }
  val hasActiveGame = currentState != null

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    CardBox(
      borderColor = ChronicleColors.BorderStrong,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .heightIn(max = 580.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Save Slots", color = ChronicleColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        IconButton(onClick = onClose) {
          Icon(Icons.Outlined.Close, contentDescription = "Close saves", tint = ChronicleColors.TextSecondary)
        }
      }
      ChronicleNotice()
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        (1..5).forEach { slotId ->
          val slot = slots[slotId]
          val isActive = slotId == activeSlotId
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(ChronicleColors.BgInput)
              .border(1.dp, if (isActive) ChronicleColors.Sky else ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                text = if (slot != null) "Slot $slotId · ${slot.state.player.firstName} ${slot.state.player.lastName}" else "Slot $slotId · Empty",
                color = ChronicleColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
              )
              if (isActive && hasActiveGame) {
                Text("Active", color = ChronicleColors.Sky, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
              }
            }
            if (slot != null) {
              val c = pack.country(slot.state.config.countryId)
              Text(
                "${c.flag} Age ${formatAge(slot.state.player.ageMonths)} · ${formatMoney(slot.state.player.finance.cash, c)} · Turn ${slot.state.turn}",
                color = ChronicleColors.TextSecondary,
                fontSize = 12.sp,
              )
            }

            if (pendingDeleteSlot == slotId) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Delete slot $slotId?", color = ChronicleColors.Rose, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { pendingDeleteSlot = null }) { Text("Cancel", fontSize = 12.sp) }
                Button(
                  onClick = {
                    pendingDeleteSlot = null
                    onDelete(slotId)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = ChronicleColors.Rose),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Confirm", color = ChronicleColors.OnAccent, fontSize = 12.sp) }
              }
            } else {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (slot != null) {
                  OutlinedButton(
                    onClick = { onLoad(slotId) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  ) { Text("Load", fontSize = 12.sp, color = ChronicleColors.Sky) }
                }
                if (hasActiveGame) {
                  OutlinedButton(
                    onClick = { onSave(slotId) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  ) { Text(if (slot == null) "Save Here" else "Overwrite", fontSize = 12.sp) }
                }
                if (slot != null) {
                  OutlinedButton(
                    onClick = { pendingDeleteSlot = slotId },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ChronicleColors.Rose.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  ) { Text("Clear", color = ChronicleColors.Rose, fontSize = 12.sp) }
                }
              }
            }
          }
        }

      }
      Spacer(Modifier.height(4.dp))
      OutlinedButton(
        onClick = onNewLife,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ChronicleColors.Emerald),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Start New Life", color = ChronicleColors.Emerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun OverwriteSlotDialog(
  slots: Map<Int, SaveSlot>,
  pack: EraPack,
  onSelectSlot: (Int) -> Unit,
  onCancel: () -> Unit,
) {
  Dialog(
    onDismissRequest = onCancel,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    CardBox(
      borderColor = ChronicleColors.Amber,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .heightIn(max = 520.dp),
    ) {
      Text("All 5 Slots Occupied", color = ChronicleColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
      Text("Pick a save slot to replace with this new life, or press Back to cancel.", color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
      Spacer(Modifier.height(10.dp))

      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        (1..5).forEach { id ->
          val slot = slots[id]
          val subtitle = slot?.let {
            val c = pack.country(it.state.config.countryId)
            "${c.flag} ${it.state.player.firstName} ${it.state.player.lastName} · Age ${formatAge(it.state.player.ageMonths)}"
          } ?: "Empty"
          Surface(
            onClick = { onSelectSlot(id) },
            shape = RoundedCornerShape(10.dp),
            color = ChronicleColors.BgInput,
            border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Replace Slot $id", color = ChronicleColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ChronicleColors.TextSecondary, fontSize = 12.sp)
              }
              Text("Overwrite", color = ChronicleColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
      }
    }
  }
}

@Composable
fun DeathSummaryScreen(
  state: GameState,
  pack: EraPack,
  onNewLife: () -> Unit,
  onOpenSaves: () -> Unit = {},
) {
  val summary = state.deathSummary ?: return
  val p = state.player
  val country = pack.country(state.config.countryId)

  // Design Review + UX-05: Lead with up to 3 defining moments before supporting totals
  val definingMoments = remember(state.timeline) {
    val interesting = state.timeline.filter { entry ->
      entry.id != "tl_birth" && entry.category in setOf("milestone", "career", "finance", "relationship", "health", "chaos")
    }
    (interesting.ifEmpty { state.timeline }).take(3)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      JournalCover(
        title = "${p.firstName} ${p.lastName}",
        subtitle = "${country.flag} Lived to ${formatAge(summary.ageMonths)} · ${summary.causeOfDeath}",
        bottom = { ThemeToggle(onCover = true) },
      )

      if (definingMoments.isNotEmpty()) {
        CardBox(borderColor = ChronicleColors.BorderStrong) {
          SectionTitle("DEFINING MOMENTS", "Chapters that shaped this life")
          Spacer(Modifier.height(8.dp))
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            definingMoments.forEach { entry ->
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(ChronicleColors.BgInput)
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(entry.title, color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                  Text(formatAge(entry.ageMonths), color = ChronicleColors.Sky, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(entry.detail, color = ChronicleColors.TextSecondary, fontSize = 12.sp)
              }
            }
          }
        }
      }

      CardBox {
        SectionTitle("LIFE & LEGACY FIGURES")
        Spacer(Modifier.height(6.dp))
        SummaryRow("Legacy Score", "${summary.legacyScore}", ChronicleColors.Gold)
        SummaryRow("Peak Net Worth", formatMoney(summary.peakNetWorth, country), ChronicleColors.Emerald)
        SummaryRow("Final Net Worth", formatMoney(summary.finalNetWorth, country), ChronicleColors.TextPrimary)
        SummaryRow("Highest Career", summary.highestJobTitle ?: "None", ChronicleColors.Sky)
        SummaryRow("Children", "${summary.childrenCount}", ChronicleColors.Pink)
      }
    }

    // UX-05: Direct Load a Life alongside Start a New Life
    Surface(
      color = ChronicleColors.BgCard,
      border = BorderStroke(1.dp, ChronicleColors.BorderSubtle),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onOpenSaves,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, ChronicleColors.Sky),
          modifier = Modifier
            .weight(0.42f)
            .heightIn(min = 48.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Text(
            "Load a Life",
            color = ChronicleColors.Sky,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
          )
        }
        Button(
          onClick = onNewLife,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ChronicleColors.Emerald, contentColor = ChronicleColors.OnAccent),
          modifier = Modifier
            .weight(0.58f)
            .heightIn(min = 48.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Text(
            "Start a New Life",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
fun SummaryRow(label: String, value: String, valueColor: Color = ChronicleColors.TextPrimary) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = ChronicleColors.TextSecondary, fontSize = 13.sp)
    Text(value, color = valueColor, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
  }
}
