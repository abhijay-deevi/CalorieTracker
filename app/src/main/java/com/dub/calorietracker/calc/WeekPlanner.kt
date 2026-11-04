package com.dub.calorietracker.calc

import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.MacroSplit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class Macros(val calories: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(calories + o.calories, protein + o.protein, carbs + o.carbs, fat + o.fat)
    companion object { val ZERO = Macros(0.0, 0.0, 0.0, 0.0) }
}

/** Week math: weeks run Sunday → Saturday. */
object WeekPlanner {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** 0 = Sunday … 6 = Saturday */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun weekDates(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    /** The 7 calorie targets for the week containing [date], with any day swaps applied. */
    fun weekTargets(state: AppState, date: LocalDate): List<Int> {
        val plan = state.plan ?: return List(7) { 0 }
        return state.weekOverrides[weekStart(date).toString()] ?: plan.weekTemplate
    }

    fun dayTarget(state: AppState, date: LocalDate): Int = weekTargets(state, date)[dayIndex(date)]

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

    /** Returns a new state with days [a] and [b] of the week containing [date] swapped. */
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

    /** The later day this week with the highest target above today's, or null if none. */
    fun bestLaterHighDay(state: AppState, date: LocalDate): Int? {
        val targets = weekTargets(state, date)
        val i = dayIndex(date)
        val today = targets[i]
        return (i + 1..6).filter { targets[it] > today }.maxByOrNull { targets[it] }
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
        val later = bestLaterHighDay(state, date) ?: return state
        val laterDate = weekStart(date).plusDays(later.toLong())
        val dayName = laterDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        val swapped = swapDays(state, date, dayIndex(date), later)
        val newTarget = dayTarget(swapped, date)
        val msg = "You went over today's regular target, so today is now your higher-calorie day " +
            "($newTarget cal). $dayName is now ${dayTarget(swapped, laterDate)} cal."
        return swapped.copy(shiftNotices = swapped.shiftNotices + (date.toString() to msg))
    }

    fun fmt(v: Double): String = v.roundToInt().toString()
}
