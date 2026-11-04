package com.dub.calorietracker.calc

import com.dub.calorietracker.data.Goal
import com.dub.calorietracker.data.Profile
import com.dub.calorietracker.data.ScheduleType
import com.dub.calorietracker.data.Sex
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Calculator {
    /** Lowest daily calories a schedule will suggest (common safe minimums). */
    fun calorieFloor(sex: Sex) = if (sex == Sex.MALE) 1500 else 1200

    /** Mifflin-St Jeor BMR. */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun maintenance(p: Profile): Int = (bmr(p) * p.activity.multiplier).roundToInt()

    fun goalTarget(maintenance: Int, goal: Goal): Int = maintenance - goal.deficit

    fun percentOf(maintenance: Int, target: Int): Int =
        if (maintenance == 0) 0 else (target * 100.0 / maintenance).roundToInt()

    /**
     * Calories for Sunday..Saturday.
     *
     * Zigzag 1: two high days (Sat/Sun) at maintenance, five equal low days, same weekly total.
     *   If the low days would fall below the floor, low days = floor and the remainder goes to the high days.
     * Zigzag 2: calories vary every day around the target (Sun lowest, Wed highest), same weekly total.
     */
    fun weekTemplate(maintenance: Int, target: Int, sex: Sex, type: ScheduleType): List<Int> {
        val weekly = target * 7
        val floor = calorieFloor(sex)
        return when (type) {
            ScheduleType.STANDARD -> List(7) { target }

            ScheduleType.ZIGZAG_1 -> {
                var high = maintenance.toDouble()
                var low = (weekly - 2 * high) / 5
                if (low < floor) {
                    low = min(floor.toDouble(), target.toDouble())
                    high = (weekly - 5 * low) / 2
                }
                val h = high.roundToInt()
                val l = low.roundToInt()
                listOf(h, l, l, l, l, l, h)
            }

            ScheduleType.ZIGZAG_2 -> {
                // Swing size: the deficit, but never let the lowest day go under the floor.
                val swing = max(0, min(maintenance - target, target - floor)).toDouble()
                val factors = listOf(-1.0, -1.0 / 3, 1.0 / 3, 1.0, 2.0 / 3, 0.0, -2.0 / 3)
                factors.map { (target + it * swing).roundToInt() }
            }
        }
    }

    fun lbToKg(lb: Double) = lb * 0.45359237
    fun kgToLb(kg: Double) = kg / 0.45359237
    fun ftInToCm(ft: Int, inches: Double) = (ft * 12 + inches) * 2.54
    fun cmToFtIn(cm: Double): Pair<Int, Int> {
        val totalIn = (cm / 2.54).roundToInt()
        return totalIn / 12 to totalIn % 12
    }
}
