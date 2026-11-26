package com.chronicle.lifesim.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.R
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt

@Composable
fun ActiveGameShell(
  vm: ChronicleViewModel,
  state: GameState,
  onOpenDirector: () -> Unit,
  onOpenSaves: () -> Unit,
) {
  val pack = vm.pack
  val activeTab = vm.activeTab
  val statDeltas = vm.statDeltas
  val onSelectTab: (ActiveTab) -> Unit = vm::selectTab
  val onAdvanceTurn: () -> Unit = vm::advanceTurn
  val onPerformAction: (PlayerAction) -> Unit = vm::performAction
  val onUpdateSchedule: (WeeklySchedule) -> Unit = vm::updateSchedule
  val onApplyPreset: (SchedulePresetId) -> Unit = vm::applyPreset
  val onSetFocusSkill: (SkillId) -> Unit = vm::setFocusSkill
  var showFullStats by remember { mutableStateOf(false) }
  var showActivities by remember { mutableStateOf(false) }
  var showDating by remember(state.seed) { mutableStateOf(false) }
  val tabState = rememberSaveableStateHolder()

  if (showFullStats) {
    FullStatsDialog(
      state = state,
      pack = pack,
      statDeltas = statDeltas,
      onDismiss = { showFullStats = false },
    )
  }

  if (showActivities) {
    ActivitiesDialog(
      state = state,
      pack = pack,
      onPerformAction = onPerformAction,
      onDismiss = { showActivities = false },
      onOpenDating = { showActivities = false; showDating = true },
    )
  }

  if (showDating) {
    DatingDialog(state, pack.country(state.config.countryId), vm.isBusy, onPerformAction) { showDating = false }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    TopHudBar(
      state = state,
      pack = pack,
      onOpenDirector = onOpenDirector,
      onOpenSaves = onOpenSaves,
      onOpenStats = { showFullStats = true },
    )

    if (activeTab == ActiveTab.LIFE) StatTray(
      state = state,
      statDeltas = statDeltas,
      onClick = { showFullStats = true },
    )

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
    ) {
      tabState.SaveableStateProvider("${state.seed}:${activeTab.name}") {
        when (activeTab) {
          ActiveTab.LIFE -> LifeJournalTab(
            state = state,
            pack = pack,
            onSelectTab = onSelectTab,
            onPerformAction = onPerformAction,
            onOpenActivities = { showActivities = true },
          )
          ActiveTab.CAREER -> CareerTab(state, pack, onPerformAction)
          ActiveTab.ASSETS -> AssetsTab(state, pack, onPerformAction)
          ActiveTab.PEOPLE -> PeopleTab(state, pack, onPerformAction) { showDating = true }
          ActiveTab.SCHEDULE -> ScheduleTab(state, pack, onUpdateSchedule, onApplyPreset, onSetFocusSkill, onPerformAction, vm.isBusy)
        }
      }
    }

    ActionDock(
      state = state,
      activeTab = activeTab,
      onSelectTab = onSelectTab,
      onAdvanceTurn = onAdvanceTurn,
      isBusy = vm.isBusy,
      ageInputLocked = vm.ageInputLocked,
      onBlockedAgeTap = vm::noteBlockedAgeTap,
    )
  }
}

@Composable
private fun TopHudBar(
  state: GameState,
  pack: EraPack,
  onOpenDirector: () -> Unit,
  onOpenSaves: () -> Unit,
  onOpenStats: () -> Unit,
) {
  val p = state.player
  val country = pack.country(state.config.countryId)
  val initials = "${p.firstName.firstOrNull() ?: '?'}${p.lastName.firstOrNull() ?: '?'}".uppercase()
  val role = p.job?.title ?: when {
    p.retired -> "Retired"
    p.activeClub != null -> p.activeClub.name
    p.education.currentProgramId != null ->
      pack.education.firstOrNull { it.id == p.education.currentProgramId }?.name ?: "Student"
    else -> country.name
  }
  Column(
    Modifier.fillMaxWidth().background(ChronicleColors.BgApp),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        onClick = onOpenStats,
        shape = RoundedCornerShape(9.dp),
        color = ChronicleColors.Cover,
        modifier = Modifier.size(44.dp).semantics { contentDescription = "Character stats" },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(initials, color = ChronicleColors.OnCover, fontFamily = ChronicleColors.Display, fontSize = 17.sp)
        }
      }
      Column(Modifier.weight(1f)) {
        Text(
          "${p.firstName} ${p.lastName}", color = ChronicleColors.TextPrimary,
          fontFamily = ChronicleColors.Display, fontSize = 21.sp, lineHeight = 24.sp,
          maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${country.flag} ${formatAge(p.ageMonths)} · $role",
          color = ChronicleColors.TextMuted, fontSize = 12.sp,
          maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
      }
      IconBtn(Icons.Outlined.Tune, "Director Mode", ChronicleColors.Emerald, onOpenDirector)
      IconBtn(Icons.Outlined.Bookmarks, "Save Slots", ChronicleColors.Emerald, onOpenSaves)
    }
    HorizontalDivider(color = ChronicleColors.BorderSubtle)
  }
}

@Composable
fun IconBtn(
  icon: ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
) {
  // UX-04: Expose exactly one merged semantic node with no duplicate child description
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = ChronicleColors.BgElevated,
    border = BorderStroke(1.dp, ChronicleColors.BorderSubtle),
    modifier = Modifier
      .size(44.dp)
      .semantics(mergeDescendants = true) {
        contentDescription = label
      },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun StatTray(
  state: GameState,
  statDeltas: Map<String, Int>,
  onClick: () -> Unit,
) {
  val s = state.player.stats
  Surface(
    onClick = onClick,
    color = ChronicleColors.BgInput,
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {
        contentDescription = "Vitals summary. Tap for detailed stats and genetics."
      },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StatMiniBar("Health", s.physicalHealth.roundToInt(), statDeltas["physicalHealth"], colorForVital(s.physicalHealth, inverse = false), Modifier.weight(1f))
      StatMiniBar("Happy", s.happiness.roundToInt(), statDeltas["happiness"], colorForVital(s.happiness, inverse = false), Modifier.weight(1f))
      StatMiniBar("Energy", s.energy.roundToInt(), statDeltas["energy"], colorForVital(s.energy, inverse = false), Modifier.weight(1f))
      StatMiniBar("Stress", s.stress.roundToInt(), statDeltas["stress"], colorForVital(s.stress, inverse = true), Modifier.weight(1f))
      Icon(
        Icons.Outlined.UnfoldMore,
        contentDescription = null,
        tint = ChronicleColors.TextMuted,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
fun StatMiniBar(
  label: String,
  value: Int,
  delta: Int?,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (label == "Health") {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = ChronicleColors.Rose, modifier = Modifier.size(12.dp))
      }
      Text(
        text = label,
        color = ChronicleColors.TextMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("$value%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      if (delta != null && delta != 0) {
        val improved = if (label == "Stress") delta < 0 else delta > 0
        Text(
          if (delta > 0) "+$delta" else "$delta",
          color = if (improved) ChronicleColors.Emerald else ChronicleColors.Rose,
          fontSize = 9.5.sp, maxLines = 1,
        )
      }
    }
  }
}

private fun colorForVital(valNum: Double, inverse: Boolean): Color {
  return if (inverse) {
    when {
      valNum >= 75.0 -> ChronicleColors.Rose
      valNum >= 45.0 -> ChronicleColors.Amber
      else -> ChronicleColors.TextPrimary
    }
  } else {
    when {
      valNum <= 25.0 -> ChronicleColors.Rose
      valNum <= 45.0 -> ChronicleColors.Amber
      else -> ChronicleColors.Emerald
    }
  }
}

@Composable
fun LifeJournalTab(
  state: GameState,
  pack: EraPack,
  onSelectTab: (ActiveTab) -> Unit,
  onPerformAction: (PlayerAction) -> Unit,
  onOpenActivities: () -> Unit,
) {
  val country = pack.country(state.config.countryId)
  val spotlight = state.timeline.firstOrNull()
  val groupedByAge = remember(state.timeline) { state.timeline.drop(1).groupBy { getAgeYears(it.ageMonths) } }

  val cash = state.player.finance.cash
  val cashflow = remember(state, pack) { SimulationEngine.estimateNextTurnCashflow(state, pack) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "journal_title") {
      JournalPageTitle("Life journal", "Age ${getAgeYears(state.player.ageMonths)} · ${state.world.currentYear} · ${formatMoney(cash, country)}")
    }
    item(key = "journal_art") { ChapterArt(R.drawable.chapter_life) }
    // Active Plan / Stage Activities card directly in first viewport
    item(key = "active_plan_card") {
      ActivePlanCard(
        state = state,
        pack = pack,
        onPerformAction = onPerformAction,
        onOpenActivities = onOpenActivities,
      )
    }
    // Next-turn financial caution or quick forecast cue when cashflow is strained
    if (cash < 0L || (getAgeYears(state.player.ageMonths) >= 18 && cashflow.projectedCash < 0L)) {
      item(key = "cashflow_notice") {
        val inDebt = cash < 0L
        Surface(
          onClick = { onSelectTab(ActiveTab.ASSETS) },
          shape = RoundedCornerShape(12.dp),
          color = if (inDebt) ChronicleColors.Rose.copy(alpha = 0.14f) else ChronicleColors.Amber.copy(alpha = 0.12f),
          border = BorderStroke(1.dp, if (inDebt) ChronicleColors.Rose.copy(alpha = 0.45f) else ChronicleColors.Amber.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = if (inDebt) ChronicleColors.Rose else ChronicleColors.Amber,
              modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (inDebt) "Negative Cash: ${formatMoney(cash, country)}" else "Cashflow Tight Next Turn",
                color = ChronicleColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Projected cash: ${formatMoney(cashflow.projectedCash, country)}. Tap to review Assets or housing.",
                color = ChronicleColors.TextSecondary,
                fontSize = 12.sp,
              )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ChronicleColors.TextMuted)
          }
        }
      }
    }

    // Latest Event Spotlight Card (Design Review visual brief)
    if (spotlight != null) {
      item(key = "spotlight_${spotlight.id}") {
        val (choiceLine, outcomeLine) = splitOutcomeDetail(spotlight.detail)
        val (catIcon, catLabel, catTint) = categoryMeta(spotlight.category)
        CardBox(
          bgColor = ChronicleColors.BgCard,
          borderColor = ChronicleColors.BorderStrong,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(catIcon, contentDescription = null, tint = catTint, modifier = Modifier.size(14.dp))
              Text(
                text = catLabel,
                color = catTint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
              )
            }
            Text(
              text = "${formatAge(spotlight.ageMonths)} · ${spotlight.year}",
              color = ChronicleColors.TextMuted,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = spotlight.title,
            color = ChronicleColors.TextPrimary,
            fontFamily = ChronicleColors.Display,
            fontSize = 25.sp,
            lineHeight = 29.sp,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = choiceLine,
            color = ChronicleColors.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
          )
          if (outcomeLine != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = ChronicleColors.BgElevated,
              border = BorderStroke(1.dp, ChronicleColors.BorderSubtle),
            ) {
              Text(
                text = outcomeLine,
                color = ChronicleColors.Emerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }
    }

    // Chronological Age Chapters
    groupedByAge.forEach { (ageYears, entries) ->
      item(key = "chapter_$ageYears", contentType = "chapter") {
        val chapterYear = entries.firstOrNull()?.year ?: state.world.currentYear
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Age $ageYears · $chapterYear",
            color = ChronicleColors.TextPrimary,
            fontFamily = ChronicleColors.Display,
            fontSize = 18.sp,
          )
          HorizontalDivider(
            color = ChronicleColors.BorderSubtle,
            modifier = Modifier.weight(1f),
          )
        }
      }

      items(entries, key = { it.id }, contentType = { "journal_entry" }) { entry ->
        val (icon, _, tint) = categoryMeta(entry.category)
        val (mainText, outcomeText) = splitOutcomeDetail(entry.detail)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
              .padding(top = 2.dp)
              .size(16.dp),
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = entry.title,
                color = ChronicleColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
              )
              Text(
                text = formatAge(entry.ageMonths),
                color = ChronicleColors.TextMuted,
                fontSize = 11.sp,
              )
            }
            Text(
              text = mainText,
              color = ChronicleColors.TextSecondary,
              fontSize = 12.5.sp,
              lineHeight = 18.sp,
            )
            if (outcomeText != null) {
              Text(
                text = outcomeText,
                color = ChronicleColors.Sky,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }
  }
}

private fun splitOutcomeDetail(detail: String): Pair<String, String?> {
  val idx = detail.indexOf(" -> ")
  return if (idx >= 0) {
    detail.substring(0, idx) to detail.substring(idx + 4)
  } else {
    detail to null
  }
}

private fun categoryMeta(category: String): Triple<ImageVector, String, Color> = when (category.lowercase()) {
  "milestone" -> Triple(Icons.Outlined.Flag, "MILESTONE", ChronicleColors.Gold)
  "career" -> Triple(Icons.Outlined.WorkOutline, "CAREER", ChronicleColors.Sky)
  "finance" -> Triple(Icons.Outlined.AccountBalanceWallet, "FINANCE", ChronicleColors.Emerald)
  "health" -> Triple(Icons.Outlined.FavoriteBorder, "HEALTH", ChronicleColors.Rose)
  "relationship" -> Triple(Icons.Outlined.PeopleOutline, "RELATIONSHIP", ChronicleColors.Pink)
  "chaos" -> Triple(Icons.Outlined.Bolt, "CHAOS", ChronicleColors.Amber)
  "world" -> Triple(Icons.Outlined.Public, "WORLD", ChronicleColors.Violet)
  "director" -> Triple(Icons.Outlined.Tune, "DIRECTOR", ChronicleColors.Amber)
  "death" -> Triple(Icons.Outlined.HourglassBottom, "OBITUARY", ChronicleColors.Rose)
  else -> Triple(Icons.Outlined.AutoStories, "LIFE EVENT", ChronicleColors.Emerald)
}

@Composable
fun ActionDock(
  state: GameState,
  activeTab: ActiveTab,
  onSelectTab: (ActiveTab) -> Unit,
  onAdvanceTurn: () -> Unit,
  isBusy: Boolean,
  ageInputLocked: Boolean,
  onBlockedAgeTap: () -> Unit,
) {
  val hasPending = state.pendingEvent != null
  val canAge = !hasPending && !isBusy && !ageInputLocked
  Surface(
    color = ChronicleColors.BgCard,
    border = BorderStroke(1.dp, ChronicleColors.BorderSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(0.8f).padding(start = 6.dp)) {
          val month = java.time.Month.of(state.world.currentMonth.coerceIn(1, 12))
            .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
          Text(
            "$month ${state.world.currentYear}",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = ChronicleColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
          )
          ActionPointsLabel(state.player.actionPoints, state.player.maxActionPoints)
        }
        Box(Modifier.weight(1.2f)) {
          Button(
            onClick = onAdvanceTurn,
            enabled = canAge,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = ChronicleColors.Emerald,
              contentColor = ChronicleColors.OnAccent,
              disabledContainerColor = ChronicleColors.BgElevated,
              disabledContentColor = ChronicleColors.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
          ) {
            Text(
              when {
                hasPending -> "Resolve decision"
                isBusy -> "Saving…"
                ageInputLocked -> "Turn saved"
                else -> formatAgeActionLabel(state.config.granularity)
              },
              fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
          }
          if (ageInputLocked && !hasPending) {
            // Keep listening during the lock so a continuous tap burst cannot start a second turn.
            Box(Modifier.matchParentSize().pointerInput(onBlockedAgeTap) {
              detectTapGestures(onPress = { onBlockedAgeTap() })
            })
          }
        }
      }
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DockButton(
          label = "Life",
          icon = Icons.Outlined.AutoStories,
          active = activeTab == ActiveTab.LIFE,
          onClick = { onSelectTab(ActiveTab.LIFE) },
          modifier = Modifier.weight(1f),
        )
        DockButton(
          label = "Career",
          icon = Icons.Outlined.WorkOutline,
          active = activeTab == ActiveTab.CAREER,
          onClick = { onSelectTab(ActiveTab.CAREER) },
          modifier = Modifier.weight(1f),
        )
        DockButton(
          label = "Assets",
          icon = Icons.Outlined.AccountBalanceWallet,
          active = activeTab == ActiveTab.ASSETS,
          onClick = { onSelectTab(ActiveTab.ASSETS) },
          modifier = Modifier.weight(1f),
        )
        DockButton(
          label = "People",
          icon = Icons.Outlined.PeopleOutline,
          active = activeTab == ActiveTab.PEOPLE,
          onClick = { onSelectTab(ActiveTab.PEOPLE) },
          modifier = Modifier.weight(1f),
        )
        DockButton(
          label = "Schedule",
          icon = Icons.Outlined.CalendarMonth,
          active = activeTab == ActiveTab.SCHEDULE,
          onClick = { onSelectTab(ActiveTab.SCHEDULE) },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
fun DockButton(
  label: String,
  icon: ImageVector,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (active) ChronicleColors.BgElevated else Color.Transparent,
    border = null,
    modifier = modifier
      .heightIn(min = 46.dp)
      .semantics(mergeDescendants = true) {
        this.selected = active
        this.role = Role.Tab
        contentDescription = label
      },
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) ChronicleColors.Emerald else ChronicleColors.TextSecondary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = label,
        color = if (active) ChronicleColors.TextPrimary else ChronicleColors.TextSecondary,
        fontSize = 10.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
fun FullStatsDialog(
  state: GameState,
  pack: EraPack,
  statDeltas: Map<String, Int>,
  onDismiss: () -> Unit,
) {
  val p = state.player
  val s = p.stats
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
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
        Text("Character Vitals & Genetics", color = ChronicleColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "Close vitals", tint = ChronicleColors.TextSecondary)
        }
      }
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("CORE ATTRIBUTES", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        listOf(
          Triple("Physical Health", s.physicalHealth.roundToInt(), statDeltas["physicalHealth"]),
          Triple("Mental Health", s.mentalHealth.roundToInt(), statDeltas["mentalHealth"]),
          Triple("Happiness", s.happiness.roundToInt(), statDeltas["happiness"]),
          Triple("Energy", s.energy.roundToInt(), statDeltas["energy"]),
          Triple("Stress", s.stress.roundToInt(), statDeltas["stress"]),
          Triple("Intelligence", s.intelligence.roundToInt(), statDeltas["intelligence"]),
          Triple("Charisma", s.charisma.roundToInt(), statDeltas["charisma"]),
          Triple("Looks", s.looks.roundToInt(), statDeltas["looks"]),
          Triple("Discipline", s.discipline.roundToInt(), statDeltas["discipline"]),
        ).forEach { (name, valInt, delta) ->
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (name == "Physical Health") {
                  Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = ChronicleColors.Rose, modifier = Modifier.size(15.dp))
                }
                Text(name, color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (delta != null && delta != 0) {
                  Text(
                    if (delta > 0) "(+$delta)" else "($delta)",
                    color = if (name == "Stress") (if (delta > 0) ChronicleColors.Rose else ChronicleColors.Emerald)
                    else (if (delta > 0) ChronicleColors.Emerald else ChronicleColors.Rose),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text("$valInt%", color = ChronicleColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
              }
            }
            LinearProgressIndicator(
              progress = { (valInt / 100f).coerceIn(0f, 1f) },
              color = if (name == "Stress") ChronicleColors.Amber else ChronicleColors.Emerald,
              trackColor = ChronicleColors.BgElevated,
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
          }
        }

        HorizontalDivider(color = ChronicleColors.BorderSubtle)
        Text("HIDDEN RESILIENCE & DNA", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MetricPill("Burnout", "${p.hidden.burnout.roundToInt()}%", Modifier.weight(1f))
          MetricPill("Sleep Debt", "${p.hidden.sleepDebt.roundToInt()}%", Modifier.weight(1f))
          MetricPill("Longevity", "${p.dna.longevity}", Modifier.weight(1f))
        }

        if (p.conditions.isNotEmpty()) {
          HorizontalDivider(color = ChronicleColors.BorderSubtle)
          Text("ACTIVE CONDITIONS", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          p.conditions.forEach { c ->
            val name = pack.conditions.conditions.firstOrNull { it.id == c.id }?.name ?: c.id
            Text(
              text = "• $name (${if (c.treated) "Treated" else "Untreated"})",
              color = if (c.treated) ChronicleColors.Sky else ChronicleColors.Rose,
              fontSize = 12.5.sp,
            )
          }
        }
      }
    }
  }
}

@Composable
fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(ChronicleColors.BgElevated)
      .border(1.dp, ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(label, color = ChronicleColors.TextMuted, fontSize = 10.5.sp, maxLines = 1)
    Text(value, color = ChronicleColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}
