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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun ActivePlanCard(
  state: GameState,
  pack: EraPack,
  onPerformAction: (PlayerAction) -> Unit,
  onOpenActivities: () -> Unit,
) {
  val p = state.player
  val ageYears = getAgeYears(p.ageMonths)
  val club = p.activeClub
  val proj = p.activeProject
  val commitments = SimulationEngine.allocateCommitments(p)

  if (club != null) {
    val teammate = state.npcs.firstOrNull { it.id == club.teammateNpcId && it.alive }
    val remMonths = max(0, club.termLengthMonths - club.termMonthsCompleted)
    CardBox(
      bgColor = ChronicleColors.BgCard,
      borderColor = if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.Sky.copy(alpha = 0.55f),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Outlined.School, contentDescription = null, tint = ChronicleColors.Sky, modifier = Modifier.size(15.dp))
          Text(
            text = "AFTER SCHOOL · ${commitments.clubHours}/${club.weeklyHours}H/WK",
            color = ChronicleColors.Sky,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
          )
        }
        Text(
          text = if (club.showcaseReady) "Showcase Ready!" else "${remMonths}m to showcase",
          color = if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.TextMuted,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = club.name,
        color = ChronicleColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
      )
      if (teammate != null) {
        Text(
          text = "Teammate: ${teammate.firstName} ${teammate.lastName} (${teammate.affection.roundToInt()}% bond)",
          color = ChronicleColors.TextSecondary,
          fontSize = 12.sp,
        )
      }
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(
        progress = { (club.progress / 100.0).toFloat().coerceIn(0f, 1f) },
        color = if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.Sky,
        trackColor = ChronicleColors.BgElevated,
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
      )
      Spacer(Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            if (club.showcaseReady) onPerformAction(PlayerAction.EnterClubShowcase(true))
            else onPerformAction(PlayerAction.PracticeClub)
          },
          enabled = p.actionPoints >= 1,
          shape = RoundedCornerShape(9.dp),
          border = BorderStroke(1.dp, if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.Sky),
          modifier = Modifier.weight(1f).heightIn(min = 40.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
          Text(
            text = if (club.showcaseReady) "Enter Showcase · 1 AP" else "Practice for Showcase · 1 AP",
            color = if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.Sky,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(
          onClick = onOpenActivities,
          shape = RoundedCornerShape(9.dp),
          border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
          modifier = Modifier.heightIn(min = 40.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text("Activities", color = ChronicleColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }
    return
  }

  if (proj != null) {
    CardBox(
      bgColor = ChronicleColors.BgCard,
      borderColor = ChronicleColors.Violet.copy(alpha = 0.6f),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("ACTIVE PROJECT", color = ChronicleColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(if (proj.progress >= 100.0) "Ready to finish" else "${proj.progress.roundToInt()}% done", color = ChronicleColors.TextMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
      }
      Spacer(Modifier.height(4.dp))
      Text(proj.title, color = ChronicleColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(6.dp))
      LinearProgressIndicator(
        progress = { (proj.progress / 100.0).toFloat().coerceIn(0f, 1f) },
        color = ChronicleColors.Violet,
        trackColor = ChronicleColors.BgElevated,
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
      )
      Spacer(Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { onPerformAction(PlayerAction.WorkOnProject) },
          enabled = p.actionPoints >= 1,
          shape = RoundedCornerShape(9.dp),
          border = BorderStroke(1.dp, ChronicleColors.Violet),
          modifier = Modifier.weight(1f).heightIn(min = 40.dp),
        ) {
          Text(if (proj.progress >= 100.0) "Finish Project · 1 AP" else "Work on Project · 1 AP", color = ChronicleColors.Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = onOpenActivities,
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier.heightIn(min = 40.dp),
        ) {
          Text("Activities", color = ChronicleColors.TextPrimary, fontSize = 12.sp)
        }
      }
    }
    return
  }

  // Stage-tailored Activities launcher when no active commitment
  val (stageHeader, stageHint) = when {
    ageYears < 6 -> "EARLY YEARS" to "Grow up to age 6 to join school clubs & launch projects."
    ageYears in 6..12 -> "AFTER SCHOOL & ACTIVITIES" to "Join a club, invite a friend over, or start a stall."
    ageYears in 13..17 -> "TEEN ACTIVITIES & CLUBS" to "Build skills, launch a zine, or deepen friendships."
    ageYears >= 60 || p.retired -> "RETIREMENT & LEGACY" to "Volunteer, mentor, host family gatherings, or build a project."
    else -> "ACTIVITIES & PLANS" to "Meet someone, start a portfolio project, or plan a family."
  }

  Surface(
    onClick = onOpenActivities,
    shape = RoundedCornerShape(13.dp),
    color = ChronicleColors.BgCard,
    border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Explore, contentDescription = null, tint = ChronicleColors.Sky, modifier = Modifier.size(20.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(stageHeader, color = ChronicleColors.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(stageHint, color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
      }
      Text("Explore", color = ChronicleColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitiesDialog(
  state: GameState,
  pack: EraPack,
  onPerformAction: (PlayerAction) -> Unit,
  onDismiss: () -> Unit,
  onOpenDating: () -> Unit,
) {
  val p = state.player
  val ageYears = getAgeYears(p.ageMonths)
  val country = pack.country(state.config.countryId)
  val friend = state.npcs.firstOrNull { it.alive && it.role == NpcRole.friend }
  val partner = state.npcs.firstOrNull { it.alive && (it.role == NpcRole.partner || it.role == NpcRole.spouse) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    CardBox(
      borderColor = ChronicleColors.BorderStrong,
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .heightIn(max = 620.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Activities & Commitments", color = ChronicleColors.TextPrimary, fontFamily = ChronicleColors.Display, fontSize = 23.sp, lineHeight = 27.sp)
          ActionPointsLabel(p.actionPoints, p.maxActionPoints)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "Close activities", tint = ChronicleColors.TextSecondary)
        }
      }
      ChronicleNotice()
      HorizontalDivider(color = ChronicleColors.BorderSubtle)
      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (ageYears < 6) {
          Text(
            "Activities open at age 6 when primary school begins.",
            color = ChronicleColors.TextSecondary,
            fontSize = 13.sp,
          )
        }

        SimulationEngine.paidActivity(state, country)?.let { offer ->
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Earn spending money", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
              if (offer.monthsUntilAvailable > 0) "Available in ${offer.monthsUntilAvailable} months"
              else "${formatMoney(offer.reward, country)} · 1 AP · Once every 3 months",
              color = ChronicleColors.TextSecondary,
              fontSize = 12.sp,
            )
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.DoPaidActivity) },
              enabled = offer.monthsUntilAvailable == 0 && p.actionPoints > 0,
              modifier = Modifier.fillMaxWidth(),
            ) { Text(offer.title) }
          }
        }

        // 1. After School Clubs (Ages 6..17)
        if (ageYears in 6..17) {
          Text("AFTER-SCHOOL CLUBS", color = ChronicleColors.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
          val club = p.activeClub
          if (club != null) {
            val teammate = state.npcs.firstOrNull { it.id == club.teammateNpcId && it.alive }
            val rem = max(0, club.termLengthMonths - club.termMonthsCompleted)
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ChronicleColors.BgElevated)
                .border(1.dp, ChronicleColors.BorderStrong, RoundedCornerShape(12.dp))
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(club.name, color = ChronicleColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${club.progress.roundToInt()}% prep", color = ChronicleColors.Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              if (teammate != null) {
                Text("Teammate: ${teammate.firstName} ${teammate.lastName} · ${teammate.affection.roundToInt()}% bond", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
              }
              Text("Practice hours / week (0 AP)", color = ChronicleColors.TextMuted, fontSize = 11.5.sp)
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 4, 6).forEach { hrs ->
                  ChoiceChipButton(
                    label = "${hrs}h/wk",
                    selected = club.weeklyHours == hrs,
                    onClick = { onPerformAction(PlayerAction.SetClubHours(hrs)) },
                    modifier = Modifier.weight(1f),
                  )
                }
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.PracticeClub) },
                  enabled = p.actionPoints >= 1,
                  modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                  shape = RoundedCornerShape(9.dp),
                ) {
                  Text("Practice · 1 AP", fontSize = 12.sp)
                }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.EnterClubShowcase(true)) },
                  enabled = club.showcaseReady && p.actionPoints >= 1,
                  modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                  shape = RoundedCornerShape(9.dp),
                  border = BorderStroke(1.dp, if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.BorderSubtle),
                ) {
                  Text(
                    text = if (club.showcaseReady) "Showcase · 1 AP" else "Next term (${rem}m)",
                    color = if (club.showcaseReady) ChronicleColors.Gold else ChronicleColors.TextMuted,
                    fontSize = 11.5.sp,
                  )
                }
              }
              TextButton(
                onClick = { onPerformAction(PlayerAction.LeaveClub) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
              ) {
                Text("Leave ${club.name}", color = ChronicleColors.Rose, fontSize = 11.5.sp)
              }
            }
          } else {
            listOf(
              Triple(ClubId.robotics, "Robotics Club", "Coding skill · Team builds"),
              Triple(ClubId.theatre, "Theatre Troupe", "Creativity skill · Term play"),
              Triple(ClubId.soccer, "Youth Soccer", "Discipline & Health · Varsity cup"),
            ).forEach { (id, title, sub) ->
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
                  Text(title, color = ChronicleColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                  Text(sub, color = ChronicleColors.TextSecondary, fontSize = 11.5.sp)
                }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.JoinClub(id, 4)) },
                  enabled = p.actionPoints >= 1,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text("Join · 1 AP", color = ChronicleColors.Sky, fontSize = 12.sp)
                }
              }
            }
          }
        }

        // 2. Projects & Side Quests
        if (ageYears >= 6) {
          Text("PROJECTS & INITIATIVES", color = ChronicleColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
          val proj = p.activeProject
          if (proj != null) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ChronicleColors.BgElevated)
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(proj.title, color = ChronicleColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text("${proj.progress.roundToInt()}%", color = ChronicleColors.Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              LinearProgressIndicator(
                progress = { (proj.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                color = ChronicleColors.Violet,
                trackColor = ChronicleColors.BgInput,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.WorkOnProject) },
                  enabled = p.actionPoints >= 1,
                  modifier = Modifier.weight(1f),
                ) { Text(if (proj.progress >= 100.0) "Finish · 1 AP" else "Work · 1 AP", fontSize = 12.sp, color = ChronicleColors.Violet) }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.AbandonProject) },
                  border = BorderStroke(1.dp, ChronicleColors.Rose.copy(alpha = 0.5f)),
                ) { Text("Abandon", fontSize = 12.sp, color = ChronicleColors.Rose) }
              }
            }
          } else {
            for (offer in SimulationEngine.projectOffers(ageYears, country)) {
              val cost = offer.cost
              val canAfford = p.finance.cash >= cost
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
                  Text(offer.title, color = ChronicleColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                  Text(
                    "Budget ${formatMoney(cost, country)} · Earns cash & Portfolio credit",
                    color = ChronicleColors.TextSecondary,
                    fontSize = 11.5.sp,
                  )
                }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.StartProject(offer.type)) },
                  enabled = canAfford && p.actionPoints >= 1,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    if (canAfford) "Start · 1 AP" else "Needs ${formatMoney(cost, country)}",
                    color = if (canAfford) ChronicleColors.Violet else ChronicleColors.TextMuted,
                    fontSize = 11.5.sp,
                  )
                }
              }
            }
          }
        }

        // 3. Social Circle, Dating & Family Planning
        if (ageYears >= 6) {
          Text("SOCIAL & FAMILY PLANS", color = ChronicleColors.Pink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.MeetSomeone(romantic = false)) },
              enabled = p.actionPoints >= 1,
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier.heightIn(min = 40.dp),
            ) {
              Text("Meet a Friend · 1 AP", fontSize = 12.sp, color = ChronicleColors.TextPrimary)
            }

            OutlinedButton(
              onClick = { friend?.let { onPerformAction(PlayerAction.InviteFriendOver(it.id)) } },
              enabled = friend != null && p.actionPoints >= 1,
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier.heightIn(min = 40.dp),
            ) {
              Text(
                if (friend != null) "Invite ${friend.firstName} Over · 1 AP" else "Invite Friend (Need friend)",
                fontSize = 12.sp,
                color = if (friend != null) ChronicleColors.Sky else ChronicleColors.TextMuted,
              )
            }

            if (ageYears >= country.legalAdultAge && partner == null) {
              OutlinedButton(
                onClick = onOpenDating,
                enabled = p.actionPoints >= 1,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, ChronicleColors.Pink),
                modifier = Modifier.heightIn(min = 40.dp),
              ) {
                Text("Find a partner", fontSize = 12.sp, color = ChronicleColors.Pink)
              }
            }
          }

          if (ageYears >= country.legalAdultAge && partner != null) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ChronicleColors.BgInput)
                .border(1.dp, ChronicleColors.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("Family Planning with ${partner.firstName}", color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.DiscussFamily("try")) },
                  enabled = p.actionPoints >= 1,
                  modifier = Modifier.weight(1f),
                ) { Text("Try · 1 AP", fontSize = 11.5.sp) }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.DiscussFamily("adopt")) },
                  enabled = p.actionPoints >= 1,
                  modifier = Modifier.weight(1f),
                ) { Text("Adopt · 1 AP", fontSize = 11.5.sp) }
                OutlinedButton(
                  onClick = { onPerformAction(PlayerAction.DiscussFamily("wait")) },
                  enabled = p.actionPoints >= 1,
                  modifier = Modifier.weight(1f),
                ) { Text("Wait · 1 AP", fontSize = 11.5.sp) }
              }
            }
          }
        }

        // 4. Elder & Retirement Roles
        if (ageYears >= 60 || p.retired) {
          Text("ELDER & COMMUNITY ROLES", color = ChronicleColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.ElderActivity("volunteer")) },
              enabled = p.actionPoints >= 1,
              modifier = Modifier.weight(1f),
            ) { Text("Volunteer", fontSize = 11.5.sp, color = ChronicleColors.Gold) }
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.ElderActivity("mentor")) },
              enabled = p.actionPoints >= 1,
              modifier = Modifier.weight(1f),
            ) { Text("Mentor", fontSize = 11.5.sp, color = ChronicleColors.Gold) }
            OutlinedButton(
              onClick = { onPerformAction(PlayerAction.ElderActivity("gathering")) },
              enabled = p.actionPoints >= 1,
              modifier = Modifier.weight(1f),
            ) { Text("Family", fontSize = 11.5.sp, color = ChronicleColors.Gold) }
          }
        }
      }
    }
  }
}
