package com.chronicle.lifesim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object ChronicleColors {
  var darkMode by mutableStateOf(false)
    private set

  fun updateDarkMode(enabled: Boolean) {
    darkMode = enabled
  }

  private fun tone(light: Long, dark: Long): Color = Color(if (darkMode) dark else light)

  val Display = FontFamily.Serif
  val Cover = Color(0xFF203D34)
  val OnCover = Color(0xFFFFF9EB)
  val OnAccent get() = tone(0xFFFFFDF6, 0xFF15271E)
  val BgApp get() = tone(0xFFF4F1E7, 0xFF151B18)
  val BgCard get() = tone(0xFFFFFCF4, 0xFF202923)
  val BgElevated get() = tone(0xFFE8EADF, 0xFF2D3A31)
  val BgInput get() = tone(0xFFF2EFE5, 0xFF19231E)
  val BorderSubtle get() = tone(0xFFD9D9CA, 0xFF2A362E)
  val BorderStrong get() = tone(0xFFB9C0AD, 0xFF3C4B40)

  val TextPrimary get() = tone(0xFF202921, 0xFFF4F0E4)
  val TextSecondary get() = tone(0xFF4D5D50, 0xFFC7CFC2)
  val TextMuted get() = tone(0xFF5E6B5E, 0xFFA8B6A9)

  val Emerald get() = tone(0xFF285F48, 0xFF8BC7A4)
  val EmeraldDeep get() = tone(0xFF214736, 0xFF32694E)
  val Rose get() = tone(0xFFA23F38, 0xFFF3A19B)
  val Amber get() = tone(0xFF864817, 0xFFF0B576)
  val Gold get() = tone(0xFF806015, 0xFFE4C37B)
  val Sky get() = tone(0xFF315E78, 0xFF90BEDA)
  val Pink get() = tone(0xFF904667, 0xFFE6ABCA)
  val Violet get() = tone(0xFF5D537C, 0xFFC7B5E7)
}

private fun journalScheme() = (if (ChronicleColors.darkMode) darkColorScheme() else lightColorScheme()).copy(
  primary = ChronicleColors.Emerald,
  onPrimary = ChronicleColors.OnAccent,
  secondary = ChronicleColors.Sky,
  tertiary = ChronicleColors.Violet,
  background = ChronicleColors.BgApp,
  onBackground = ChronicleColors.TextPrimary,
  surface = ChronicleColors.BgCard,
  onSurface = ChronicleColors.TextPrimary,
  surfaceVariant = ChronicleColors.BgElevated,
  onSurfaceVariant = ChronicleColors.TextSecondary,
  outline = ChronicleColors.BorderStrong,
  primaryContainer = ChronicleColors.BgElevated,
  onPrimaryContainer = ChronicleColors.TextPrimary,
  error = ChronicleColors.Rose,
  onError = ChronicleColors.OnAccent,
  onSecondary = ChronicleColors.OnAccent,
  onTertiary = ChronicleColors.OnAccent,
  surfaceContainer = ChronicleColors.BgCard,
)

private val ChronicleTypography = Typography(
  headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
  titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
  titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
  bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
  labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ChronicleTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = journalScheme(),
    typography = ChronicleTypography,
    shapes = Shapes(
      extraSmall = RoundedCornerShape(5.dp),
      small = RoundedCornerShape(8.dp),
      medium = RoundedCornerShape(12.dp),
      large = RoundedCornerShape(16.dp),
      extraLarge = RoundedCornerShape(20.dp),
    ),
    content = content,
  )
}
