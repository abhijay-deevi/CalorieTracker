package com.dub.calorietracker.data

import com.dub.calorietracker.calc.Calculator
import com.dub.calorietracker.calc.WeekPlanner
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One Sunday–Saturday week of check-ins and food, in the user's units. Future days are left out. */
data class WeekSummary(
    val start: LocalDate,
    /** Last day included: Saturday, or today for the current week. */
    val end: LocalDate,
    val inProgress: Boolean,
    val weights: List<Pair<LocalDate, Double>>,
    /** Latest waist measurement this week. */
    val waist: Double?,
    val days: Int,
    val loggedDays: Int,
    val avgCalories: Double,
    val avgProtein: Double,
    val avgTarget: Double,
    val gym: Int,
    val cardio: Int,
)

/** Totals for a stretch of days (future days left out). Averages are over days with food logged. */
data class PeriodStats(
    val days: Int,
    val loggedDays: Int,
    val avgCalories: Double,
    val avgTarget: Double,
    val avgProtein: Double,
    val gym: Int,
    val cardio: Int,
    /** Morning weights in the user's units, oldest first. */
    val weights: List<Pair<LocalDate, Double>>,
)

/**
 * What gets sent to ChatGPT: daily morning weight, waist once a week, weekly calorie and protein intake,
 * gym sessions and cardio sessions per week. Plain text so ChatGPT reads it without explanation.
 */
object ChatExport {
    private val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private fun n(v: Double) = NumberFormat.getIntegerInstance(Locale.US).format(v.roundToInt())
    private fun one(v: Double) = String.format(Locale.US, "%.1f", v)

    fun units(state: AppState) = state.plan?.profile?.units ?: Units.US
    fun weightIn(kg: Double, units: Units) = if (units == Units.US) Calculator.kgToLb(kg) else kg
    fun weightLabel(units: Units) = if (units == Units.US) "lb" else "kg"
    fun waistIn(cm: Double, units: Units) = if (units == Units.US) cm / 2.54 else cm
    fun waistLabel(units: Units) = if (units == Units.US) "in" else "cm"

    private fun days(from: LocalDate, to: LocalDate) =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    fun stats(state: AppState, from: LocalDate, to: LocalDate, today: LocalDate): PeriodStats {
        val days = days(from, if (to.isAfter(today)) today else to)
        val units = units(state)
        val logged = days.filter { d -> state.entries.any { it.date == d.toString() } }
        val eaten = logged.map { WeekPlanner.eaten(state.entries, it) }
        fun avg(xs: List<Double>) = if (xs.isEmpty()) 0.0 else xs.average()
        return PeriodStats(
            days = days.size,
            loggedDays = logged.size,
            avgCalories = avg(eaten.map { it.calories }),
            avgTarget = if (state.plan == null) 0.0 else avg(logged.map { WeekPlanner.dayTarget(state, it).toDouble() }),
            avgProtein = avg(eaten.map { it.protein }),
            gym = days.count { state.dayLogs[it.toString()]?.gym == true },
            cardio = days.count { state.dayLogs[it.toString()]?.cardio == true },
            weights = days.mapNotNull { d -> state.dayLogs[d.toString()]?.weightKg?.let { d to weightIn(it, units) } },
        )
    }

    fun week(state: AppState, anyDay: LocalDate, today: LocalDate): WeekSummary {
        val start = WeekPlanner.weekStart(anyDay)
        val sat = start.plusDays(6)
        val end = if (sat.isAfter(today)) today else sat
        val s = stats(state, start, end, today)
        val units = units(state)
        val waist = days(start, end).mapNotNull { state.dayLogs[it.toString()]?.waistCm }.lastOrNull()
        return WeekSummary(
            start, end, inProgress = sat.isAfter(today), weights = s.weights, waist = waist?.let { waistIn(it, units) },
            days = s.days, loggedDays = s.loggedDays, avgCalories = s.avgCalories, avgProtein = s.avgProtein,
            avgTarget = s.avgTarget, gym = s.gym, cardio = s.cardio,
        )
    }

    /** The weeks touching [from]..[to] (only weeks that have started by [today]), oldest first. */
    fun text(state: AppState, from: LocalDate, to: LocalDate, today: LocalDate): String {
        val weeks = generateSequence(WeekPlanner.weekStart(from)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(to) && !it.isAfter(today) }
            .map { week(state, it, today) }.toList()
        val units = units(state)
        val wl = weightLabel(units)
        val plan = state.plan
        val sb = StringBuilder()
        sb.appendLine("My progress log (weeks run Sunday to Saturday). Weight is morning body weight in $wl; waist in ${waistLabel(units)}.")
        if (plan != null) {
            val protein = if (plan.proteinG > 0) plan.proteinG.toDouble() else WeekPlanner.macroTargets(plan.dailyTarget, state).protein
            sb.appendLine("My plan: ${n(plan.dailyTarget.toDouble())} cal/day on average, protein goal ${n(protein)} g/day.")
        }
        for (w in weeks) {
            sb.appendLine()
            sb.appendLine("Week of ${w.start.format(dayFmt)} - ${w.start.plusDays(6).format(dayFmt)}" +
                if (w.inProgress) " (in progress, through ${w.end.format(dowFmt)})" else "")
            sb.appendLine("- Morning weight: " + if (w.weights.isEmpty()) "not logged" else
                w.weights.joinToString(", ") { "${it.first.format(dowFmt)} ${one(it.second)}" } +
                    (if (w.weights.size > 1) " (average ${one(w.weights.map { it.second }.average())})" else ""))
            sb.appendLine("- Waist: " + (w.waist?.let { "${one(it)} ${waistLabel(units)}" } ?: "not measured"))
            sb.appendLine("- Intake: " + if (w.loggedDays == 0) "not logged" else
                "averaged ${n(w.avgCalories)} cal/day and ${n(w.avgProtein)} g protein/day (${w.loggedDays} of ${w.days} days logged)")
            sb.appendLine("- Gym sessions: ${w.gym}")
            sb.appendLine("- Cardio sessions: ${w.cardio}")
        }
        return sb.toString().trimEnd()
    }
}
