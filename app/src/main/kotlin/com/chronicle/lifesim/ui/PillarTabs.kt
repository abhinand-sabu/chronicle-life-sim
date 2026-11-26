package com.chronicle.lifesim.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.R
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ==========================================
// 1. CAREER & EDUCATION TAB
// ==========================================
@Composable
fun CareerTab(
  state: GameState,
  pack: EraPack,
  onPerformAction: (PlayerAction) -> Unit,
) {
  val p = state.player
  val country = pack.country(state.config.countryId)
  val ageYears = getAgeYears(p.ageMonths)
  var confirmQuit by remember { mutableStateOf(false) }
  var showJobBoard by rememberSaveable(p.job == null) { mutableStateOf(p.job == null) }
  var showEduCatalog by rememberSaveable { mutableStateOf(false) }
  val openings = remember(state, pack) {
    if (ageYears < 14) emptyList()
    else pack.careers.flatMap { track ->
      track.tiers.map { tier ->
        val (eligible, reason) = SimulationEngine.canApplyForJob(state, track, tier)
        CareerOpening(track, tier, eligible, reason)
      }
    }.sortedWith(compareByDescending<CareerOpening> { it.eligible }
      .thenBy { it.tier.minAge }
      .thenByDescending { it.tier.baseAnnualSalary })
  }
  val partTime = openings.filter { it.track.isPartTime }
  val fullTime = openings.filterNot { it.track.isPartTime }


  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "career_page") {
      JournalPageTitle(
        if (ageYears < 14) "School" else "Career & education",
        p.job?.title ?: if (ageYears < 14) "Learning, clubs and skills" else "Work, qualifications and skills",
        ChronicleColors.Sky,
      )
    }
    item(key = "career_art") { ChapterArt(R.drawable.chapter_career) }
    if (partTime.isNotEmpty()) {
      item(key = "part_time_header") {
        SectionTitle("Part-time jobs", "1 AP per interview")
      }
      items(partTime, key = { "part_time_${it.track.id}_${it.tier.id}" }, contentType = { "job" }) { opening ->
        CareerOpeningRow(opening, p, country, onPerformAction)
      }
    }
    // Stage-aware CareerTab: School & After-School view under age 14 instead of locked adult jobs
    if (ageYears < 14) {
      item(key = "youth_school_progress") {
        val curProg = p.education.currentProgramId?.let { id -> pack.education.firstOrNull { it.id == id } }
        val club = p.activeClub
        val teammate = club?.teammateNpcId?.let { tid -> state.npcs.firstOrNull { it.id == tid && it.alive } }
        CardBox(borderColor = ChronicleColors.Sky.copy(alpha = 0.6f)) {
          Text("SCHOOL & AFTER-SCHOOL", color = ChronicleColors.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
          Spacer(Modifier.height(4.dp))
          Text(
            curProg?.name ?: if (ageYears < 5) "Pre-School Years" else "Between School Terms",
            color = ChronicleColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
          )
          if (curProg != null) {
            val totalM = curProg.durationYears * 12
            Spacer(Modifier.height(6.dp))
            Text("${p.education.monthsCompletedInProgram} / ${totalM}m completed · GPA ${"%.2f".format(p.education.gpa)}", color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { (p.education.monthsCompletedInProgram.toFloat() / max(1, totalM)).coerceIn(0f, 1f) },
              color = ChronicleColors.Sky,
              trackColor = ChronicleColors.BgElevated,
              modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            )
          }
          Spacer(Modifier.height(10.dp))
          HorizontalDivider(color = ChronicleColors.BorderSubtle)
          Spacer(Modifier.height(8.dp))
          Text(
            text = if (club != null) "Club: ${club.name} (${club.weeklyHours}h/wk)${if (teammate != null) " · with ${teammate.firstName}" else ""}"
            else "No club joined yet — open Activities on the Life tab to join one!",
            color = ChronicleColors.TextSecondary,
            fontSize = 12.5.sp,
          )
        }
      }
    } else {
      // Current Employment + Next Role Checklist
      item(key = "current_job") {
      val job = p.job
      if (job != null) {
        val track = pack.careers.firstOrNull { it.id == job.trackId }
        val curTier = track?.tiers?.firstOrNull { it.id == job.tierId }
        val nextTier = if (track != null && curTier != null) {
          track.tiers.firstOrNull { it.level == curTier.level + 1 }
        } else null

        CardBox(borderColor = ChronicleColors.BorderStrong) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text("CURRENT ROLE", color = ChronicleColors.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
              Text(job.title, color = ChronicleColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
              Text(
                "${track?.name ?: job.trackId} · ${formatMoney(job.annualSalary, country)}/yr",
                color = ChronicleColors.TextSecondary,
                fontSize = 12.5.sp,
              )
            }
            MetricPill("Tenure", "${"%.1f".format(job.monthsInTrack / 12.0)}y")
          }

          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Job Performance", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
            Text("${job.performance.roundToInt()}%", color = ChronicleColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { (job.performance / 100.0).toFloat().coerceIn(0f, 1f) },
            color = if (job.performance >= 65.0) ChronicleColors.Emerald else if (job.performance >= 35.0) ChronicleColors.Amber else ChronicleColors.Rose,
            trackColor = ChronicleColors.BgElevated,
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
          )

          // Next Role Checklist (Design Review)
          Spacer(Modifier.height(12.dp))
          HorizontalDivider(color = ChronicleColors.BorderSubtle)
          Spacer(Modifier.height(10.dp))
          if (nextTier != null) {
            val nextSal = (nextTier.baseAnnualSalary * country.salaryMultiplier).roundToLong()
            Text(
              "NEXT ROLE · ${nextTier.title} (${formatMoney(nextSal, country)}/yr)",
              color = ChronicleColors.TextPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            ChecklistRow(
              label = "Age ${nextTier.minAge}+",
              met = ageYears >= nextTier.minAge,
              detail = "Now ${ageYears}y",
            )
            if (nextTier.minYearsInTrack != null) {
              val haveY = job.monthsInTrack / 12.0
              ChecklistRow(
                label = "${nextTier.minYearsInTrack}y in track",
                met = haveY >= nextTier.minYearsInTrack,
                detail = "${"%.1f".format(haveY)}y / ${nextTier.minYearsInTrack}y",
              )
            }
            nextTier.minSkills.forEach { (sk, req) ->
              val haveSk = p.skills[sk] ?: 0.0
              ChecklistRow(
                label = sk.name.replace('_', ' ').replaceFirstChar { it.uppercase() },
                met = haveSk >= req,
                detail = "${haveSk.roundToInt()} / ${req.toInt()}",
              )
            }
            ChecklistRow(
              label = "Strong performance (65%+ recommended)",
              met = job.performance >= 65.0,
              detail = "${job.performance.roundToInt()}%",
            )
          } else {
            Text(
              "TOP OF TRACK · Eligible for 8% merit raise reviews",
              color = ChronicleColors.Gold,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
          }

          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.RequestPromotion) },
              enabled = p.actionPoints >= 1,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, ChronicleColors.Sky.copy(alpha = 0.7f)),
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Text(
                text = if (nextTier != null) "Ask Promotion · 1 AP" else "Ask Raise · 1 AP",
                color = ChronicleColors.Sky,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
              )
            }
            if (ageYears >= country.legalAdultAge && !p.retired) {
              OutlinedButton(
                onClick = { onPerformAction(PlayerAction.Retire) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              ) {
                Text("Retire", color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
              }
            }
          }

          // 2-step Quit Confirmation
          Spacer(Modifier.height(6.dp))
          if (!confirmQuit) {
            TextButton(
              onClick = { confirmQuit = true },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) {
              Text("Quit current job...", color = ChronicleColors.TextMuted, fontSize = 12.sp)
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Resign without notice?", color = ChronicleColors.Rose, fontSize = 12.sp, modifier = Modifier.weight(1f))
              TextButton(onClick = { confirmQuit = false }) {
                Text("Cancel", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
              }
              Button(
                onClick = {
                  confirmQuit = false
                  onPerformAction(PlayerAction.QuitJob)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ChronicleColors.Rose),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
              ) {
                Text("Confirm Quit", color = ChronicleColors.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
        CardBox {
          Text("CAREER STATUS", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(4.dp))
          Text(
            text = when {
              p.retired -> "Officially Retired"
              ageYears < 14 -> "Too young for formal employment"
              else -> "Currently Unemployed"
            },
            color = ChronicleColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
          )
          if (p.retired) {
            Text(
              "Pension begins at age ${country.retirementAge} (${formatMoney(country.annualPensionBase, country)}/yr).",
              color = ChronicleColors.TextSecondary,
              fontSize = 12.5.sp,
            )
          }
        }
      }
    }
    }

    // Portfolio & Achievements card (shown at all ages when earned, or explains impact for teens/adults)
    if (p.portfolio.isNotEmpty() || ageYears >= 14) {
      item(key = "portfolio_card") {
        val bonusPct = minOf(15, p.portfolio.size * 5)
        CardBox {
          SectionTitle(
            "PORTFOLIO & ACHIEVEMENTS",
            if (p.portfolio.isEmpty()) "Win club showcases or complete projects for up to +15% hiring odds"
            else "${p.portfolio.size} item(s) · +${bonusPct}% hiring & promotion bonus",
          )
          if (p.portfolio.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              p.portfolio.forEach { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(ChronicleColors.BgInput)
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(item.title, color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(item.impactLabel, color = ChronicleColors.Emerald, fontSize = 11.5.sp)
                  }
                  Text(formatAge(item.ageMonths), color = ChronicleColors.TextMuted, fontSize = 11.5.sp)
                }
              }
            }
          }
        }
      }
    }

    // Active Education & Progress (only shown for >= 14 since < 14 has dedicated School card above)
    if (ageYears >= 14) {
    item(key = "education") {
      val curProg = p.education.currentProgramId?.let { id -> pack.education.firstOrNull { it.id == id } }
      CardBox {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionTitle(
            "EDUCATION",
            if (curProg != null) "${curProg.name} · GPA ${"%.2f".format(p.education.gpa)}"
            else "${p.education.completedProgramIds.size} program(s) completed",
          )
          TextButton(onClick = { showEduCatalog = !showEduCatalog }) {
            Text(if (showEduCatalog) "Hide" else "Browse", color = ChronicleColors.Sky, fontSize = 12.sp)
          }
        }

        if (curProg != null) {
          val totalMonths = curProg.durationYears * 12
          val doneMonths = p.education.monthsCompletedInProgram
          val remMonths = max(0, totalMonths - doneMonths)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$doneMonths / ${totalMonths}m completed", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
            Text("${remMonths}m left", color = ChronicleColors.Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { (doneMonths.toFloat() / max(1, totalMonths)).coerceIn(0f, 1f) },
            color = ChronicleColors.Sky,
            trackColor = ChronicleColors.BgElevated,
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
          )
        }
        if (p.education.studentLoanBalance > 0L) {
          Spacer(Modifier.height(6.dp))
          Text(
            "Student Loans: ${formatMoney(p.education.studentLoanBalance, country)}",
            color = ChronicleColors.Amber,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }

        AnimatedVisibility(visible = showEduCatalog) {
          Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            pack.education.forEach { prog ->
              val (ok, reason) = SimulationEngine.canEnrollInProgram(state, prog)
              val completed = p.education.completedProgramIds.contains(prog.id)
              val tuition = (prog.annualTuition * country.tuitionMultiplier).roundToLong()
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(ChronicleColors.BgInput)
                  .border(1.dp, ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(prog.name, color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  Text(
                    "${prog.durationYears}y · ${if (tuition == 0L) "Free" else "${formatMoney(tuition, country)}/yr"}",
                    color = ChronicleColors.TextSecondary,
                    fontSize = 11.5.sp,
                  )
                  if (!completed && reason != null) {
                    Text(reason, color = ChronicleColors.TextMuted, fontSize = 11.sp)
                  }
                }
                if (completed) {
                  Text("Done", color = ChronicleColors.Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                  OutlinedButton(
                    onClick = { onPerformAction(PlayerAction.EnrollEducation(prog.id)) },
                    enabled = ok && p.actionPoints >= 1,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  ) {
                    Text("Enroll · 1 AP", fontSize = 11.5.sp)
                  }
                }
              }
            }
          }
        }
      }
    }
    }

    if (ageYears >= 14) {
      item(key = "full_time_header") {
        CardBox {
          Row(
            modifier = Modifier.fillMaxWidth().clickable { showJobBoard = !showJobBoard },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              SectionTitle("Full-time jobs", "${fullTime.count { it.eligible }} eligible openings · 1 AP per interview")
            }
            Icon(
              if (showJobBoard) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription = if (showJobBoard) "Collapse full-time jobs" else "Expand full-time jobs",
              tint = ChronicleColors.TextSecondary,
            )
          }
        }
      }
      if (showJobBoard) {
        items(fullTime, key = { "job_${it.track.id}_${it.tier.id}" }, contentType = { "job" }) { opening ->
          CareerOpeningRow(opening, p, country, onPerformAction)
        }
      }
    }
  }
}

private data class CareerOpening(
  val track: CareerTrackDef,
  val tier: CareerTierDef,
  val eligible: Boolean,
  val reason: String?,
)

@Composable
private fun CareerOpeningRow(
  opening: CareerOpening,
  player: PlayerState,
  country: CountryDef,
  onAction: (PlayerAction) -> Unit,
) {
  val (track, tier, eligible, reason) = opening
  val isCurrent = player.job?.trackId == track.id && player.job.tierId == tier.id
  var confirmSwitch by remember(player.job, track.id, tier.id) { mutableStateOf(false) }
  val salary = (tier.baseAnnualSalary * country.salaryMultiplier).roundToLong()
  if (confirmSwitch) {
    JobInterviewDialog(track, tier, player, onAction) { confirmSwitch = false }
  }
  CardBox {
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(tier.title, color = ChronicleColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
          "${formatMoney(salary, country)}/yr · ${tier.weeklyHoursRequired}h/wk",
          color = ChronicleColors.TextSecondary, fontSize = 12.sp,
        )
        if (reason != null && !isCurrent) {
          Text(reason, color = ChronicleColors.Amber, fontSize = 12.sp)
        }
      }
      if (isCurrent) {
        Text("Current", color = ChronicleColors.Emerald, fontSize = 12.sp)
      } else {
        OutlinedButton(
          onClick = { confirmSwitch = true },
          enabled = eligible && player.actionPoints >= 1,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text("Interview", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun ChecklistRow(label: String, met: Boolean, detail: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      Icon(
        imageVector = if (met) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (met) ChronicleColors.Emerald else ChronicleColors.TextMuted,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = label,
        color = if (met) ChronicleColors.TextPrimary else ChronicleColors.TextSecondary,
        fontSize = 12.sp,
      )
    }
    Text(
      text = detail,
      color = if (met) ChronicleColors.Emerald else ChronicleColors.Amber,
      fontSize = 11.5.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

// ==========================================
// 2. ASSETS & FINANCES TAB
// ==========================================
@Composable
fun AssetsTab(
  state: GameState,
  pack: EraPack,
  onPerformAction: (PlayerAction) -> Unit,
) {
  val p = state.player
  val fin = p.finance
  val country = pack.country(state.config.countryId)
  val netWorth = remember(state, pack) { SimulationEngine.calculateNetWorth(state, pack) }
  val cashflow = remember(state, pack) { SimulationEngine.estimateNextTurnCashflow(state, pack) }
  var openMarket by remember { mutableStateOf(false) }
  var openLoans by remember { mutableStateOf(false) }
  if (openMarket) AssetMarketDialog(state, country, onPerformAction) { openMarket = false }
  if (openLoans) CreditDialog(state, country, onPerformAction) { openLoans = false }
  val adult = getAgeYears(p.ageMonths) >= country.legalAdultAge
  val visibleHomes = remember(pack) { pack.assets.housing.filterNot { it.id in setOf("unhoused", "emergency_shelter") } }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "assets_page") {
      JournalPageTitle("Finances", "Cash, investments and housing")
    }
    item(key = "assets_art") { ChapterArt(R.drawable.chapter_assets) }
    item(key = "asset_services") {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { openMarket = true }, modifier = Modifier.weight(1f)) { Text("Marketplace") }
        OutlinedButton(onClick = { openLoans = true }, modifier = Modifier.weight(1f)) { Text("Loans") }
      }
    }
    if (adult && (fin.cash < 0 || fin.housingId in setOf("unhoused", "emergency_shelter"))) {
      item(key = "housing_assistance") { HardshipCard(state, country, onPerformAction) }
    }
    // Lead with Cash on hand & Next-Turn Cashflow (Design Review)
    item(key = "cashflow_summary") {
      CardBox(borderColor = if (fin.cash < 0L) ChronicleColors.Rose else ChronicleColors.BorderStrong) {
        Text("CASH & NEXT-TURN CASHFLOW", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          Column {
            Text("Cash on Hand", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
            Text(
              formatExactMoney(fin.cash, country),
              color = if (fin.cash < 0L) ChronicleColors.Rose else ChronicleColors.TextPrimary,
              fontSize = 22.sp,
              fontWeight = FontWeight.ExtraBold,
            )
          }
          MetricPill("Net Worth", formatMoney(netWorth, country))
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = ChronicleColors.BorderSubtle)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Expected next income (after tax)", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
          Text("+${formatMoney(cashflow.income, country)}", color = ChronicleColors.Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Estimated bills, rent & debt", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
          Text("-${formatMoney(cashflow.cashExpenses, country)}", color = ChronicleColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Projected cash", color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(
            formatMoney(cashflow.projectedCash, country),
            color = if (cashflow.projectedCash >= 0L) ChronicleColors.Emerald else ChronicleColors.Rose,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
          )
        }
        if (cashflow.financedTuition > 0L) {
          Text(
            "Estimated tuition added to loans: ${formatMoney(cashflow.financedTuition, country)}",
            color = ChronicleColors.TextSecondary,
            fontSize = 12.sp,
          )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MetricPill("Credit Score", "${fin.creditScore}", Modifier.weight(1f))
          MetricPill("Mortgage", formatMoney(fin.mortgageBalance, country), Modifier.weight(1f))
          MetricPill("Loans", formatMoney(p.education.studentLoanBalance, country), Modifier.weight(1f))
        }
      }
    }

    item(key = "net_worth_history") {
      NetWorthHistoryCard(fin.netWorthHistory, p.ageMonths, netWorth, country)
    }

    item(key = "investments") {
      SectionTitle("INVESTMENTS", "Market returns follow macro cycles · 1 AP per trade")
    }
    items(fin.investments, key = { "holding_${it.assetId}" }, contentType = { "holding" }) { holding ->
      val def = pack.assets.investments.firstOrNull { it.id == holding.assetId }
      val gain = holding.balance - holding.costBasis
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ChronicleColors.BgElevated, RoundedCornerShape(10.dp))
          .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(def?.name ?: holding.assetId, color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(
            "${formatMoney(holding.balance, country)} (${if (gain >= 0) "+" else ""}${formatMoney(gain, country)})",
            color = if (gain >= 0) ChronicleColors.Emerald else ChronicleColors.Rose,
            fontSize = 12.sp,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { onPerformAction(PlayerAction.SellInvestment(holding.assetId, minOf(1000L, holding.balance))) },
            enabled = p.actionPoints >= 1 && holding.balance > 0L,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text("Sell", fontSize = 11.5.sp, color = ChronicleColors.Amber)
          }
          OutlinedButton(
            onClick = { onPerformAction(PlayerAction.SellInvestment(holding.assetId, holding.balance)) },
            enabled = p.actionPoints >= 1 && holding.balance > 0L,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text("All", fontSize = 11.5.sp, color = ChronicleColors.TextPrimary)
          }
        }
      }
    }
    items(pack.assets.investments, key = { "asset_${it.id}" }, contentType = { "investment" }) { asset ->
      val buyAmt = max(asset.minInvestment, 1000L)
      val unavailable = when {
        getAgeYears(p.ageMonths) < country.legalAdultAge -> "Available at age ${country.legalAdultAge}"
        fin.cash < buyAmt -> "Need ${formatMoney(buyAmt, country)} cash"
        p.actionPoints < 1 -> "Need 1 AP"
        else -> null
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(ChronicleColors.BgCard, RoundedCornerShape(10.dp))
          .border(1.dp, ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
          .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(asset.name, color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(
            "~${(asset.expectedAnnualReturn * 100).roundToInt()}%/yr · Min ${formatMoney(asset.minInvestment, country)}",
            color = ChronicleColors.TextSecondary,
            fontSize = 11.5.sp,
          )
          if (unavailable != null) {
            Text(unavailable, color = ChronicleColors.TextMuted, fontSize = 12.sp)
          }
        }
        OutlinedButton(
          onClick = { onPerformAction(PlayerAction.Invest(asset.id, buyAmt)) },
          enabled = unavailable == null,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text("Buy ${formatMoney(buyAmt, country)} · 1 AP", fontSize = 11.5.sp)
        }
      }
    }

    item(key = "housing") {
      val curHouse = pack.assets.housing.firstOrNull { it.id == fin.housingId }
      SectionTitle(
        "HOUSING",
        "${curHouse?.name ?: fin.housingId}${if (fin.ownsHousing) " · Owned" else ""}",
      )
    }
    items(visibleHomes, key = { "housing_${it.id}" }, contentType = { "property" }) { h ->
      val rent = (h.annualRent * country.costOfLivingMultiplier).roundToLong()
      val price = (h.purchasePrice * country.costOfLivingMultiplier).roundToLong()
      val down = (price * 0.2).roundToLong()
      val moveInCost = (h.annualRent * country.costOfLivingMultiplier / 12).roundToLong()
      val saleValue = if (fin.ownsHousing) {
        val house = pack.assets.housing.firstOrNull { it.id == fin.housingId }
        ((house?.purchasePrice ?: 0) * country.costOfLivingMultiplier).roundToLong() - fin.mortgageBalance
      } else 0L
      val canReturn = !adult || hasFamilyHousing(state.npcs)
      val availableCash = safeAddMoney(fin.cash, saleValue)
      val isHere = fin.housingId == h.id
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(ChronicleColors.BgCard, RoundedCornerShape(10.dp))
          .border(1.dp, if (isHere) ChronicleColors.Sky else ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
          .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(h.name, color = ChronicleColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
          if (isHere) Text(if (fin.ownsHousing) "Owned" else "Current", color = ChronicleColors.Sky, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(
          text = if (h.purchasePrice > 0) "Rent ${formatMoney(rent, country)}/yr · ${formatMoney(moveInCost, country)} to move in · Buy ${formatMoney(down, country)} down"
          else if (h.annualRent > 0) "Rent ${formatMoney(rent, country)}/yr · ${formatMoney(moveInCost, country)} to move in"
          else if (canReturn) "Free family housing" else "No family can house you right now",
          color = ChronicleColors.TextSecondary,
          fontSize = 11.5.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!isHere || fin.ownsHousing) {
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.ChangeHousing(h.id, buy = false)) },
              enabled = p.actionPoints >= 2 && if (h.id == "parents_home") canReturn else adult && availableCash >= moveInCost,
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(if (h.id == "parents_home") "Move Home · 2 AP" else "Rent · 2 AP", fontSize = 11.5.sp, color = ChronicleColors.TextPrimary)
            }
          }
          if (h.purchasePrice > 0 && !(isHere && fin.ownsHousing)) {
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.ChangeHousing(h.id, buy = true)) },
              enabled = p.actionPoints >= 2 && adult && availableCash >= down,
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text("Buy · 2 AP", fontSize = 11.5.sp, color = ChronicleColors.Sky)
            }
          }
        }
      }
    }
  }
}

// ==========================================
// 3. PEOPLE TAB (Compact cards + Detail sheet)
// ==========================================
@Composable
fun PeopleTab(
  state: GameState,
  pack: EraPack,
  onPerformAction: (PlayerAction) -> Unit,
  onOpenDating: () -> Unit,
) {
  val p = state.player
  val country = pack.country(state.config.countryId)
  var selectedNpcId by remember { mutableStateOf<String?>(null) }

  val livingNpcs = remember(state.npcs) {
    val priority = mapOf(
      NpcRole.spouse to 0,
      NpcRole.partner to 1,
      NpcRole.child to 2,
      NpcRole.parent to 3,
      NpcRole.sibling to 4,
      NpcRole.friend to 5,
    )
    state.npcs.filter { it.alive }.sortedBy { priority[it.role] ?: 6 }
  }
  val deceasedNpcs = remember(state.npcs) { state.npcs.filterNot { it.alive } }

  selectedNpcId?.let { id ->
    state.npcs.firstOrNull { it.id == id && it.alive }?.let { npc ->
      PersonDetailDialog(
        npc = npc,
        country = country,
        cash = p.finance.cash,
        playerAgeYears = getAgeYears(p.ageMonths),
        legalMarriageAge = country.legalMarriageAge,
        actionPoints = p.actionPoints,
        maxActionPoints = p.maxActionPoints,
        onAction = onPerformAction,
        onDismiss = { selectedNpcId = null },
      )
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item(key = "people_hdr") {
      JournalPageTitle("People", "${livingNpcs.size} living contacts", ChronicleColors.Pink)
    }

    item(key = "people_art") { ChapterArt(R.drawable.chapter_people) }
    if (getAgeYears(p.ageMonths) >= country.legalAdultAge &&
      livingNpcs.none { it.role == NpcRole.partner || it.role == NpcRole.spouse }
    ) {
      item(key = "dating") {
        CardBox(bgColor = ChronicleColors.Pink.copy(alpha = 0.10f)) {
          Text("Dating", color = ChronicleColors.TextPrimary, fontFamily = ChronicleColors.Display, fontSize = 21.sp)
          Text(if (p.dateProspect == null) "Meet someone new and see where it goes." else "${p.dateProspect.firstName} · ${p.courtshipDates.coerceAtMost(2)}/2 good dates", color = ChronicleColors.TextSecondary, fontSize = 13.sp)
          TextButton(onClick = onOpenDating) {
            Text(if (p.dateProspect == null) "Find a partner" else "View your match", color = ChronicleColors.Pink)
          }
        }
      }
    }

    items(livingNpcs, key = { it.id }, contentType = { "contact" }) { npc ->
      val primaryAction = when {
        npc.tension >= 45.0 -> NpcInteraction.apologize to "Apologize"
        (npc.role == NpcRole.partner || npc.role == NpcRole.spouse) && npc.affection >= 55.0 -> NpcInteraction.date to "Date"
        else -> NpcInteraction.talk to "Talk"
      }
      val statusText = when {
        npc.tension >= 60.0 -> "High tension — relationship strained"
        npc.affection >= 80.0 && npc.trust >= 70.0 -> "Deep bond · Warm & trusting"
        npc.affection >= 55.0 -> "Friendly terms (${npc.affection.roundToInt()}% bond)"
        else -> "Distant (${npc.affection.roundToInt()}% bond)"
      }
      val initials = "${npc.firstName.firstOrNull() ?: '?'}${npc.lastName.firstOrNull() ?: '?'}".uppercase()

      Surface(
        onClick = { selectedNpcId = npc.id },
        shape = RoundedCornerShape(14.dp),
        color = ChronicleColors.BgCard,
        border = BorderStroke(1.dp, if (npc.tension >= 50.0) ChronicleColors.Amber.copy(alpha = 0.5f) else ChronicleColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(ChronicleColors.BgElevated)
              .border(1.dp, ChronicleColors.BorderStrong, CircleShape),
          ) {
            Text(initials, color = ChronicleColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                "${npc.firstName} ${npc.lastName}",
                color = ChronicleColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              Text(
                "${npc.relationshipLabel()} · ${getAgeYears(npc.ageMonths)}y",
                color = ChronicleColors.TextMuted,
                fontSize = 11.5.sp,
              )
            }
            Text(
              statusText,
              color = if (npc.tension >= 60.0) ChronicleColors.Amber else ChronicleColors.TextSecondary,
              fontSize = 12.sp,
            )
            npc.memories.firstOrNull()?.let { latestMem ->
              Text(
                "Recent: ${latestMem.label}",
                color = ChronicleColors.TextMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          OutlinedButton(
            onClick = { onPerformAction(PlayerAction.InteractNpc(npc.id, primaryAction.first)) },
            enabled = p.actionPoints >= 1,
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(primaryAction.second, color = ChronicleColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    if (deceasedNpcs.isNotEmpty()) {
      item(key = "deceased_hdr") {
        CardBox {
          Text("IN MEMORY (${deceasedNpcs.size})", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(6.dp))
          deceasedNpcs.forEach { d ->
            Text(
              "• ${d.firstName} ${d.lastName} (${d.relationshipLabel()}, ${getAgeYears(d.ageMonths)}y)",
              color = ChronicleColors.TextMuted,
              fontSize = 12.sp,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PersonDetailDialog(
  npc: Npc,
  country: CountryDef,
  cash: Long,
  playerAgeYears: Int,
  legalMarriageAge: Int,
  actionPoints: Int,
  maxActionPoints: Int,
  onAction: (PlayerAction) -> Unit,
  onDismiss: () -> Unit,
) {
  val ageY = getAgeYears(npc.ageMonths)
  val isPartner = npc.role == NpcRole.partner || npc.role == NpcRole.spouse
  var showGifts by remember { mutableStateOf(false) }
  var showDates by remember { mutableStateOf(false) }
  if (showGifts) {
    LifeServiceDialog("Choose a gift", "For ${npc.firstName}", { showGifts = false }) {
      ActionPointsLabel(actionPoints, maxActionPoints)
      Gift.entries.forEach { gift ->
        val cost = gift.cost(country)
        OutlinedButton(
          onClick = { showGifts = false; onAction(PlayerAction.GiveGift(npc.id, gift)) },
          enabled = actionPoints > 0 && (cost == 0L || cash >= cost),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("${gift.label} · ${if (cost == 0L) "Free" else formatExactMoney(cost, country)} · 1 AP") }
      }
    }
  }
  if (showDates) {
    LifeServiceDialog("Plan time together", "With ${npc.firstName}", { showDates = false }) {
      ActionPointsLabel(actionPoints, maxActionPoints)
      FirstDatePlan.entries.forEach { plan ->
        val cost = SimulationEngine.firstDateCost(plan, country)
        OutlinedButton(
          onClick = { showDates = false; onAction(PlayerAction.RelationshipDate(npc.id, plan)) },
          enabled = actionPoints > 0 && (cost == 0L || cash >= cost),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("${plan.label()} · ${if (cost == 0L) "Free" else formatExactMoney(cost, country)} · 1 AP") }
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
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
        Column(modifier = Modifier.weight(1f)) {
          Text("${npc.firstName} ${npc.lastName}", color = ChronicleColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
          Text("${npc.relationshipLabel()} · Age ${ageY}y", color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
          ActionPointsLabel(actionPoints, maxActionPoints)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "Close person details", tint = ChronicleColors.TextSecondary)
        }
      }
      ChronicleNotice()
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MetricPill("Affection", "${npc.affection.roundToInt()}%", Modifier.weight(1f))
          MetricPill("Trust", "${npc.trust.roundToInt()}%", Modifier.weight(1f))
          MetricPill("Respect", "${npc.respect.roundToInt()}%", Modifier.weight(1f))
          MetricPill("Tension", "${npc.tension.roundToInt()}%", Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MetricPill("Warmth", "${npc.personality.warmth}", Modifier.weight(1f))
          MetricPill("Ambition", "${npc.personality.ambition}", Modifier.weight(1f))
          MetricPill("Loyalty", "${npc.personality.loyalty}", Modifier.weight(1f))
        }

        // Actions (1 AP each)
        Text("INTERACTIONS (1 AP)", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { onAction(PlayerAction.InteractNpc(npc.id, NpcInteraction.talk)) },
            enabled = actionPoints >= 1,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
          ) { Text("Talk", fontSize = 12.sp) }
          OutlinedButton(
            onClick = { showGifts = true },
            enabled = actionPoints >= 1,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
          ) { Text("Give a gift", fontSize = 12.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (isPartner) {
            OutlinedButton(
              onClick = { showDates = true },
              enabled = actionPoints >= 1,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(9.dp),
            ) { Text("Plan a date", fontSize = 12.sp) }
          }
          OutlinedButton(
            onClick = { onAction(PlayerAction.InteractNpc(npc.id, NpcInteraction.apologize)) },
            enabled = actionPoints >= 1,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
          ) { Text("Apologize", fontSize = 12.sp) }
          OutlinedButton(
            onClick = { onAction(PlayerAction.InteractNpc(npc.id, NpcInteraction.argue)) },
            enabled = actionPoints >= 1,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, ChronicleColors.Rose.copy(alpha = 0.5f)),
          ) { Text("Argue", color = ChronicleColors.Rose, fontSize = 12.sp) }
        }
        listOf(
          NpcInteraction.spend_time to "Spend time",
          NpcInteraction.offer_help to "Offer help",
          NpcInteraction.set_boundary to "Set a boundary",
        ).forEach { (interaction, label) ->
          OutlinedButton(
            onClick = { onAction(PlayerAction.InteractNpc(npc.id, interaction)) },
            enabled = actionPoints > 0 && (interaction != NpcInteraction.set_boundary || npc.tension >= 10),
            modifier = Modifier.fillMaxWidth(),
          ) { Text(label) }
        }
        if (isPartner) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (npc.role == NpcRole.partner && playerAgeYears >= legalMarriageAge && ageY >= legalMarriageAge) {
              OutlinedButton(
                onClick = { onAction(PlayerAction.InteractNpc(npc.id, NpcInteraction.propose)) },
                enabled = actionPoints >= 1,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, ChronicleColors.Emerald),
              ) { Text("Propose", color = ChronicleColors.Emerald, fontSize = 12.sp) }
            }
            OutlinedButton(
              onClick = { onAction(PlayerAction.InteractNpc(npc.id, NpcInteraction.breakup)) },
              enabled = actionPoints >= 1,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(9.dp),
            ) { Text(if (npc.role == NpcRole.spouse) "Separate" else "Break up", color = ChronicleColors.TextMuted, fontSize = 12.sp) }
          }
        }

        if (npc.role == NpcRole.child && ageY < 18) {
          Text("PARENTING STYLE (0 AP)", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          ParentingStyle.entries.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              pair.forEach { style ->
                ChoiceChipButton(
                  label = style.name.replaceFirstChar { it.uppercase() },
                  selected = npc.parentingStyle == style,
                  onClick = { onAction(PlayerAction.SetParentingStyle(npc.id, style)) },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        if (npc.memories.isNotEmpty()) {
          Text("SHARED MEMORIES", color = ChronicleColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          npc.memories.take(6).forEach { m ->
            Text(
              "• ${m.label} (${if (m.sentimentDelta >= 0) "+" else ""}${"%.1f".format(m.sentimentDelta)})",
              color = if (m.sentimentDelta >= 0) ChronicleColors.TextSecondary else ChronicleColors.Amber,
              fontSize = 12.sp,
            )
          }
        }
      }
    }
  }
}

// ==========================================
// 4. SCHEDULE & WELLNESS TAB (UX-03 + Design Review)
// ==========================================
@Composable
fun ScheduleTab(
  state: GameState,
  pack: EraPack,
  onUpdateSchedule: (WeeklySchedule) -> Unit,
  onApplyPreset: (SchedulePresetId) -> Unit,
  onSetFocusSkill: (SkillId) -> Unit,
  onPerformAction: (PlayerAction) -> Unit,
  busy: Boolean,
) {
  val p = state.player
  val cur = p.schedule
  val commitments = SimulationEngine.allocateCommitments(p)
  val reqHours = remember(state, pack) { SimulationEngine.getRequiredWorkOrSchoolHours(state, pack) }
  val total = cur.sleep + cur.workOrSchool + cur.hustleAndSkills + cur.fitnessAndSelfCare + cur.familyAndSocial + cur.leisureAndRest
  var previewPreset by remember { mutableStateOf<SchedulePresetId?>(null) }
  var showScheduleEditor by remember { mutableStateOf(false) }
  var showWellness by remember { mutableStateOf(false) }
  if (showWellness) WellnessDialog(state, pack, onPerformAction) { showWellness = false }
  if (showScheduleEditor) {
    ScheduleEditorDialog(cur, reqHours, busy, onUpdateSchedule) { showScheduleEditor = false }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(ChronicleColors.BgApp),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "schedule_page") {
      JournalPageTitle("Weekly schedule", "Allocate 168 hours", ChronicleColors.Amber)
      OutlinedButton(onClick = { showWellness = true }) { Text("Wellness & habits") }
    }
    item(key = "schedule_art") { ChapterArt(R.drawable.chapter_schedule) }
    // UX-03: Stacked label + total + single segmented allocation bar
    item(key = "schedule_bar") {
      CardBox {
        Text(
          text = "168-HOUR WEEKLY FOCUS",
          color = ChronicleColors.TextMuted,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "$total / 168h",
            color = if (total == 168) ChronicleColors.Emerald else ChronicleColors.Rose,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
          )
          if (reqHours > 0) {
            Text(
              text = if (cur.workOrSchool < reqHours) "${reqHours - cur.workOrSchool}h short for work/school" else "${reqHours}h minimum for work/school",
              color = ChronicleColors.Sky,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        if (p.activeClub != null || (p.activeProject?.progress ?: 100.0) < 100.0) {
          Spacer(Modifier.height(8.dp))
          Text("Reserved from skills and leisure", color = ChronicleColors.TextMuted, fontSize = 12.sp)
          Text(
            "Club ${commitments.clubHours}h · Project ${commitments.projectHours}h · Leisure left ${commitments.leisureHours}h",
            color = ChronicleColors.TextSecondary,
            fontSize = 13.sp,
          )
        }
        Spacer(Modifier.height(10.dp))
        // Segmented allocation bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ChronicleColors.BgElevated),
        ) {
          val segments = listOf(
            cur.workOrSchool to ChronicleColors.Sky,
            cur.hustleAndSkills to ChronicleColors.Violet,
            cur.fitnessAndSelfCare to ChronicleColors.Emerald,
            cur.sleep to Color(0xFF475569),
            cur.familyAndSocial to ChronicleColors.Pink,
            cur.leisureAndRest to ChronicleColors.Amber,
          )
          segments.forEach { (hrs, col) ->
            if (hrs > 0) {
              Box(
                modifier = Modifier
                  .weight(hrs.toFloat())
                  .fillMaxHeight()
                  .background(col),
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        Text(
          text = "Work ${cur.workOrSchool}h · Hustle ${cur.hustleAndSkills}h · Fit ${cur.fitnessAndSelfCare}h · Sleep ${cur.sleep}h · Family ${cur.familyAndSocial}h · Rest ${cur.leisureAndRest}h",
          color = ChronicleColors.TextSecondary,
          fontSize = 11.5.sp,
          lineHeight = 16.sp,
        )
      }
    }

    // Presets with honest comparison preview before applying (Design Review)
    item(key = "presets") {
      CardBox {
        SectionTitle("SCHEDULE PRESETS", "Tap a preset to preview trade-offs against your required hours")
        Spacer(Modifier.height(8.dp))
        val presets = listOf(
          SchedulePresetId.balanced to "Balanced",
          SchedulePresetId.hustle to "Hustle",
          SchedulePresetId.family_first to "Family First",
          SchedulePresetId.health_kick to "Health",
        )
        presets.chunked(2).forEach { pair ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { (id, label) ->
              ChoiceChipButton(
                label = label,
                selected = previewPreset == id,
                onClick = { previewPreset = id },
                modifier = Modifier.weight(1f),
              )
            }
          }
          Spacer(Modifier.height(6.dp))
        }

        previewPreset?.let { pid ->
          val raw = SimulationEngine.SCHEDULE_PRESETS.getValue(pid)
          val sim = SimulationEngine.normalizeSchedule(
            raw.copy(workOrSchool = max(raw.workOrSchool, reqHours)),
          )
          Spacer(Modifier.height(6.dp))
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(ChronicleColors.BgElevated)
              .border(1.dp, ChronicleColors.BorderStrong, RoundedCornerShape(10.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              "Preview: Work/School priority locks ${max(raw.workOrSchool, reqHours)}h",
              color = ChronicleColors.TextPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
            PreviewDiffLine("Work & School", cur.workOrSchool, sim.workOrSchool)
            PreviewDiffLine("Sleep", cur.sleep, sim.sleep)
            PreviewDiffLine("Family & Social", cur.familyAndSocial, sim.familyAndSocial)
            PreviewDiffLine("Leisure & Rest", cur.leisureAndRest, sim.leisureAndRest)
            PreviewDiffLine("Hustle & Skills", cur.hustleAndSkills, sim.hustleAndSkills)
            PreviewDiffLine("Fitness & Care", cur.fitnessAndSelfCare, sim.fitnessAndSelfCare)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { previewPreset = null },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
              ) { Text("Cancel", fontSize = 12.sp) }
              Button(
                onClick = {
                  onApplyPreset(pid)
                  previewPreset = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = ChronicleColors.Sky, contentColor = ChronicleColors.OnAccent),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
              ) { Text("Apply Preset", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
          }
        }
      }
    }

    item(key = "customize_week") {
      CardBox {
        SectionTitle("Customize week", "Set your hours, then save the whole week")
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { showScheduleEditor = true }, enabled = !busy) {
          Text("Edit hours")
        }
      }
    }

    // Focus Skill
    item(key = "focus_skill") {
      CardBox {
        SectionTitle("FOCUS SKILL", "Trained by weekly Hustle & Skills hours")
        Spacer(Modifier.height(8.dp))
        SkillId.entries.chunked(2).forEach { pair ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { sk ->
              val lvl = (p.skills[sk] ?: 0.0).roundToInt()
              ChoiceChipButton(
                label = "${sk.name.replace('_', ' ').replaceFirstChar { it.uppercase() }} ($lvl)",
                selected = p.focusSkill == sk,
                onClick = { onSetFocusSkill(sk) },
                modifier = Modifier.weight(1f),
              )
            }
          }
          Spacer(Modifier.height(6.dp))
        }
      }
    }

    // Wellness & Habits
    item(key = "wellness") {
      CardBox {
        SectionTitle("WELLNESS & HABITS", "Manage medical care and recurring habits")
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onPerformAction(PlayerAction.DoctorVisit(p.conditions.firstOrNull { !it.treated }?.id)) },
            enabled = p.actionPoints >= 2,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
          ) { Text("Doctor · 2 AP", fontSize = 12.sp) }
          OutlinedButton(
            onClick = { onPerformAction(PlayerAction.TherapySession) },
            enabled = p.actionPoints >= 2,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(9.dp),
          ) { Text("Therapy · 2 AP", fontSize = 12.sp) }
        }

        TextButton(onClick = { showWellness = true }) {
          Text("Activities, routines & habit support")
        }
      }
    }
  }
}

@Composable
private fun PreviewDiffLine(label: String, cur: Int, next: Int) {
  val diff = next - cur
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = ChronicleColors.TextSecondary, fontSize = 11.5.sp)
    Text(
      text = if (diff == 0) "${next}h" else "${cur}h -> ${next}h (${if (diff > 0) "+$diff" else "$diff"}h)",
      color = when {
        diff > 0 -> ChronicleColors.Emerald
        diff < 0 -> ChronicleColors.Amber
        else -> ChronicleColors.TextMuted
      },
      fontSize = 11.5.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}


private fun Npc.relationshipLabel(): String = if (role == NpcRole.parent) {
  when (gender) {
    Gender.female -> "Mum"
    Gender.male -> "Dad"
    Gender.non_binary -> "Parent"
  }
} else role.name.replaceFirstChar { it.uppercase() }
