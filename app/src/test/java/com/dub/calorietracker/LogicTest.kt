package com.dub.calorietracker

import com.dub.calorietracker.calc.Calculator
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.ActivityLevel
import com.dub.calorietracker.data.EntrySource
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.FoodMemory
import com.dub.calorietracker.data.Goal
import com.dub.calorietracker.data.Plan
import com.dub.calorietracker.data.Profile
import com.dub.calorietracker.data.ScheduleType
import com.dub.calorietracker.data.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    // Numbers from the reference calculator screenshots (maintenance 2,587, male).
    private val m = 2587

    @Test fun goals() {
        assertEquals(2337, Calculator.goalTarget(m, Goal.MILD_LOSS))
        assertEquals(2087, Calculator.goalTarget(m, Goal.LOSS))
        assertEquals(1587, Calculator.goalTarget(m, Goal.EXTREME_LOSS))
        assertEquals(90, Calculator.percentOf(m, 2337))
        assertEquals(81, Calculator.percentOf(m, 2087))
        assertEquals(61, Calculator.percentOf(m, 1587))
    }

    @Test fun zigzag1() {
        assertEquals(listOf(2587, 2237, 2237, 2237, 2237, 2237, 2587), Calculator.weekTemplate(m, 2337, Sex.MALE, ScheduleType.ZIGZAG_1))
        assertEquals(listOf(2587, 1887, 1887, 1887, 1887, 1887, 2587), Calculator.weekTemplate(m, 2087, Sex.MALE, ScheduleType.ZIGZAG_1))
        assertEquals(listOf(1805, 1500, 1500, 1500, 1500, 1500, 1805), Calculator.weekTemplate(m, 1587, Sex.MALE, ScheduleType.ZIGZAG_1))
    }

    @Test fun zigzag2() {
        assertEquals(listOf(2087, 2254, 2420, 2587, 2504, 2337, 2170), Calculator.weekTemplate(m, 2337, Sex.MALE, ScheduleType.ZIGZAG_2))
        assertEquals(listOf(1587, 1920, 2254, 2587, 2420, 2087, 1754), Calculator.weekTemplate(m, 2087, Sex.MALE, ScheduleType.ZIGZAG_2))
        assertEquals(listOf(1500, 1558, 1616, 1674, 1645, 1587, 1529), Calculator.weekTemplate(m, 1587, Sex.MALE, ScheduleType.ZIGZAG_2))
    }

    @Test fun mifflin() {
        // 30 y/o male, 180 cm, 80 kg: 10*80 + 6.25*180 - 5*30 + 5 = 1780
        val p = Profile(30, Sex.MALE, 180.0, 80.0, ActivityLevel.MODERATE)
        assertEquals(1780.0, Calculator.bmr(p), 0.01)
        assertEquals(2608, Calculator.maintenance(p))
    }

    @Test fun memory() {
        val foods = FoodMemory.remember(emptyList(), "peanut butter jelly sandwich",
            "i ate peanut butter jelly which was 2 white bread, 1 spread peanut butter, 1 spread jelly",
            "", 380.0, 12.0, 50.0, 15.0, emptyList()) { "id1" }
        assertNotNull(FoodMemory.exactMatch("i ate peanut butter jelly", foods))
        assertNotNull(FoodMemory.exactMatch("I had a Peanut Butter Jelly Sandwich!", foods))
        assertNull(FoodMemory.exactMatch("i ate peanut butter", foods))
        assertEquals(1, FoodMemory.suggestions("peanut", foods).size)
    }

    @Test fun autoShift() {
        val template = Calculator.weekTemplate(m, 2087, Sex.MALE, ScheduleType.ZIGZAG_1)
        val plan = Plan(Profile(30, Sex.MALE, 180.0, 80.0, ActivityLevel.MODERATE), m, Goal.LOSS, 2087, ScheduleType.ZIGZAG_1, template)
        val wed = LocalDate.of(2026, 11, 4) // a Wednesday
        val entry = FoodEntry("e", wed.toString(), 0, "pizza", 2300.0, 0.0, 0.0, 0.0, source = EntrySource.TEXT)
        val s = WeekPlanner.autoShift(AppState(plan = plan, entries = listOf(entry)), wed, wed)
        val week = WeekPlanner.weekTargets(s, wed)
        assertEquals(2587, week[3])       // Wednesday became the high day
        assertEquals(1887, week[6])       // Saturday became regular
        assertEquals(template.sum(), week.sum()) // weekly total unchanged
    }

    @Test fun multiPhotoPromptSaysOneMeal() {
        val one = com.dub.calorietracker.ai.Prompt.user("chili", 1, emptyList())
        val three = com.dub.calorietracker.ai.Prompt.user(null, 3, emptyList())
        org.junit.Assert.assertTrue(one.contains("A photo of the food is attached."))
        org.junit.Assert.assertTrue(three.contains("3 photos are attached"))
        org.junit.Assert.assertTrue(three.contains("do not count any food twice"))
        org.junit.Assert.assertTrue(three.contains("estimate from the photo"))
    }
}
