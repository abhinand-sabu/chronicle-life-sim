package com.chronicle.lifesim.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chronicle.lifesim.ui.theme.ChronicleColors

internal val LocalChronicleNotice = compositionLocalOf<Pair<String, Boolean>?> { null }
internal val LocalDismissChronicleNotice = compositionLocalOf<() -> Unit> { {} }

@Composable
internal fun ChronicleNotice(respectNavigationBar: Boolean = false) {
  val notice = LocalChronicleNotice.current
  var previous by remember { mutableStateOf(notice) }
  SideEffect {
    if (notice != null) previous = notice
  }
  AnimatedVisibility(
    visible = notice != null,
    enter = expandVertically(tween(240, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom) +
      fadeIn(tween(160)),
    exit = shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) +
      fadeOut(tween(200)),
  ) {
    val (message, error) = (notice ?: previous) ?: return@AnimatedVisibility
    val dismiss = LocalDismissChronicleNotice.current
    val accent = if (error) ChronicleColors.Rose else ChronicleColors.Emerald
    val inset = if (respectNavigationBar) Modifier.navigationBarsPadding().padding(horizontal = 14.dp) else Modifier
    Surface(
      modifier = inset.fillMaxWidth().padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
      color = accent.copy(alpha = 0.09f).compositeOver(ChronicleColors.BgElevated),
      contentColor = ChronicleColors.TextPrimary,
    ) {
      Row(
        Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
          Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { if (notice != null) dismiss() }) {
          Text("Dismiss", color = accent)
        }
      }
    }
  }
}
