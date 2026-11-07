package com.dub.calorietracker

import com.dub.calorietracker.calc.Calculator
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.ActivityLevel
import com.dub.calorietracker.data.DayLog
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

    @Test fun openFoodFactsParsing() {
        val off = com.dub.calorietracker.ai.OpenFoodFacts
        // Real shape (Nutella): only per-100 g values.
        val nutella = off.parse("3017620422003", """{"status":1,"product":{"product_name":"Nutella","brands":"Nutella, Ferrero",
            "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}""")!!
        assertEquals("Nutella", nutella.name)
        org.junit.Assert.assertFalse(nutella.perServing)
        assertEquals(539.0, nutella.calories, 0.01)
        // Per-serving values win when present.
        val bar = off.parse("1", """{"status":1,"product":{"product_name":"Protein Bar","brands":"Acme","serving_size":"1 bar (40 g)",
            "nutriments":{"energy-kcal_serving":190,"proteins_serving":20,"carbohydrates_serving":22,"fat_serving":7,"energy-kcal_100g":475}}}""")!!
        assertEquals("Acme Protein Bar", bar.name)
        assertEquals("1 bar (40 g)", bar.servingSize)
        assertEquals(190.0, bar.calories, 0.01)
        assertEquals(20.0, bar.protein, 0.01)
        // Unknown barcode.
        assertNull(off.parse("0", """{"status":0,"status_verbose":"product not found"}"""))
    }

    @Test fun macroPlan() {
        val me = Profile(30, Sex.MALE, 177.8, 81.65, ActivityLevel.MODERATE)
        // Recomp: ~5% under maintenance, top-of-range protein.
        assertEquals(2481, Calculator.goalTarget(2612, Goal.RECOMP))
        assertEquals(180, Calculator.proteinGrams(me, Goal.RECOMP))      // 2.2 g/kg
        assertEquals(163, Calculator.proteinGrams(me, Goal.LOSS))        // 2.0 g/kg
        // Heavier body weight: protein uses the weight at BMI 27 (85.4 kg at this height), not 120 kg.
        assertEquals(171, Calculator.proteinGrams(me.copy(weightKg = 120.0), Goal.LOSS))
        // Fat 25% of 2112 cal = 59 g; never under 0.6 g/kg.
        assertEquals(59, Calculator.fatGrams(2112, com.dub.calorietracker.data.MacroStyle.BALANCED, me))
        assertEquals(49, Calculator.fatGrams(1200, com.dub.calorietracker.data.MacroStyle.HIGHER_CARB, me))
        assertEquals(232, Calculator.carbGrams(2112, 163, 59))
    }

    @Test fun proteinStaysTheSameOnHighAndLowDays() {
        val template = Calculator.weekTemplate(2612, 2112, Sex.MALE, ScheduleType.ZIGZAG_1) // 2612 high, 1912 low
        val plan = Plan(null, 2612, Goal.LOSS, 2112, ScheduleType.ZIGZAG_1, template, proteinG = 163, fatG = 59)
        val s = AppState(plan = plan)
        val high = WeekPlanner.macroTargets(2612, s)
        val low = WeekPlanner.macroTargets(1912, s)
        assertEquals(163.0, high.protein, 0.0); assertEquals(163.0, low.protein, 0.0)
        assertEquals(59.0, high.fat, 0.0); assertEquals(59.0, low.fat, 0.0)
        assertEquals(175.0, high.carbs - low.carbs, 1.0) // the extra 700 cal are all carbs
        // Custom plan zigzag: high days ~20% over the average, same weekly total.
        val custom = Calculator.weekTemplate(Calculator.customHighDay(2000), 2000, Sex.FEMALE, ScheduleType.ZIGZAG_1)
        assertEquals(2400, custom[0]); assertEquals(1840, custom[1])
        assertEquals(14000, custom.sum())
    }

    @Test fun backupRoundTripWithoutKeys() {
        val b = com.dub.calorietracker.data.Backup
        val s = AppState(
            entries = listOf(FoodEntry("e", "2026-11-04", 0, "pizza", 600.0, 25.0, 70.0, 22.0, source = EntrySource.PASTE)),
            ai = com.dub.calorietracker.data.AiSettings(apiKeys = mapOf(com.dub.calorietracker.data.AiProvider.GEMINI to "secret")),
        )
        val text = b.toJson(s)
        org.junit.Assert.assertFalse(text.contains("secret"))
        val back = b.fromJson(text)!!
        assertEquals(s.entries, back.entries)
        // Restoring keeps the key already in the app.
        assertEquals("secret", b.restore(s, back).ai.key(com.dub.calorietracker.data.AiProvider.GEMINI))
        assertNull(b.fromJson("""{"hello":1}"""))
        assertNull(b.fromJson("not json"))
    }

    @Test fun restoreMergesInsteadOfReplacing() {
        val b = com.dub.calorietracker.data.Backup
        fun e(id: String, date: String) = FoodEntry(id, date, 0, id, 100.0, 1.0, 1.0, 1.0)
        val old = AppState(
            entries = listOf(e("a", "2026-09-01"), e("b", "2026-09-02")),
            dayLogs = mapOf("2026-09-01" to DayLog(weightKg = 82.0, gym = true)),
        )
        // After a reinstall: one new food and a new check-in before restoring.
        val now = AppState(entries = listOf(e("c", "2026-11-07")), dayLogs = mapOf("2026-11-07" to DayLog(weightKg = 80.0)))
        val merged = b.restore(now, b.fromJson(b.toJson(old))!!)
        assertEquals(setOf("a", "b", "c"), merged.entries.map { it.id }.toSet())
        assertEquals(setOf("2026-09-01", "2026-11-07"), merged.dayLogs.keys)
        // Restoring the same file twice doesn't duplicate anything.
        assertEquals(3, b.restore(merged, b.fromJson(b.toJson(old))!!).entries.size)
    }

    @Test fun chatExport() {
        val plan = Plan(
            Profile(30, Sex.MALE, 180.0, 80.0, ActivityLevel.MODERATE), 2600, Goal.LOSS, 2100,
            ScheduleType.STANDARD, List(7) { 2100 }, proteinG = 160, fatG = 60,
        )
        fun food(id: String, date: String, cal: Double, p: Double) = FoodEntry(id, date, 0, id, cal, p, 0.0, 0.0)
        val s = AppState(
            plan = plan,
            entries = listOf(food("a", "2026-10-26", 2000.0, 150.0), food("b", "2026-10-27", 2200.0, 170.0), food("c", "2026-11-02", 1900.0, 140.0)),
            dayLogs = mapOf(
                "2026-10-25" to DayLog(weightKg = Calculator.lbToKg(182.0), waistCm = 34.0 * 2.54),
                "2026-10-26" to DayLog(weightKg = Calculator.lbToKg(181.0), gym = true, cardio = true),
                "2026-10-28" to DayLog(gym = true),
                "2026-11-02" to DayLog(weightKg = Calculator.lbToKg(180.4), cardio = true),
            ),
        )
        val ce = com.dub.calorietracker.data.ChatExport
        val t = ce.text(s, LocalDate.parse("2026-10-25"), LocalDate.parse("2026-11-07"), today = LocalDate.parse("2026-11-04"))
        assert(t.contains("My plan: 2,100 cal/day on average, protein goal 160 g/day.")) { t }
        fun block(vararg lines: String) = assert(t.contains(lines.joinToString("\n"))) { t }
        block("Week of Oct 25 - Oct 31", "- Morning weight: Sun 182.0, Mon 181.0 (average 181.5)", "- Waist: 34.0 in",
            "- Intake: averaged 2,100 cal/day and 160 g protein/day (2 of 7 days logged)", "- Gym sessions: 2", "- Cardio sessions: 1")
        block("Week of Nov 1 - Nov 7 (in progress, through Wed)", "- Morning weight: Mon 180.4", "- Waist: not measured",
            "- Intake: averaged 1,900 cal/day and 140 g protein/day (1 of 4 days logged)", "- Gym sessions: 0", "- Cardio sessions: 1")
        assert(!t.contains("Nov 8")) { t } // weeks that haven't started are left out
    }

    @Test fun pasteParser() {
        val pp = com.dub.calorietracker.data.PasteParser
        // The requested format, inside a longer ChatGPT reply.
        val a = pp.parse("Here's your breakdown...\nTotal 1,660\nLOG: banana bread & paneer sandwiches | 1,660 cal | 200 carbs | 54 protein | 75 fat")!!
        assertEquals("banana bread & paneer sandwiches", a.name)
        assertEquals(1660.0, a.calories, 0.0); assertEquals(200.0, a.carbs, 0.0)
        assertEquals(54.0, a.protein, 0.0); assertEquals(75.0, a.fat, 0.0)
        // Same food twice: the later (corrected) line wins.
        val b = pp.parse("LOG: x | 100 cal | 1 carbs | 2 protein | 3 fat\nLOG: x | 200 cal | 4 carbs | 5 protein | 6 fat")!!
        assertEquals(200.0, b.calories, 0.0)
        // Several foods → one review with each as an item, totals summed, a "Total" line ignored.
        val m = pp.parse("Nice!\nLOG: Barebells bar | 200 cal | 18 carbs | 20 protein | 8 fat\n" +
            "LOG: Banana | 105 cal | 27 carbs | 1 protein | 0 fat\nLOG: Total | 305 cal | 45 carbs | 21 protein | 8 fat")!!
        assertEquals(2, m.parts.size)
        assertEquals("Barebells bar + Banana", m.name)
        assertEquals(305.0, m.calories, 0.0); assertEquals(45.0, m.carbs, 0.0)
        assertEquals(21.0, m.protein, 0.0); assertEquals(8.0, m.fat, 0.0)
        // Labelled, any order, colon style.
        val c = pp.parse("Calories: 1160\nProtein: 32g Carbs: 157g Fat: 48g")!!
        assertEquals(1160.0, c.calories, 0.0); assertEquals(157.0, c.carbs, 0.0)
        assertEquals(32.0, c.protein, 0.0); assertEquals(48.0, c.fat, 0.0)
        // Short labels.
        val d = pp.parse("450 kcal, P 30, C 40, F 15")!!
        assertEquals(30.0, d.protein, 0.0); assertEquals(40.0, d.carbs, 0.0); assertEquals(15.0, d.fat, 0.0)
        // Four bare numbers: calories, carbs, protein, fat.
        val e = pp.parse("540 84 9 21")!!
        assertEquals(84.0, e.carbs, 0.0); assertEquals(9.0, e.protein, 0.0)
        assertNull(pp.parse("banana bread"))
    }
}
