package com.chronicle.lifesim.engine

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToLong

@Serializable
enum class Goods(val label: String, val category: String, val price: Long, val resale: Double) {
  bike("City bicycle", "Transport", 350, 0.65),
  car("Used hatchback", "Transport", 6800, 0.70),
  phone("Smartphone", "Electronics", 420, 0.55),
  laptop("Laptop", "Electronics", 950, 0.60),
  furniture("Living room set", "Home", 780, 0.55),
  appliance("Kitchen appliances", "Home", 520, 0.60),
  instrument("Acoustic guitar", "Hobbies", 290, 0.70),
  camera("Digital camera", "Hobbies", 640, 0.62);

  fun cost(country: CountryDef) = (price * country.costOfLivingMultiplier).roundToLong()
  fun resaleValue(country: CountryDef) = (cost(country) * resale).roundToLong()
}

@Serializable
enum class LoanType(
  val label: String, val principal: Long, val apr: Double, val termMonths: Int,
  val minCredit: Int, val requiresIncome: Boolean,
) {
  bank("Bank personal loan", 6500, 0.085, 36, 650, true),
  community("Credit union loan", 2500, 0.135, 24, 570, true),
  emergency("Emergency lender", 900, 0.32, 12, 420, false),
  shady("Unlicensed lender", 1700, 0.85, 8, 0, false);

  private val monthlyRate = (1 + apr).pow(1.0 / 12) - 1

  fun amount(country: CountryDef) = (principal * country.costOfLivingMultiplier).roundToLong()
  fun monthlyPayment(country: CountryDef) =
    (amount(country) * monthlyRate / (1 - (1 + monthlyRate).pow(-termMonths))).roundToLong().coerceAtLeast(1)

  fun accrueMonthlyInterest(balance: Long) = clampMoney((balance * (1 + monthlyRate)).roundToLong())
}

@Serializable
data class PersonalLoan(val type: LoanType, val balance: Long, val lateMonths: Int = 0)

enum class Gift(val label: String, val price: Long) {
  letter("Handwritten note", 0),
  flowers("Flowers", 28),
  book("Book", 38),
  headphones("Headphones", 115),
  keepsake("Personal keepsake", 165);

  fun cost(country: CountryDef) = (price * country.costOfLivingMultiplier).roundToLong()
}

enum class Wellness(val label: String, val benefit: String) {
  rest("Rest and reset", "+8 Energy · −4 Stress"),
  movement("Go for a walk", "+4 Health · −3 Stress"),
  breathing("Breathing exercise", "+4 Mental health · −6 Stress")
}

enum class InterviewAnswer(val label: String, val hint: String) {
  practical("Walk through your approach", "Demonstrates hands-on skills"),
  teamwork("Use a teamwork example", "Demonstrates communication"),
  dependable("Explain how you keep commitments", "Demonstrates discipline"),
}

internal fun isRomantic(npc: Npc) = npc.alive && (npc.role == NpcRole.partner || npc.role == NpcRole.spouse)
internal fun hasFamilyHousing(npcs: List<Npc>) = npcs.any { it.alive && it.role == NpcRole.parent && it.affection >= 58.0 && it.tension < 35.0 }

internal data class LoanSettlement(
  val cash: Long, val loans: List<PersonalLoan>, val paid: Long, val creditLoss: Int, val collections: Boolean,
)

internal fun settleLoans(finance: FinanceState, available: Long, country: CountryDef, gran: TurnGranularity): LoanSettlement {
  var cash = available
  var paid = 0L
  var creditLoss = 0
  var collections = false
  val months = getMonthsPerTurn(gran)
  // Highest-interest loans are paid first when there isn't enough cash for every payment.
  val loans = finance.personalLoans.sortedByDescending { it.type.apr }.mapNotNull { loan ->
    val type = loan.type
    val installment = type.monthlyPayment(country)
    var balance = loan.balance
    var late = loan.lateMonths
    repeat(months) {
      if (balance == 0L) return@repeat
      balance = type.accrueMonthlyInterest(balance)
      val due = minOf(balance, installment)
      val payment = minOf(cash.coerceAtLeast(0), due)
      cash = safeAddMoney(cash, -payment)
      paid = safeAddMoney(paid, payment)
      balance -= payment
      if (payment == due) {
        late = 0
      } else {
        late = (late + 1).coerceAtMost(1200)
        creditLoss += if (type == LoanType.shady) 12 else 5
        if (late == 3) collections = true
      }
    }
    if (balance == 0L) null else loan.copy(balance = balance, lateMonths = late)
  }
  return LoanSettlement(cash, loans, paid, creditLoss, collections)
}
