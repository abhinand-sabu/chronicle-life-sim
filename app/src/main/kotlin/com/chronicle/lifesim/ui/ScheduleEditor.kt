package com.chronicle.lifesim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.engine.SimulationEngine
import com.chronicle.lifesim.engine.WeeklySchedule
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt

private enum class WeekHours(
  val label: String,
  val ceiling: Int,
  val read: (WeeklySchedule) -> Int,
  val write: (WeeklySchedule, Int) -> WeeklySchedule,
) {
  sleep("Sleep", 84, WeeklySchedule::sleep, { s, h -> s.copy(sleep = h) }),
  work("Work / school", 80, WeeklySchedule::workOrSchool, { s, h -> s.copy(workOrSchool = h) }),
  skills("Hustle & skills", 50, WeeklySchedule::hustleAndSkills, { s, h -> s.copy(hustleAndSkills = h) }),
  fitness("Fitness & self-care", 35, WeeklySchedule::fitnessAndSelfCare, { s, h -> s.copy(fitnessAndSelfCare = h) }),
  family("Family & social", 60, WeeklySchedule::familyAndSocial, { s, h -> s.copy(familyAndSocial = h) }),
}

@Composable
internal fun ScheduleEditorDialog(
  saved: WeeklySchedule,
  requiredWork: Int,
  busy: Boolean,
  onSave: (WeeklySchedule) -> Unit,
  onClose: () -> Unit,
) {
  val starting = remember(saved, requiredWork) { SimulationEngine.prepareManualSchedule(saved, requiredWork) }
  var draft by remember(starting) { mutableStateOf(starting) }
  val free = draft.leisureAndRest
  fun minimum(field: WeekHours) = when (field) {
    WeekHours.sleep -> 21
    WeekHours.work -> requiredWork
    else -> 0
  }
  fun setHours(field: WeekHours, requested: Int) {
    val current = field.read(draft)
    val ceiling = maxOf(current, minOf(field.ceiling, current + free))
    val next = requested.coerceIn(minimum(field), ceiling)
    draft = SimulationEngine.fitManualSchedule(field.write(draft, next)) ?: draft
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    CardBox(Modifier.fillMaxWidth(0.94f).heightIn(max = 630.dp)) {
      JournalPageTitle("Customize week", "$free of 168h for leisure & rest")
      Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val guidance = when {
          starting != saved && draft == starting -> "This draft reserves ${requiredWork}h for work and school. Review the other hours before saving."
          free == 0 -> "All hours are allocated. Reduce an activity to free time before adding hours elsewhere."
          else -> "Edit as many activities as you like, then save once."
        }
        Text(guidance, color = ChronicleColors.TextSecondary)
        WeekHours.entries.forEach { field ->
          val hours = field.read(draft)
          ScheduleHourSlider(
            label = field.label,
            hours = hours,
            minimum = minimum(field),
            trackMaximum = maxOf(hours, field.ceiling),
            remaining = free,
            onCommit = { setHours(field, it) },
          )
        }
      }
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text("Cancel") }
        Button(
          enabled = !busy && draft != saved,
          onClick = { onSave(draft); onClose() },
        ) { Text("Save week") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleHourSlider(
  label: String,
  hours: Int,
  minimum: Int,
  trackMaximum: Int,
  remaining: Int,
  onCommit: (Int) -> Unit,
) {
  val limit = minOf(trackMaximum, hours + remaining)
  val slider = remember(hours, minimum, limit) {
    SliderState(value = hours.toFloat(), valueRange = minimum.toFloat()..limit.toFloat())
  }
  slider.onValueChangeFinished = {
    val rounded = slider.value.roundToInt()
    slider.value = rounded.toFloat()
    if (rounded != hours) onCommit(rounded)
  }
  val previewHours by remember(slider) { derivedStateOf { slider.value.roundToInt() } }
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label)
      Text("${previewHours}h", fontWeight = FontWeight.Bold)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { onCommit(hours - 1) },
        enabled = hours > minimum,
        modifier = Modifier.semantics { contentDescription = "Decrease $label by 1 hour" },
      ) { Text("−") }
      Slider(
        state = slider,
        enabled = limit > minimum,
        modifier = Modifier.weight(1f).semantics { contentDescription = label },
      )
      IconButton(
        onClick = { onCommit(hours + 1) },
        enabled = hours < limit,
        modifier = Modifier.semantics { contentDescription = "Increase $label by 1 hour" },
      ) { Text("+") }
    }
  }
}
