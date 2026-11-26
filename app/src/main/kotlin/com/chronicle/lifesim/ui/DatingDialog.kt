package com.chronicle.lifesim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt

@Composable
internal fun DatingDialog(
  state: GameState,
  country: CountryDef,
  busy: Boolean,
  onAction: (PlayerAction) -> Unit,
  onClose: () -> Unit,
) {
  val player = state.player
  val prospect = player.dateProspect
  val foundPartner = state.npcs.any(::isRomantic)
  var preference by rememberSaveable { mutableStateOf<Gender?>(null) }
  LaunchedEffect(foundPartner) {
    if (foundPartner) onClose()
  }
  LifeServiceDialog("Dating", if (prospect == null) "Find a match" else "Getting to know each other", onClose) {
    ActionPointsLabel(player.actionPoints, player.maxActionPoints)
    if (prospect == null) {
      Text("Who would you like to meet?")
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = preference == null, onClick = { preference = null }, label = { Text("Anyone") })
        FilterChip(selected = preference == Gender.male, onClick = { preference = Gender.male }, label = { Text("Men") })
        FilterChip(selected = preference == Gender.female, onClick = { preference = Gender.female }, label = { Text("Women") })
      }
      FilterChip(
        selected = preference == Gender.non_binary,
        onClick = { preference = Gender.non_binary },
        label = { Text("Nonbinary people") },
      )
      Button(onClick = { onAction(PlayerAction.FindDate(preference)) }, enabled = !busy && player.actionPoints > 0) {
        Text("Find a match · 1 AP")
      }
    } else {
      Text("${prospect.firstName} ${prospect.lastName}", style = MaterialTheme.typography.titleLarge)
      val personality = when {
        prospect.personality.warmth >= 72 -> "Warm and sociable"
        prospect.personality.ambition >= 70 -> "Ambitious"
        prospect.personality.loyalty >= 70 -> "Loyal"
        else -> "Easygoing"
      }
      Text("${formatAge(prospect.ageMonths)} · $personality", color = ChronicleColors.TextSecondary)
      Text("Affection ${prospect.affection.roundToInt()} · Trust ${prospect.trust.roundToInt()} · Good dates ${player.courtshipDates.coerceAtMost(2)}/2")
      if (player.lastDateChatTurn != null) {
        Text("Enjoys: ${SimulationEngine.preferredFirstDate(prospect).label()}", color = ChronicleColors.TextSecondary)
      }
      OutlinedButton(
        onClick = { onAction(PlayerAction.ChatWithMatch) },
        enabled = !busy && player.actionPoints > 0 && player.lastDateChatTurn != state.turn,
      ) { Text("Get to know them · 1 AP") }
      if (player.lastDateTurn != state.turn) {
        SectionTitle("Plan a date", "Choose an outing")
        FirstDatePlan.entries.forEach { plan ->
          val cost = SimulationEngine.firstDateCost(plan, country)
          val costLabel = if (cost == 0L) "Free" else formatExactMoney(cost, country)
          OutlinedButton(
            onClick = { onAction(PlayerAction.GoOnFirstDate(plan)) },
            enabled = !busy && player.actionPoints > 0 && (cost == 0L || player.finance.cash >= cost),
            modifier = Modifier.fillMaxWidth(),
          ) { Text("${plan.label()} · $costLabel · 1 AP") }
        }
      } else {
        Text("You’ve been on a date this turn. You can plan another after aging up.", color = ChronicleColors.TextSecondary)
      }
      if (player.courtshipDates >= 2 && prospect.trust >= 50 && prospect.affection >= 58) {
        Button(onClick = { onAction(PlayerAction.DefineRelationship) }, enabled = !busy) { Text("Ask to become partners · 0 AP") }
      } else {
        Text("To become partners, share 2 good dates and reach 58 Affection and 50 Trust.", color = ChronicleColors.TextSecondary)
      }
      TextButton(onClick = { onAction(PlayerAction.PassDate) }, enabled = !busy) {
        Text(if (player.courtshipDates > 0) "Stay friends" else "Pass on this match")
      }
    }
  }
}

internal fun FirstDatePlan.label(): String = when (this) {
  FirstDatePlan.walk -> "Walk in the park"
  FirstDatePlan.coffee -> "Coffee"
  FirstDatePlan.dinner -> "Dinner"
  FirstDatePlan.museum -> "Museum"
  FirstDatePlan.picnic -> "Picnic"
}
