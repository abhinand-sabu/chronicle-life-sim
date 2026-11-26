package com.chronicle.lifesim.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Mulberry32 deterministic PRNG matching the TypeScript engine's uint32 arithmetic.
 */
class SeededRng(seed: Long) {
  private var state: Long = seed and 0xFFFFFFFFL

  fun getState(): Long = state and 0xFFFFFFFFL

  fun next(): Double {
    state = (state + 0x6D2B79F5L) and 0xFFFFFFFFL
    var t = state.toInt()
    t = (t xor (t ushr 15)) * (t or 1)
    t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
    val out = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
    return out.toDouble() / 4294967296.0
  }

  fun nextInt(minVal: Int, maxVal: Int): Int {
    if (maxVal <= minVal) return minVal
    return (next() * (maxVal - minVal + 1)).toInt() + minVal
  }

  fun nextGaussian(): Double {
    val u1 = max(1e-7, next())
    val u2 = next()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
  }

  fun chance(p: Double): Boolean {
    if (p <= 0.0) return false
    if (p >= 1.0) return true
    return next() < p
  }

  fun <T> pick(items: List<T>): T {
    require(items.isNotEmpty()) { "SeededRng.pick called on empty list" }
    return items[(next() * items.size).toInt().coerceIn(0, items.lastIndex)]
  }

  fun <T> weightedPick(items: List<T>, getWeight: (T) -> Double): T? {
    var total = 0.0
    for (item in items) total += max(0.0, getWeight(item))
    if (total <= 0.0) return null
    var roll = next() * total
    for (item in items) {
      roll -= max(0.0, getWeight(item))
      if (roll <= 0.0) return item
    }
    return items.lastOrNull()
  }
}

fun clamp(value: Double, minVal: Double, maxVal: Double): Double = max(minVal, min(maxVal, value))
fun clampInt(value: Int, minVal: Int, maxVal: Int): Int = max(minVal, min(maxVal, value))

fun getMonthsPerTurn(granularity: TurnGranularity): Int = when (granularity) {
  TurnGranularity.YEARLY -> 12
  TurnGranularity.QUARTERLY -> 3
  TurnGranularity.BIMONTHLY -> 2
  TurnGranularity.MONTHLY -> 1
}

fun getDt(granularity: TurnGranularity): Double = getMonthsPerTurn(granularity).toDouble() / 12.0

fun scaleAnnualProbability(annualProb: Double, granularity: TurnGranularity): Double {
  val p = clamp(annualProb, 0.0, 1.0)
  if (p == 0.0) return 0.0
  if (p == 1.0) return 1.0
  return 1.0 - (1.0 - p).pow(getDt(granularity))
}

fun scaleCompoundFactor(annualRate: Double, granularity: TurnGranularity): Double {
  val safeBase = max(0.05, 1.0 + annualRate)
  return safeBase.pow(getDt(granularity))
}

fun getAgeYears(ageMonths: Int): Int = ageMonths / 12

fun getLifeStage(ageMonths: Int): LifeStage {
  val years = getAgeYears(ageMonths)
  return when {
    years <= 4 -> LifeStage.INFANCY
    years <= 12 -> LifeStage.CHILDHOOD
    years <= 17 -> LifeStage.ADOLESCENCE
    years <= 29 -> LifeStage.YOUNG_ADULT
    years <= 49 -> LifeStage.PRIME_ADULT
    years <= 64 -> LifeStage.MATURE_ADULT
    else -> LifeStage.ELDER
  }
}

fun formatAge(ageMonths: Int): String {
  val y = ageMonths / 12
  val m = ageMonths % 12
  return if (m == 0) "${y}y" else "${y}y ${m}m"
}

const val MAX_MONEY: Long = 1_000_000_000_000L
const val MIN_MONEY: Long = -1_000_000_000_000L

fun clampMoney(value: Long): Long = value.coerceIn(MIN_MONEY, MAX_MONEY)

fun safeAddMoney(a: Long, b: Long): Long {
  val minBig = java.math.BigInteger.valueOf(MIN_MONEY)
  val maxBig = java.math.BigInteger.valueOf(MAX_MONEY)
  return java.math.BigInteger.valueOf(a)
    .add(java.math.BigInteger.valueOf(b))
    .max(minBig)
    .min(maxBig)
    .toLong()
}

fun formatGranularityShort(g: TurnGranularity): String = when (g) {
  TurnGranularity.MONTHLY -> "+1m"
  TurnGranularity.BIMONTHLY -> "+2m"
  TurnGranularity.QUARTERLY -> "+3m"
  TurnGranularity.YEARLY -> "+1y"
}

fun formatAgeActionLabel(g: TurnGranularity): String = when (g) {
  TurnGranularity.MONTHLY -> "Age 1 month"
  TurnGranularity.BIMONTHLY -> "Age 2 months"
  TurnGranularity.QUARTERLY -> "Age 3 months"
  TurnGranularity.YEARLY -> "Age 1 year"
}

fun formatGranularityLabel(g: TurnGranularity): String = when (g) {
  TurnGranularity.MONTHLY -> "Monthly (12/yr)"
  TurnGranularity.BIMONTHLY -> "Bi-monthly (6/yr)"
  TurnGranularity.QUARTERLY -> "Quarterly (4/yr)"
  TurnGranularity.YEARLY -> "Yearly (1/yr)"
}

fun computeTurnActionPoints(granularity: TurnGranularity, energy: Double, burnout: Double): Int {
  var ap = when (granularity) {
    TurnGranularity.MONTHLY -> 3
    TurnGranularity.BIMONTHLY -> 5
    TurnGranularity.QUARTERLY -> 7
    TurnGranularity.YEARLY -> 12
  }
  if (burnout >= 75.0) ap -= 2 else if (burnout >= 50.0) ap -= 1
  if (energy <= 20.0) ap -= 1
  return max(2, ap)
}

fun advanceCalendar(year: Int, month: Int, monthsToAdd: Int): Pair<Int, Int> {
  val total = year * 12 + (month - 1) + monthsToAdd
  return (total / 12) to ((total % 12) + 1)
}

fun formatMoney(amount: Long, country: CountryDef): String {
  val sign = if (amount < 0) "-" else ""
  val absVal = abs(amount).toDouble()
  return when {
    absVal >= 1_000_000_000 -> "$sign${country.currencySymbol}${"%.2f".format(absVal / 1_000_000_000.0)}B"
    absVal >= 1_000_000 -> "$sign${country.currencySymbol}${"%.2f".format(absVal / 1_000_000.0)}M"
    absVal >= 10_000 -> "$sign${country.currencySymbol}${(absVal / 1_000.0).roundToLong()}K"
    else -> "$sign${country.currencySymbol}${absVal.roundToLong().let { "%,d".format(it) }}"
  }
}

fun formatExactMoney(amount: Long, country: CountryDef): String {
  val sign = if (amount < 0) "-" else ""
  return "$sign${country.currencySymbol}${"%,d".format(abs(amount))}"
}

fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
fun Double.round4(): Double = (this * 10000.0).roundToInt() / 10000.0
