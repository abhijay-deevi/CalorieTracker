package com.dub.calorietracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.dub.calorietracker.calc.WeekPlanner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.calc.Calculator
import com.dub.calorietracker.data.ActivityLevel
import com.dub.calorietracker.data.Goal
import com.dub.calorietracker.data.MacroStyle
import com.dub.calorietracker.data.Plan
import com.dub.calorietracker.data.Profile
import com.dub.calorietracker.data.ScheduleType
import com.dub.calorietracker.data.Sex
import com.dub.calorietracker.data.Units
import kotlin.math.roundToInt

private enum class Step { SUMMARY, FORM, RESULTS, MACROS, SCHEDULE, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: AppViewModel, modifier: Modifier, firstRun: Boolean = false, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val existing = state.plan
    val prof = existing?.profile

    var step by rememberSaveable { mutableStateOf(if (existing != null) Step.SUMMARY else Step.FORM) }
    var units by rememberSaveable { mutableStateOf(prof?.units ?: Units.US) }
    var age by rememberSaveable { mutableStateOf(prof?.age?.toString() ?: "") }
    var sex by rememberSaveable { mutableStateOf(prof?.sex ?: Sex.MALE) }
    var feet by rememberSaveable { mutableStateOf(prof?.let { Calculator.cmToFtIn(it.heightCm).first.toString() } ?: "") }
    var inches by rememberSaveable { mutableStateOf(prof?.let { Calculator.cmToFtIn(it.heightCm).second.toString() } ?: "") }
    var cm by rememberSaveable { mutableStateOf(prof?.heightCm?.roundToInt()?.toString() ?: "") }
    var pounds by rememberSaveable { mutableStateOf(prof?.let { Calculator.kgToLb(it.weightKg).roundToInt().toString() } ?: "") }
    var kg by rememberSaveable { mutableStateOf(prof?.weightKg?.let { "%.1f".format(it) } ?: "") }
    var activity by rememberSaveable { mutableStateOf(prof?.activity ?: ActivityLevel.MODERATE) }
    var goal by rememberSaveable { mutableStateOf(existing?.goal ?: Goal.LOSS) }
    var schedule by rememberSaveable { mutableStateOf(existing?.schedule ?: ScheduleType.STANDARD) }
    var style by rememberSaveable { mutableStateOf(existing?.macroStyle ?: MacroStyle.BALANCED) }
    // Custom plan: numbers typed in by hand.
    var customMode by rememberSaveable { mutableStateOf(false) }
    var cCal by rememberSaveable { mutableStateOf(existing?.dailyTarget?.toString() ?: "") }
    var cProt by rememberSaveable { mutableStateOf(existing?.proteinG?.takeIf { it > 0 }?.toString() ?: "") }
    var cFat by rememberSaveable { mutableStateOf(existing?.fatG?.takeIf { it > 0 }?.toString() ?: "") }
    var cCarb by rememberSaveable { mutableStateOf("") }

    fun buildProfile(): Profile? {
        val a = age.toIntOrNull()?.takeIf { it in 15..100 } ?: return null
        val h = if (units == Units.US) {
            val f = feet.toIntOrNull() ?: return null
            Calculator.ftInToCm(f, inches.toDoubleOrNull() ?: 0.0)
        } else cm.toDoubleOrNull() ?: return null
        val w = if (units == Units.US) Calculator.lbToKg(pounds.toDoubleOrNull() ?: return null)
        else kg.toDoubleOrNull() ?: return null
        if (h < 90 || h > 250 || w < 30 || w > 300) return null
        return Profile(a, sex, h, w, activity, units)
    }
    // System back moves to the previous setup step instead of leaving the app.
    androidx.activity.compose.BackHandler(enabled = step != Step.SUMMARY && !(step == Step.FORM && existing == null)) {
        step = when (step) {
            Step.SCHEDULE -> if (customMode) Step.CUSTOM else Step.MACROS
            Step.MACROS -> Step.RESULTS
            Step.RESULTS -> Step.FORM
            Step.CUSTOM -> if (existing != null) Step.SUMMARY else Step.FORM
            else -> Step.SUMMARY
        }
    }
    val profile = buildProfile()
    val maintenance = profile?.let { Calculator.maintenance(it) } ?: 0
    val custom = parseCustom(cCal, cProt, cFat, cCarb)
    // What the schedule step builds on: calculated (profile + goal + macro style) or the custom numbers.
    val target = if (customMode) custom?.calories ?: 0 else Calculator.goalTarget(maintenance, goal)
    val proteinG = if (customMode) custom?.proteinG ?: 0 else profile?.let { Calculator.proteinGrams(it, goal) } ?: 0
    val fatG = if (customMode) custom?.fatG ?: 0 else Calculator.fatGrams(target, style, profile)
    val highDay = if (customMode) Calculator.customHighDay(target) else maintenance
    val floorSex = (if (customMode) prof else profile)?.sex ?: Sex.FEMALE

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            when (step) {
                Step.SUMMARY -> "Your plan"; Step.FORM -> "Calorie calculator"
                Step.RESULTS -> "Choose your goal"; Step.MACROS -> "Protein, carbs & fat"
                Step.SCHEDULE -> "Choose your eating schedule"; Step.CUSTOM -> "Custom plan"
            },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (firstRun) 4.dp else 20.dp),
        )
        if (firstRun) {
            Text(
                when (step) {
                    Step.FORM -> "Welcome! First, let's work out how many calories you need. Step 1 of 3"
                    Step.RESULTS, Step.MACROS, Step.CUSTOM -> "Step 2 of 3"
                    else -> "Step 3 of 3 — after this you can start logging meals."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = if (step == Step.FORM) 0.dp else 16.dp),
            )
            if (step == Step.FORM) {
                val restore = rememberRestoreLauncher(vm)
                androidx.compose.material3.TextButton(
                    onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                ) { Text("Reinstalled? Restore from a backup") }
                TextButton(onClick = { customMode = true; step = Step.CUSTOM }, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                    Text("Already know your numbers? Enter a custom plan")
                }
            }
        }

        when (step) {
            Step.SUMMARY -> existing?.let { p ->
                SectionCard {
                    if (p.custom) Text("Custom plan", fontWeight = FontWeight.SemiBold)
                    else Text(p.goal.label + if (p.goal.rate.isNotEmpty() && p.goal != Goal.RECOMP) " (${p.goal.rate})" else "", fontWeight = FontWeight.SemiBold)
                    if (!p.custom) Text("Maintenance: ${num(p.maintenanceCalories)} cal/day")
                    Text("Target: ${num(p.dailyTarget)} cal/day average · ${num(p.dailyTarget * 7)} cal/week")
                    Text("Schedule: ${p.schedule.label}")
                }
                if (p.proteinG > 0) MacroSummary(p.dailyTarget, p.proteinG, p.fatG, p.weekTemplate)
                else SectionCard {
                    Text("This plan was made before protein and fat targets were added. Tap Recalculate (or Custom plan) " +
                        "to get them; until then macros use the old percentages.", style = MaterialTheme.typography.bodySmall)
                }
                ScheduleTable(p.weekTemplate, p.proteinG, p.fatG)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { customMode = false; step = Step.FORM }) { Text("Recalculate") }
                    OutlinedButton(onClick = {
                        customMode = true
                        cCal = p.dailyTarget.toString()
                        cProt = p.proteinG.takeIf { it > 0 }?.toString() ?: ""
                        cFat = p.fatG.takeIf { it > 0 }?.toString() ?: ""
                        cCarb = ""
                        step = Step.CUSTOM
                    }) { Text("Custom plan") }
                }
            }

            Step.FORM -> {
                SectionCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Units.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = units == u, onClick = { units = u },
                                shape = SegmentedButtonDefaults.itemShape(i, Units.entries.size)) {
                                Text(if (u == Units.US) "US units" else "Metric")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    NumField("Age", age, { age = it }, Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gender", Modifier.padding(end = 8.dp))
                        Sex.entries.forEach { s ->
                            Row(Modifier.selectable(sex == s) { sex = s }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(sex == s, { sex = s })
                                Text(if (s == Sex.MALE) "Male" else "Female", Modifier.padding(end = 12.dp))
                            }
                        }
                    }
                    if (units == Units.US) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Height (ft)", feet, { feet = it }, Modifier.weight(1f))
                            NumField("(in)", inches, { inches = it }, Modifier.weight(1f))
                        }
                        NumField("Weight (lb)", pounds, { pounds = it }, Modifier.fillMaxWidth())
                    } else {
                        NumField("Height (cm)", cm, { cm = it }, Modifier.fillMaxWidth())
                        NumField("Weight (kg)", kg, { kg = it }, Modifier.fillMaxWidth())
                    }
                }

                SectionCard {
                    Text("Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ActivityLevel.entries.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().selectable(activity == a) { activity = a }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(activity == a, { activity = a })
                            Column {
                                Text(a.label, fontWeight = FontWeight.Medium)
                                Text(a.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("What counts as exercise?", style = MaterialTheme.typography.labelLarge)
                    Text("• Exercise: 15-30 minutes of elevated heart rate activity.", style = MaterialTheme.typography.bodySmall)
                    Text("• Intense exercise: 45-120 minutes of elevated heart rate activity.", style = MaterialTheme.typography.bodySmall)
                    Text("• Very intense exercise: 2+ hours of elevated heart rate activity.", style = MaterialTheme.typography.bodySmall)
                }

                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existing != null) OutlinedButton(onClick = { step = Step.SUMMARY }) { Text("Cancel") }
                    Button(enabled = profile != null, onClick = { customMode = false; step = Step.RESULTS }) { Text("Calculate") }
                }
                if (profile == null) Text("Fill in all fields to calculate.", Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall)
            }

            Step.RESULTS -> {
                Text("Tap the goal you want to follow.", Modifier.padding(horizontal = 20.dp))
                Goal.entries.forEach { g ->
                    val t = Calculator.goalTarget(maintenance, g)
                    GoalCard(g, t, Calculator.percentOf(maintenance, t), selected = goal == g) {
                        goal = g
                        if (g == Goal.MAINTAIN) schedule = ScheduleType.STANDARD
                        step = Step.MACROS
                    }
                }
                Text("Recomp vs. weight loss: weight loss eats further below maintenance to drop weight faster. " +
                    "Recomp eats only ~5% below maintenance with more protein, so you lose fat slowly while building " +
                    "muscle. Your weight may barely move while your shape changes. It works best if you lift weights " +
                    "3+ times a week, and best of all if you're newer to lifting or have more fat to lose.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                profile?.let {
                    val floor = Calculator.calorieFloor(it.sex)
                    Text("BMR: ${num(Calculator.bmr(it))} cal/day. Eating under $floor cal/day is not recommended without a doctor's guidance.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp))
                }
                TextButton(onClick = { step = Step.FORM }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Back") }
            }

            Step.MACROS -> {
                val p = profile ?: run { step = Step.FORM; return@Column }
                Text("${goal.label}: ${num(target)} cal/day average", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                Text("Protein is set from your body weight (${"%.1f".format(goal.proteinPerKg)} g per kg" +
                    (if (Calculator.proteinWeightKg(p) < p.weightKg) ", using a capped weight" else "") +
                    ") and stays the same every day. Pick how the rest is split between carbs and fat.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                MacroStyle.entries.forEach { m ->
                    val f = Calculator.fatGrams(target, m, p)
                    MacroStyleCard(m, target, proteinG, f, selected = style == m) { style = m }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = Step.RESULTS }) { Text("Back") }
                    Button(onClick = { step = Step.SCHEDULE }) { Text("Next") }
                }
            }

            Step.CUSTOM -> {
                SectionCard {
                    Text("Type your own daily goals. Leave carbs (or fat) blank and the app fills in the rest of the calories.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    NumField("Calories per day (average)", cCal, { cCal = it }, Modifier.fillMaxWidth())
                    NumField("Protein (g)", cProt, { cProt = it }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Carbs (g)", cCarb, { cCarb = it }, Modifier.weight(1f))
                        NumField("Fat (g)", cFat, { cFat = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (custom != null) {
                        MacroRows(custom.calories, custom.proteinG, Calculator.carbGrams(custom.calories, custom.proteinG, custom.fatG), custom.fatG)
                        custom.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OverColor, modifier = Modifier.padding(top = 4.dp)) }
                        custom.macroCalories?.let { mc ->
                            TextButton(onClick = { cCal = mc.toString() }) { Text("Use ${num(mc)} cal instead") }
                        }
                    } else Text("Enter calories and protein to see the breakdown.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = if (existing != null) Step.SUMMARY else Step.FORM }) { Text("Cancel") }
                    Button(enabled = custom != null && !custom.blocking, onClick = { step = Step.SCHEDULE }) { Text("Next") }
                }
            }

            Step.SCHEDULE -> {
                val p = profile
                if (!customMode && p == null) { step = Step.FORM; return@Column }
                if (customMode && custom == null) { step = Step.CUSTOM; return@Column }
                Text("${if (customMode) "Custom plan" else goal.label}: ${num(target)} cal/day average (${num(target * 7)} cal/week)",
                    Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                Text("Protein ($proteinG g) and fat ($fatG g) stay the same every day. On high days the extra calories " +
                    "come from carbs, which refill your muscles' energy stores for training.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                val options = if (!customMode && goal == Goal.MAINTAIN) listOf(ScheduleType.STANDARD) else ScheduleType.entries
                options.forEach { type ->
                    val week = Calculator.weekTemplate(highDay, target, floorSex, type)
                    ScheduleOption(type, week, proteinG, fatG, customMode, selected = schedule == type) { schedule = type }
                }
                if (!customMode && goal == Goal.MAINTAIN) Text("Zigzag schedules only apply to fat-loss and recomp goals.",
                    Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = if (customMode) Step.CUSTOM else Step.MACROS }) { Text("Back") }
                    Button(onClick = {
                        val sched = if (options.contains(schedule)) schedule else ScheduleType.STANDARD
                        vm.savePlan(Plan(
                            profile = if (customMode) prof else p,
                            maintenanceCalories = if (customMode) existing?.maintenanceCalories ?: 0 else maintenance,
                            goal = if (customMode) existing?.goal ?: Goal.MAINTAIN else goal,
                            dailyTarget = target, schedule = sched,
                            weekTemplate = Calculator.weekTemplate(highDay, target, floorSex, sched),
                            proteinG = proteinG, fatG = fatG, macroStyle = style, custom = customMode,
                        ))
                        step = Step.SUMMARY
                        onDone()
                    }) { Text("Start this plan") }
                }
            }
        }
    }
}

/** Custom plan fields → numbers (null until calories and protein are filled in). */
private data class CustomNumbers(
    val calories: Int, val proteinG: Int, val fatG: Int,
    val warning: String? = null,
    /** Set when typed macros add up to a different calorie number (offer to use it). */
    val macroCalories: Int? = null,
    /** True when the numbers can't work (protein + fat alone exceed the calories). */
    val blocking: Boolean = false,
)

private fun parseCustom(cal: String, prot: String, fat: String, carb: String): CustomNumbers? {
    val c = cal.toDoubleOrNull()?.toInt()?.takeIf { it >= 800 } ?: return null
    val p = prot.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: return null
    val f = fat.toDoubleOrNull()?.toInt()
    val cb = carb.toDoubleOrNull()?.toInt()
    val fatG = when {
        f != null -> f
        cb != null -> maxOf(0, ((c - p * 4 - cb * 4) / 9.0).toInt())
        else -> Calculator.fatGrams(c, MacroStyle.BALANCED, null)
    }
    if (c - p * 4 - fatG * 9 < 0) {
        return CustomNumbers(c, p, fatG, "Protein and fat alone are more than ${num(c)} calories. Lower them or raise calories.", blocking = true)
    }
    if (f != null && cb != null) {
        val macroCal = p * 4 + cb * 4 + f * 9
        if (kotlin.math.abs(macroCal - c) > 30) {
            return CustomNumbers(c, p, fatG,
                "Those macros add up to ${num(macroCal)} cal, not ${num(c)}. Carbs will be set to " +
                    "${Calculator.carbGrams(c, p, fatG)} g so they match your calories.", macroCalories = macroCal)
        }
    }
    return CustomNumbers(c, p, fatG)
}

/** Grams and % of calories for each macro. */
@Composable
private fun MacroRows(calories: Int, proteinG: Int, carbsG: Int, fatG: Int) {
    listOf(Triple("Protein", proteinG, 4), Triple("Carbs", carbsG, 4), Triple("Fat", fatG, 9)).forEach { (label, g, k) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, Modifier.weight(1f))
            Text("${num(g)} g", fontWeight = FontWeight.SemiBold)
            Text("${Calculator.pct(g, k, calories)}%", Modifier.width(52.dp), textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MacroStyleCard(style: MacroStyle, calories: Int, proteinG: Int, fatG: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).selectable(selected, onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected, onClick)
                Column {
                    Text(style.label, fontWeight = FontWeight.SemiBold)
                    Text(style.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            MacroRows(calories, proteinG, Calculator.carbGrams(calories, proteinG, fatG), fatG)
        }
    }
}

/** Plan summary: what stays fixed and how carbs move with the day's calories. */
@Composable
private fun MacroSummary(avg: Int, proteinG: Int, fatG: Int, week: List<Int>) {
    SectionCard {
        Text("Daily macros (average day)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        MacroRows(avg, proteinG, Calculator.carbGrams(avg, proteinG, fatG), fatG)
        if (week.distinct().size > 1) {
            val lo = Calculator.carbGrams(week.min(), proteinG, fatG)
            val hi = Calculator.carbGrams(week.max(), proteinG, fatG)
            Text("Protein and fat stay the same every day. Carbs go from ${num(lo)} g on your lowest day to ${num(hi)} g on your highest.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d*\.?\d*"""))) onChange(v) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun GoalCard(goal: Goal, target: Int, percent: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.label, style = MaterialTheme.typography.titleMedium)
                if (goal.rate.isNotEmpty()) Text(goal.rate, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(num(target), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(" $percent%", style = MaterialTheme.typography.bodySmall)
                }
                Text("Calories/day", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScheduleOption(type: ScheduleType, week: List<Int>, proteinG: Int, fatG: Int, custom: Boolean, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).selectable(selected, onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected, onClick)
                Column {
                    Text(when (type) {
                        ScheduleType.STANDARD -> "Standard eating"
                        ScheduleType.ZIGZAG_1 -> "Zigzag 1 — 2 high days, 5 regular days"
                        ScheduleType.ZIGZAG_2 -> "Zigzag 2 — calories change every day"
                    }, fontWeight = FontWeight.SemiBold)
                    Text(when (type) {
                        ScheduleType.STANDARD -> "Same calories every day."
                        ScheduleType.ZIGZAG_1 -> if (custom) "Weekend days ~20% higher, weekdays a bit lower, same weekly total (you can move high days later)."
                            else "Higher calories on the weekend (you can move high days later)."
                        ScheduleType.ZIGZAG_2 -> "Gradually higher then lower calories through the week."
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            week.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(WeekPlanner.dayNames[i], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${num(c)} cal", style = MaterialTheme.typography.bodyMedium)
                    if (proteinG > 0) Text(" · ${num(Calculator.carbGrams(c, proteinG, fatG))} g carbs",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScheduleTable(week: List<Int>, proteinG: Int, fatG: Int) {
    SectionCard {
        week.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(WeekPlanner.dayNames[i], Modifier.weight(1f))
                Text("${num(c)} cal")
                if (proteinG > 0) Text(" · ${num(Calculator.carbGrams(c, proteinG, fatG))} g carbs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

