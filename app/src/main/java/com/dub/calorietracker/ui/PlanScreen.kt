package com.dub.calorietracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.dub.calorietracker.data.Plan
import com.dub.calorietracker.data.Profile
import com.dub.calorietracker.data.ScheduleType
import com.dub.calorietracker.data.Sex
import com.dub.calorietracker.data.Units
import kotlin.math.roundToInt

private enum class Step { SUMMARY, FORM, RESULTS, SCHEDULE }
private val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

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
    androidx.activity.compose.BackHandler(enabled = step == Step.RESULTS || step == Step.SCHEDULE ||
        (step == Step.FORM && existing != null)) {
        step = when (step) { Step.SCHEDULE -> Step.RESULTS; Step.RESULTS -> Step.FORM; else -> Step.SUMMARY }
    }
    val profile = buildProfile()
    val maintenance = profile?.let { Calculator.maintenance(it) } ?: 0

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            when (step) {
                Step.SUMMARY -> "Your plan"; Step.FORM -> "Calorie calculator"
                Step.RESULTS -> "Choose your goal"; Step.SCHEDULE -> "Choose your eating schedule"
            },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (firstRun) 4.dp else 20.dp),
        )
        if (firstRun) {
            Text(
                when (step) {
                    Step.FORM -> "Welcome! First, let's work out how many calories you need. Step 1 of 3"
                    Step.RESULTS -> "Step 2 of 3"
                    else -> "Step 3 of 3 — after this you can start logging meals."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
        }

        when (step) {
            Step.SUMMARY -> existing?.let { p ->
                SectionCard {
                    Text("${p.goal.label}${if (p.goal.rate.isNotEmpty()) " (${p.goal.rate})" else ""}", fontWeight = FontWeight.SemiBold)
                    Text("Maintenance: ${num(p.maintenanceCalories)} cal/day")
                    Text("Target: ${num(p.dailyTarget)} cal/day average · ${num(p.dailyTarget * 7)} cal/week")
                    Text("Schedule: ${p.schedule.label}")
                }
                ScheduleTable(p.weekTemplate)
                Button(onClick = { step = Step.FORM }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Recalculate / change plan")
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
                    Button(enabled = profile != null, onClick = { step = Step.RESULTS }) { Text("Calculate") }
                }
                if (profile == null) Text("Fill in all fields to calculate.", Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall)
            }

            Step.RESULTS -> {
                Text("Tap the goal you want to follow.", Modifier.padding(horizontal = 20.dp))
                Goal.entries.forEach { g ->
                    val target = Calculator.goalTarget(maintenance, g)
                    GoalCard(g, target, Calculator.percentOf(maintenance, target), selected = goal == g) {
                        goal = g
                        if (g == Goal.MAINTAIN) schedule = ScheduleType.STANDARD
                        step = Step.SCHEDULE
                    }
                }
                profile?.let {
                    val floor = Calculator.calorieFloor(it.sex)
                    Text("BMR: ${num(Calculator.bmr(it))} cal/day. Eating under $floor cal/day is not recommended without a doctor's guidance.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp))
                }
                TextButton(onClick = { step = Step.FORM }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Back") }
            }

            Step.SCHEDULE -> {
                val p = profile ?: run { step = Step.FORM; return@Column }
                val target = Calculator.goalTarget(maintenance, goal)
                Text("${goal.label}: ${num(target)} cal/day average (${num(target * 7)} cal/week)",
                    Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                val options = if (goal == Goal.MAINTAIN) listOf(ScheduleType.STANDARD) else ScheduleType.entries
                options.forEach { type ->
                    val week = Calculator.weekTemplate(maintenance, target, p.sex, type)
                    ScheduleOption(type, week, selected = schedule == type) { schedule = type }
                }
                if (goal == Goal.MAINTAIN) Text("Zigzag schedules only apply to weight-loss goals.",
                    Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = Step.RESULTS }) { Text("Back") }
                    Button(onClick = {
                        vm.savePlan(Plan(
                            profile = p, maintenanceCalories = maintenance, goal = goal, dailyTarget = target,
                            schedule = schedule, weekTemplate = Calculator.weekTemplate(maintenance, target, p.sex, schedule),
                        ))
                        step = Step.SUMMARY
                        onDone()
                    }) { Text("Start this plan") }
                }
            }
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
private fun ScheduleOption(type: ScheduleType, week: List<Int>, selected: Boolean, onClick: () -> Unit) {
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
                        ScheduleType.ZIGZAG_1 -> "Higher calories on the weekend (you can move high days later)."
                        ScheduleType.ZIGZAG_2 -> "Gradually higher then lower calories through the week."
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            week.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(dayNames[i], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${num(c)} Calories", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ScheduleTable(week: List<Int>) {
    SectionCard {
        week.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(dayNames[i], Modifier.weight(1f))
                Text("${num(c)} Calories")
            }
        }
    }
}
