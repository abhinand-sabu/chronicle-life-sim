package com.chronicle.lifesim.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronicle.lifesim.R
import com.chronicle.lifesim.engine.*
import com.chronicle.lifesim.ui.theme.ChronicleColors
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
  pack: EraPack,
  hasSaves: Boolean,
  onStartLife: (CreateLifeOptions) -> Unit,
  onOpenSaves: () -> Unit,
) {
  var firstName by rememberSaveable { mutableStateOf("") }
  var lastName by rememberSaveable { mutableStateOf("") }
  var gender by rememberSaveable { mutableStateOf(Gender.female) }
  var countryId by rememberSaveable { mutableStateOf(pack.meta.defaultCountryId) }
  var familyTier by rememberSaveable { mutableStateOf(FamilyWealthTier.middle) }
  var countrySearch by rememberSaveable { mutableStateOf("") }

  // Collapsible advanced customization (Design Review + UX-01)
  var showCustomize by rememberSaveable { mutableStateOf(false) }
  var granularity by rememberSaveable { mutableStateOf(TurnGranularity.BIMONTHLY) }
  var difficulty by rememberSaveable { mutableStateOf(Difficulty.realistic) }
  var chaosLevel by rememberSaveable { mutableFloatStateOf(35f) }
  var econVol by rememberSaveable { mutableFloatStateOf(40f) }
  var ironman by rememberSaveable { mutableStateOf(false) }

  var longevity by rememberSaveable { mutableFloatStateOf(62f) }
  var intellectCeiling by rememberSaveable { mutableFloatStateOf(68f) }
  var athleticism by rememberSaveable { mutableFloatStateOf(60f) }
  var mentalResilience by rememberSaveable { mutableFloatStateOf(62f) }
  var appearance by rememberSaveable { mutableFloatStateOf(60f) }

  val filteredCountries = remember(pack.countries, countrySearch) {
    val q = countrySearch.trim().lowercase()
    if (q.isEmpty()) pack.countries else pack.countries.filter {
      it.name.lowercase().contains(q) || it.currencyCode.lowercase().contains(q)
    }
  }
  val selectedCountry = remember(pack, countryId) { pack.country(countryId) }

  fun launchLife(randomAll: Boolean) {
    val chosenCountry = if (randomAll) pack.countries.random() else selectedCountry
    val chosenGender = if (randomAll) Gender.entries.random() else gender
    val chosenTier = if (randomAll) FamilyWealthTier.entries.random() else familyTier
    val opts = if (randomAll) {
      CreateLifeOptions(
        countryId = chosenCountry.id,
        gender = chosenGender,
        familyWealthTier = chosenTier,
        granularity = granularity,
        difficulty = difficulty,
        chaosLevel = chaosLevel.roundToInt(),
        economyVolatility = econVol.roundToInt(),
        ironman = ironman,
      )
    } else {
      CreateLifeOptions(
        firstName = firstName.trim().ifEmpty { null },
        lastName = lastName.trim().ifEmpty { null },
        gender = gender,
        countryId = countryId,
        granularity = granularity,
        difficulty = difficulty,
        chaosLevel = chaosLevel.roundToInt(),
        economyVolatility = econVol.roundToInt(),
        ironman = ironman,
        familyWealthTier = familyTier,
        dnaOverrides = DnaProfile(
          longevity = longevity.roundToInt(),
          intellectCeiling = intellectCeiling.roundToInt(),
          athleticism = athleticism.roundToInt(),
          mentalResilience = mentalResilience.roundToInt(),
          appearance = appearance.roundToInt(),
        ),
      )
    }
    onStartLife(opts)
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
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      JournalCover(
        title = "Chronicle",
        subtitle = "Create a life",
        bottom = {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = onOpenSaves,
              border = BorderStroke(1.dp, ChronicleColors.OnCover.copy(alpha = 0.5f)),
            ) {
              Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = ChronicleColors.OnCover, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(8.dp))
              Text(if (hasSaves) "Load a Life" else "Save Slots", color = ChronicleColors.OnCover)
            }
            ThemeToggle(onCover = true)
          }
        },
      )

      ChapterArt(R.drawable.chapter_setup)

      // 1. Identity Card (Required/Immediate setup)
      CardBox {
        SectionTitle("IDENTITY", "Leave blank for realistic local names")
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = setupTextFieldColors(),
          )
          OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = setupTextFieldColors(),
          )
        }

        Spacer(Modifier.height(10.dp))
        Text("Gender", color = ChronicleColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // UX-01: wrap gracefully without mid-word ellipsis at 320dp 1.3x
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            Gender.female to "Female",
            Gender.male to "Male",
            Gender.non_binary to "Non-binary",
          ).forEach { (g, label) ->
            ChoiceChipButton(
              label = label,
              selected = gender == g,
              onClick = { gender = g },
              modifier = Modifier.defaultMinSize(minWidth = 92.dp),
            )
          }
        }
      }

      // 2. Birth Country Card
      CardBox {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionTitle("BIRTH COUNTRY", "${selectedCountry.flag} ${selectedCountry.name} (${selectedCountry.currencyCode})")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = countrySearch,
          onValueChange = { countrySearch = it },
          placeholder = { Text("Search countries or currency...", fontSize = 12.5.sp) },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = setupTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))

        val rows = filteredCountries.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          rows.forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              pair.forEach { c ->
                val active = c.id == countryId
                Surface(
                  onClick = { countryId = c.id },
                  shape = RoundedCornerShape(10.dp),
                  color = if (active) ChronicleColors.EmeraldDeep.copy(alpha = 0.30f) else ChronicleColors.BgInput,
                  border = BorderStroke(1.dp, if (active) ChronicleColors.Emerald else ChronicleColors.BorderSubtle),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(c.flag, fontSize = 16.sp)
                    Text(
                      text = c.name,
                      color = if (active) ChronicleColors.TextPrimary else ChronicleColors.TextSecondary,
                      fontSize = 12.5.sp,
                      fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
              if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
      }

      // 3. Family Background (UX-01: 2-column grid instead of horizontal overflow)
      CardBox {
        SectionTitle("FAMILY BACKGROUND", "Sets starting cash and neighborhood")
        Spacer(Modifier.height(8.dp))
        val tiers = listOf(
          FamilyWealthTier.struggling to "Struggling",
          FamilyWealthTier.working to "Working",
          FamilyWealthTier.middle to "Middle",
          FamilyWealthTier.upper_middle to "Upper-Mid",
          FamilyWealthTier.wealthy to "Wealthy",
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          tiers.chunked(2).forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              pair.forEach { (tier, label) ->
                ChoiceChipButton(
                  label = label,
                  selected = familyTier == tier,
                  onClick = { familyTier = tier },
                  modifier = Modifier.weight(1f),
                )
              }
              if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
      }

      // 4. Collapsible World & DNA Customization (Design Review)
      CardBox {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showCustomize = !showCustomize }
            .padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "CUSTOMIZE WORLD & DNA",
              color = ChronicleColors.TextPrimary,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.6.sp,
            )
            Text(
              "${formatGranularityShort(granularity)} · ${difficulty.name.replaceFirstChar { it.uppercase() }}${if (ironman) " · Ironman" else ""}",
              color = ChronicleColors.TextSecondary,
              fontSize = 11.5.sp,
            )
          }
          Icon(
            if (showCustomize) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = ChronicleColors.TextSecondary,
          )
        }

        AnimatedVisibility(visible = showCustomize) {
          Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Turn Speed", color = ChronicleColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              TurnGranularity.entries.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  pair.forEach { g ->
                    ChoiceChipButton(
                      label = formatGranularityLabel(g),
                      selected = granularity == g,
                      onClick = { granularity = g },
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }

            Text("Difficulty", color = ChronicleColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(
                Difficulty.forgiving to "Forgiving",
                Difficulty.realistic to "Realistic",
                Difficulty.hardcore to "Hardcore",
              ).forEach { (d, lbl) ->
                ChoiceChipButton(
                  label = lbl,
                  selected = difficulty == d,
                  onClick = { difficulty = d },
                  modifier = Modifier.weight(1f),
                )
              }
            }

            SliderRow("World Chaos", chaosLevel.roundToInt(), 0f..100f, "${chaosLevel.roundToInt()}%") { chaosLevel = it }
            SliderRow("Economy Volatility", econVol.roundToInt(), 0f..100f, "${econVol.roundToInt()}%") { econVol = it }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Ironman Mode", color = ChronicleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Locks Director Mode overrides", color = ChronicleColors.TextMuted, fontSize = 11.5.sp)
              }
              Switch(checked = ironman, onCheckedChange = { ironman = it })
            }

            HorizontalDivider(color = ChronicleColors.BorderSubtle)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Genetics & Potential", color = ChronicleColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              TextButton(
                onClick = {
                  longevity = Random.nextInt(40, 92).toFloat()
                  intellectCeiling = Random.nextInt(42, 95).toFloat()
                  athleticism = Random.nextInt(38, 92).toFloat()
                  mentalResilience = Random.nextInt(40, 92).toFloat()
                  appearance = Random.nextInt(38, 92).toFloat()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text("Roll DNA", fontSize = 12.sp, color = ChronicleColors.Sky)
              }
            }
            SliderRow("Longevity", longevity.roundToInt(), 30f..98f, "${longevity.roundToInt()}") { longevity = it }
            SliderRow("Intellect Ceiling", intellectCeiling.roundToInt(), 35f..99f, "${intellectCeiling.roundToInt()}") { intellectCeiling = it }
            SliderRow("Athleticism", athleticism.roundToInt(), 30f..98f, "${athleticism.roundToInt()}") { athleticism = it }
            SliderRow("Mental Resilience", mentalResilience.roundToInt(), 30f..98f, "${mentalResilience.roundToInt()}") { mentalResilience = it }
            SliderRow("Appearance", appearance.roundToInt(), 30f..98f, "${appearance.roundToInt()}") { appearance = it }
          }
        }
      }
    }

    // Sticky bottom CTA bar designed to grow vertically without clipping at 320dp 1.3x
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
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = { launchLife(randomAll = true) },
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, ChronicleColors.BorderStrong),
          modifier = Modifier
            .weight(0.36f)
            .heightIn(min = 48.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) {
          Text(
            "Random",
            color = ChronicleColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
          )
        }
        Button(
          onClick = { launchLife(randomAll = false) },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ChronicleColors.Emerald,
            contentColor = ChronicleColors.OnAccent,
          ),
          modifier = Modifier
            .weight(0.64f)
            .heightIn(min = 48.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Text(
            "Begin New Life",
            fontSize = 14.sp,
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
private fun setupTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedBorderColor = ChronicleColors.Emerald,
  unfocusedBorderColor = ChronicleColors.BorderStrong,
  focusedContainerColor = ChronicleColors.BgInput,
  unfocusedContainerColor = ChronicleColors.BgInput,
  focusedTextColor = ChronicleColors.TextPrimary,
  unfocusedTextColor = ChronicleColors.TextPrimary,
  focusedLabelColor = ChronicleColors.Emerald,
  unfocusedLabelColor = ChronicleColors.TextMuted,
)

@Composable
fun CardBox(
  modifier: Modifier = Modifier,
  borderColor: Color = ChronicleColors.BorderSubtle,
  bgColor: Color = ChronicleColors.BgCard,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(bgColor, RoundedCornerShape(12.dp))
      .border(1.dp, borderColor, RoundedCornerShape(12.dp))
      .padding(16.dp),
    content = content,
  )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      color = ChronicleColors.TextPrimary,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.7.sp,
    )
    if (subtitle != null) {
      Text(
        text = subtitle,
        color = ChronicleColors.TextSecondary,
        fontSize = 12.sp,
      )
    }
  }
}

@Composable
fun ChoiceChipButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (selected) ChronicleColors.EmeraldDeep.copy(alpha = 0.34f) else ChronicleColors.BgInput,
    border = BorderStroke(1.dp, if (selected) ChronicleColors.Emerald else ChronicleColors.BorderSubtle),
    modifier = modifier
      .heightIn(min = 38.dp)
      .semantics(mergeDescendants = true) {
        this.selected = selected
        role = Role.RadioButton
      },
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
      Text(
        text = label,
        color = if (selected) ChronicleColors.TextPrimary else ChronicleColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun SliderRow(
  label: String,
  value: Int,
  range: ClosedFloatingPointRange<Float>,
  valueLabel: String,
  commitOnRelease: Boolean = false,
  enabled: Boolean = true,
  onChange: (Float) -> Unit,
) {
  var draft by remember(value, range) { mutableFloatStateOf(value.toFloat().coerceIn(range)) }
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, color = ChronicleColors.TextSecondary, fontSize = 12.5.sp)
      Text(if (commitOnRelease) "${draft.roundToInt()}h" else valueLabel, color = ChronicleColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
      value = if (commitOnRelease) draft else value.toFloat(),
      onValueChange = { newValue ->
        if (commitOnRelease) draft = newValue else onChange(newValue)
      },
      onValueChangeFinished = if (commitOnRelease) ({ onChange(draft) }) else null,
      valueRange = range,
      enabled = enabled && range.endInclusive > range.start,
      colors = SliderDefaults.colors(
        thumbColor = ChronicleColors.Emerald,
        activeTrackColor = ChronicleColors.Emerald,
        inactiveTrackColor = ChronicleColors.BgElevated,
      ),
    )
  }
}
