package com.chronicle.lifesim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronicle.lifesim.ui.theme.ChronicleColors

/** The shared book-cover treatment for the beginning and end of a life. */
@Composable
internal fun JournalCover(
  title: String,
  subtitle: String,
  bottom: @Composable (() -> Unit)? = null,
) {
  Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ChronicleColors.Cover)) {
    Canvas(Modifier.matchParentSize()) {
      val stroke = 1.dp.toPx()
      val ink = ChronicleColors.OnCover.copy(alpha = 0.16f)
      val bindingX = 12.dp.toPx()
      drawLine(ink, Offset(bindingX, 0f), Offset(bindingX, size.height), stroke)
      val center = Offset(size.width * 0.92f, size.height * 0.22f)
      for (ring in 1..5) {
        val radius = 14.dp.toPx() * ring
        drawOval(
          ink,
          topLeft = Offset(center.x - radius, center.y - radius * 0.72f),
          size = Size(radius * 2f, radius * 1.44f),
          style = Stroke(stroke),
        )
      }
    }
    Column(
      Modifier.fillMaxWidth().padding(start = 26.dp, top = 21.dp, end = 18.dp, bottom = 19.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Text(title, color = ChronicleColors.OnCover, fontFamily = ChronicleColors.Display, fontSize = 35.sp, lineHeight = 41.sp)
      Text(subtitle, color = ChronicleColors.OnCover.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp)
      bottom?.invoke()
    }
  }
}

@Composable
internal fun JournalPageTitle(title: String, detail: String, accent: Color = ChronicleColors.Emerald) {
  Column(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(title, color = ChronicleColors.TextPrimary, fontFamily = ChronicleColors.Display, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
    Text(detail, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    HorizontalDivider(Modifier.padding(top = 5.dp), color = ChronicleColors.BorderStrong)
  }
}

@Composable
internal fun ActionPointsLabel(remaining: Int, maximum: Int) {
  Text(
    "$remaining / $maximum AP",
    color = if (remaining > 0) ChronicleColors.Emerald else ChronicleColors.Rose,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
  )
}

internal const val APPEARANCE_PREFS = "chronicle_appearance"
internal const val DARK_MODE_KEY = "dark_mode"

@Composable
internal fun ThemeToggle(onCover: Boolean = false) {
  val context = LocalContext.current
  val dark = ChronicleColors.darkMode
  TextButton(
    onClick = {
      context.getSharedPreferences(APPEARANCE_PREFS, 0).edit().putBoolean(DARK_MODE_KEY, !dark).apply()
      ChronicleColors.updateDarkMode(!dark)
    },
  ) {
    Text(
      if (dark) "Use light mode" else "Use dark mode",
      color = if (onCover) ChronicleColors.OnCover else ChronicleColors.Emerald,
    )
  }
}
