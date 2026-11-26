package com.chronicle.lifesim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronicle.lifesim.engine.CountryDef
import com.chronicle.lifesim.engine.NetWorthPoint
import com.chronicle.lifesim.engine.formatAge
import com.chronicle.lifesim.engine.formatMoney
import com.chronicle.lifesim.ui.theme.ChronicleColors

@Composable
internal fun NetWorthHistoryCard(
  history: List<NetWorthPoint>,
  ageMonths: Int,
  netWorth: Long,
  country: CountryDef,
) {
  val points = remember(history, ageMonths, netWorth) {
    (history.takeLast(23) + NetWorthPoint(ageMonths, netWorth))
      .associateBy { it.ageMonths }.values.sortedBy { it.ageMonths }
  }
  val first = points.first()
  val latest = points.last()
  val change = latest.netWorth - first.netWorth
  val accent = if (change >= 0) ChronicleColors.Emerald else ChronicleColors.Rose
  val changeText = (if (change > 0) "+" else "") + formatMoney(change, country)

  CardBox {
    Text("Net worth history", color = ChronicleColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (points.size < 2) {
      Text("Age up to start tracking your net worth.", color = ChronicleColors.TextSecondary, fontSize = 13.sp)
    } else {
      Spacer(Modifier.height(8.dp))
      Column(
        Modifier.fillMaxWidth().background(ChronicleColors.BgInput, RoundedCornerShape(12.dp)).padding(12.dp),
      ) {
        Text(changeText, color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Change over this period", color = ChronicleColors.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Spacer(
          modifier = Modifier.fillMaxWidth().height(80.dp).semantics {
            contentDescription = "Net worth went from ${formatMoney(first.netWorth, country)} to " +
              "${formatMoney(latest.netWorth, country)} between ${formatAge(first.ageMonths)} and ${formatAge(latest.ageMonths)}."
          }.drawWithCache {
            val low = points.minOf { it.netWorth }.toDouble()
            val high = points.maxOf { it.netWorth }.toDouble()
            val ageSpan = (latest.ageMonths - first.ageMonths).coerceAtLeast(1)
            val inset = 4.dp.toPx()
            val plotHeight = size.height - 2 * inset
            val coordinates = points.map { point ->
              val x = size.width * (point.ageMonths - first.ageMonths) / ageSpan
              val fraction = if (high == low) 0.5 else (high - point.netWorth) / (high - low)
              Offset(x, inset + plotHeight * fraction.toFloat())
            }
            val line = Path().apply {
              moveTo(coordinates.first().x, coordinates.first().y)
              coordinates.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path().apply {
              addPath(line)
              lineTo(coordinates.last().x, size.height)
              lineTo(coordinates.first().x, size.height)
              close()
            }
            val gradient = Brush.verticalGradient(listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0f)))
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val gridColor = ChronicleColors.BorderSubtle
            onDrawBehind {
              for (index in 1..3) {
                val y = size.height * index / 4
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
              }
              drawPath(fill, brush = gradient)
              drawPath(line, color = accent, style = stroke)
            }
          },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(formatAge(first.ageMonths), color = ChronicleColors.TextSecondary, fontSize = 12.sp)
          Text(formatAge(latest.ageMonths), color = ChronicleColors.TextSecondary, fontSize = 12.sp)
        }
      }
    }
  }
}
