package com.chronicle.lifesim.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt

@Composable
internal fun ChapterArt(@DrawableRes picture: Int) {
  Image(
    painter = painterResource(picture),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(12.dp)),
  )
}

@Composable
internal fun LifeServiceDialog(title: String, subtitle: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    CardBox(Modifier.fillMaxWidth(.92f).heightIn(max = 600.dp)) {
      JournalPageTitle(title, subtitle)
      ChronicleNotice()
      Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content,
      )
      TextButton(onClick = onClose) { Text("Close") }
    }
  }
}

@Composable
internal fun AssetMarketDialog(state: GameState, country: CountryDef, onAction: (PlayerAction) -> Unit, onClose: () -> Unit) {
  val p = state.player
  val fin = p.finance
  val categories = remember { listOf("Transport", "Electronics", "Home", "Hobbies", "Owned") }
  var category by remember { mutableStateOf(categories.first()) }
  LifeServiceDialog("Marketplace", "Cash ${formatExactMoney(fin.cash, country)} · ${fin.possessions.size} owned", onClose) {
    ActionPointsLabel(p.actionPoints, p.maxActionPoints)
    categories.chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pair.forEach { option ->
          FilterChip(
            selected = category == option,
            onClick = { category = option },
            label = { Text(option) },
          )
        }
      }
    }
    val offers = if (category == "Owned") Goods.entries.filter { it in fin.possessions }
      else Goods.entries.filter { it.category == category }
    if (offers.isEmpty()) Text("Items you buy appear here.", color = ChronicleColors.TextSecondary)
    offers.forEach { item ->
      val owned = item in fin.possessions
      val amount = if (owned) item.resaleValue(country) else item.cost(country)
      val ageAllowed = item != Goods.car || getAgeYears(p.ageMonths) >= country.legalAdultAge
      CardBox {
        Text(item.label, style = MaterialTheme.typography.titleSmall)
        if (!ageAllowed && !owned) Text("Available at age ${country.legalAdultAge}.", color = ChronicleColors.TextSecondary)
        if (owned) Text("In your inventory", color = ChronicleColors.TextSecondary)
        OutlinedButton(
          onClick = { onAction(PlayerAction.Shop(item, sell = owned)) },
          enabled = p.actionPoints > 0 && (owned || (ageAllowed && fin.cash >= amount)),
        ) {
          Text("${if (owned) "Sell" else "Buy"} ${formatExactMoney(amount, country)} · 1 AP")
        }
      }
    }
  }
}

@Composable
internal fun CreditDialog(state: GameState, country: CountryDef, onAction: (PlayerAction) -> Unit, onClose: () -> Unit) {
  val p = state.player
  val f = p.finance
  var offer by remember { mutableStateOf<LoanType?>(null) }
  LifeServiceDialog("Loans", "Credit ${f.creditScore} · Cash ${formatExactMoney(f.cash, country)}", onClose) {
    ActionPointsLabel(p.actionPoints, p.maxActionPoints)
    Text("Payments come out of available cash each turn. Interest accrues and missed payments hurt your credit.", color = ChronicleColors.TextSecondary)
    LoanType.entries.forEach { type ->
      val loan = f.personalLoans.firstOrNull { it.type == type }
      val (allowed, reason) = SimulationEngine.canBorrow(state, type, country)
      val terms = "${formatExactMoney(type.amount(country), country)} · ${(type.apr * 1000).roundToInt() / 10.0}% annual interest · ${type.termMonths} months"
      CardBox(borderColor = if (type == LoanType.shady) ChronicleColors.Rose.copy(alpha = .5f) else ChronicleColors.BorderSubtle) {
        Text(type.label, style = MaterialTheme.typography.titleSmall)
        Text(terms, color = ChronicleColors.TextSecondary)
        Text("${formatExactMoney(type.monthlyPayment(country), country)} monthly payment", color = ChronicleColors.TextSecondary)
        if (type == LoanType.shady) Text("Missed payments can trigger harassment and extra stress.", color = ChronicleColors.Rose)
        if (loan != null) {
          Text("Balance ${formatExactMoney(loan.balance, country)}${if (loan.lateMonths > 0) " · ${loan.lateMonths} months overdue" else ""}")
          val full = f.cash >= loan.balance
          OutlinedButton(
            onClick = { onAction(PlayerAction.RepayLoan(type, payInFull = full)) },
            enabled = f.cash > 0,
          ) { Text(if (full) "Pay off · 0 AP" else "Pay available cash · 0 AP") }
        } else if (offer == type && allowed) {
          Text("Confirm that you want this loan.")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { offer = null; onAction(PlayerAction.Borrow(type)) }, enabled = p.actionPoints > 0) {
              Text("Confirm loan · 1 AP")
            }
            TextButton(onClick = { offer = null }) { Text("Cancel") }
          }
        } else {
          if (!allowed) Text(reason, color = ChronicleColors.TextSecondary)
          OutlinedButton(onClick = { offer = type }, enabled = allowed && p.actionPoints > 0) { Text("Review loan") }
        }
      }
    }
  }
}

@Composable
internal fun HardshipCard(state: GameState, country: CountryDef, onAction: (PlayerAction) -> Unit) {
  var confirmSettlement by remember { mutableStateOf(false) }
  if (confirmSettlement) {
    AlertDialog(
      onDismissRequest = { confirmSettlement = false },
      title = { Text("Settle your overdraft?") },
      text = { Text("This once-per-life option reduces the overdraft to one month of basic costs. Your credit score drops to 300. Other loans and housing stay as they are.") },
      confirmButton = {
        TextButton(onClick = { confirmSettlement = false; onAction(PlayerAction.SettleOverdraft) },
          enabled = state.player.actionPoints >= 2 && SimulationEngine.canSettleOverdraft(state, country)) { Text("Settle · 2 AP") }
      },
      dismissButton = { TextButton(onClick = { confirmSettlement = false }) { Text("Cancel") } },
    )
  }
  val f = state.player.finance
  val street = f.housingId == "unhoused"
  val shelter = f.housingId == "emergency_shelter"
  CardBox(borderColor = ChronicleColors.Rose.copy(alpha = .6f)) {
    val title = when {
      street -> "Unhoused"
      shelter -> "Staying at an emergency shelter"
      else -> "Financial hardship"
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(when {
      street -> "Sleeping outdoors strains health, energy, and mood. Emergency shelter reduces that risk."
      shelter -> "Shelter lowers living costs while you look for stable housing."
      else -> "Overdraft interest adds up. Long-term debt can strain family ties and cost you your home."
    }, color = ChronicleColors.TextSecondary)
    if (street || (f.cash < 0 && !f.receivedEmergencyAid)) {
      Button(onClick = { onAction(PlayerAction.SeekEmergencyAid) }, enabled = state.player.actionPoints > 0) {
        Text(if (street) "Find emergency shelter · 1 AP" else "Request one-time aid · 1 AP")
      }
    }
    if (SimulationEngine.canSettleOverdraft(state, country)) {
      OutlinedButton(onClick = { confirmSettlement = true }, enabled = state.player.actionPoints >= 2) { Text("Review overdraft settlement") }
    }
    if (street || shelter) {
      OutlinedButton(onClick = { onAction(PlayerAction.AskFamilyForShelter) }, enabled = state.player.actionPoints > 0) {
        Text("Ask family to take you in · 1 AP")
      }
    }
  }
}

@Composable
internal fun WellnessDialog(state: GameState, pack: EraPack, onAction: (PlayerAction) -> Unit, onClose: () -> Unit) {
  val p = state.player
  val country = pack.country(state.config.countryId)
  val allowed = p.actionPoints > 0 && getAgeYears(p.ageMonths) >= 6
  LifeServiceDialog("Wellness & habits", "Free activities, routines, and habit support", onClose) {
    ActionPointsLabel(p.actionPoints, p.maxActionPoints)
    if (getAgeYears(p.ageMonths) < 6) Text("Activities and routines open at age 6.")
    Wellness.entries.forEach { activity ->
      OutlinedButton(
        onClick = { onAction(PlayerAction.PracticeWellness(activity)) },
        enabled = allowed,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("${activity.label} · 1 AP\n${activity.benefit}") }
    }
    val knownHabits = remember(pack) { pack.conditions.habits.associateBy { it.id } }
    p.habits.forEach { active ->
      val habit = knownHabits[active.id]
      CardBox {
        Text(habit?.name ?: active.id, style = MaterialTheme.typography.titleSmall)
        Text("Intensity ${active.strength.roundToInt()} / 100", color = ChronicleColors.TextSecondary)
        if (habit?.polarity == "negative") {
          TextButton(onClick = { onAction(PlayerAction.HabitSupport(active.id)) }, enabled = p.actionPoints > 0) {
            Text("Get support · 1 AP")
          }
        }
        TextButton(
          onClick = { onAction(PlayerAction.QuitHabit(active.id)) },
          enabled = p.actionPoints > 0 && (habit?.polarity != "negative" || active.strength <= 55),
        ) { Text(if (habit?.polarity == "negative") "Quit · 1 AP" else "Stop routine · 1 AP") }
      }
    }
    SectionTitle("Start a routine", "Costs are included in your regular bills")
    pack.conditions.habits.filter { habit -> habit.polarity == "positive" && p.habits.none { it.id == habit.id } }.forEach { habit ->
      val cost = (habit.annualCost * country.costOfLivingMultiplier).roundToInt().toLong()
      val costLabel = if (cost == 0L) "Free" else "${formatExactMoney(cost, country)}/yr"
      OutlinedButton(
        onClick = { onAction(PlayerAction.AdoptHabit(habit.id)) },
        enabled = allowed,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("${habit.name} · $costLabel · 1 AP") }
    }
  }
}

@Composable
internal fun JobInterviewDialog(
  track: CareerTrackDef, tier: CareerTierDef, player: PlayerState, onAction: (PlayerAction) -> Unit, onClose: () -> Unit,
) {
  LifeServiceDialog("Interview", tier.title, onClose) {
    ActionPointsLabel(player.actionPoints, player.maxActionPoints)
    val prompt = when {
      track.isPartTime -> "We need someone who can reliably cover shifts. What would you tell the hiring manager?"
      tier.minSkills.isNotEmpty() -> "This role calls for specialist skills. How would you show them what you can do?"
      else -> "We work closely across teams. How would you show us your strengths?"
    }
    Text(prompt)
    player.job?.let {
      Text("If hired, you’ll leave ${it.title} and start with fresh role progress. A rejection leaves your current job intact.", color = ChronicleColors.Amber)
    }
    InterviewAnswer.entries.forEach { answer ->
      OutlinedButton(
        onClick = { onClose(); onAction(PlayerAction.ApplyJob(track.id, tier.id, answer)) },
        enabled = player.actionPoints > 0,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("${answer.label} · 1 AP\n${answer.hint}") }
    }
  }
}
