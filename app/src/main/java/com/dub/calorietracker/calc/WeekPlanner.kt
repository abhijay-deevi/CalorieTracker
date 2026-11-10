package com.dub.calorietracker.calc

import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.MacroSplit
import java.time.LocalDate
import kotlin.math.roundToInt

data class Macros(val calories: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(calories + o.calories, protein + o.protein, carbs + o.carbs, fat + o.fat)
    companion object { val ZERO = Macros(0.0, 0.0, 0.0, 0.0) }
}

/**
 * Week math: weeks run Monday → Sunday. Calorie lists (plan template, day swaps) are indexed by weekday,
 * 0 = Sunday … 6 = Saturday, so each weekday keeps its own target (zigzag high days stay on Saturday and Sunday).
 */
object WeekPlanner {
    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** Weekday indexes in display order, Monday first: 1, 2, 3, 4, 5, 6, 0. */
    val mondayFirst = listOf(1, 2, 3, 4, 5, 6, 0)

    /** The Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays(((dayIndex(date) + 6) % 7).toLong())

    /** Weekday: 0 = Sunday … 6 = Saturday */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** The 7 dates (Monday → Sunday) of the week containing [date]. */
    fun weekDates(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    /** The 7 calorie targets (indexed by weekday) for the week containing [date], with any day swaps applied. */
    fun weekTargets(state: AppState, date: LocalDate): List<Int> {
        val plan = state.plan ?: return List(7) { 0 }
        return state.weekOverrides[weekStart(date).toString()] ?: plan.weekTemplate
    }

    fun dayTarget(state: AppState, date: LocalDate): Int = weekTargets(state, date)[dayIndex(date)]

    /**
     * Macro goals for a day with [calories]. Protein and fat are fixed grams from the plan (same every day);
     * carbs take the rest, so high days mean more carbs, not more protein.
     */
    fun macroTargets(calories: Int, state: AppState): Macros {
        val plan = state.plan
        if (plan != null && plan.proteinG > 0) {
            return Macros(calories.toDouble(), plan.proteinG.toDouble(),
                Calculator.carbGrams(calories, plan.proteinG, plan.fatG).toDouble(), plan.fatG.toDouble())
        }
        return macroTargets(calories, state.macros)
    }

    /** Older plans: percentages of each day's calories. */
    fun macroTargets(calories: Int, split: MacroSplit): Macros = Macros(
        calories.toDouble(),
        protein = calories * split.proteinPct / 100.0 / 4.0,
        carbs = calories * split.carbsPct / 100.0 / 4.0,
        fat = calories * split.fatPct / 100.0 / 9.0,
    )

    fun eaten(entries: List<FoodEntry>, date: LocalDate): Macros {
        val d = date.toString()
        return entries.filter { it.date == d }
            .fold(Macros.ZERO) { acc, e -> acc + Macros(e.calories, e.protein, e.carbs, e.fat) }
    }

    /** True when this schedule has days with different targets (zigzag). */
    fun isZigzag(state: AppState) = state.plan?.weekTemplate?.distinct()?.size?.let { it > 1 } ?: false

    /** A day counts as a "high" day when its target is above the week's average. */
    fun isHighDay(targets: List<Int>, index: Int): Boolean {
        val avg = targets.average()
        return targets[index] > avg + 1
    }

    /** Returns a new state with weekdays [a] and [b] (0 = Sunday) of the week containing [date] swapped. */
    fun swapDays(state: AppState, date: LocalDate, a: Int, b: Int): AppState {
        if (a == b) return state
        val targets = weekTargets(state, date).toMutableList()
        val tmp = targets[a]; targets[a] = targets[b]; targets[b] = tmp
        val key = weekStart(date).toString()
        val template = state.plan?.weekTemplate
        val overrides = if (targets == template) state.weekOverrides - key
        else state.weekOverrides + (key to targets.toList())
        return state.copy(weekOverrides = overrides)
    }

    /** The later day this week with the highest target above [date]'s, or null if none. */
    fun bestLaterHighDay(state: AppState, date: LocalDate): LocalDate? {
        val targets = weekTargets(state, date)
        val today = targets[dayIndex(date)]
        return weekDates(date).filter { it.isAfter(date) && targets[dayIndex(it)] > today }
            .maxByOrNull { targets[dayIndex(it)] }
    }

    /**
     * Auto-detect: if you've eaten more than today's target and a higher-calorie day is still
     * coming up this week, move that higher day to today (weekly total stays the same).
     * Only runs for the current day so past days are never rewritten.
     */
    fun autoShift(state: AppState, date: LocalDate, today: LocalDate): AppState {
        if (!state.autoDetectHighDays || state.plan == null || !isZigzag(state)) return state
        if (date != today) return state
        val eaten = eaten(state.entries, date).calories
        if (eaten <= dayTarget(state, date)) return state
        val laterDate = bestLaterHighDay(state, date) ?: return state
        val dayName = laterDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        val swapped = swapDays(state, date, dayIndex(date), dayIndex(laterDate))
        val newTarget = dayTarget(swapped, date)
        val msg = "You went over today's regular target, so today is now your higher-calorie day " +
            "($newTarget cal). $dayName is now ${dayTarget(swapped, laterDate)} cal."
        return swapped.copy(shiftNotices = swapped.shiftNotices + (date.toString() to msg))
    }

    fun fmt(v: Double): String = v.roundToInt().toString()
}
