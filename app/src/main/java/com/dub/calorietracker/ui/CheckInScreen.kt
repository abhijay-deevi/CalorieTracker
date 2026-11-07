package com.dub.calorietracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.calc.Calculator
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.ChatExport
import com.dub.calorietracker.data.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Check-in tab: morning weight, gym, cardio and weekly waist for a day, this week's numbers, and copy for ChatGPT. */
@Composable
fun CheckInScreen(vm: AppViewModel, modifier: Modifier, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateText)
    val log = state.dayLogs[dateText]
    val units = ChatExport.units(state)
    val wl = ChatExport.weightLabel(units)
    val waistLabel = ChatExport.waistLabel(units)
    val copy = rememberCopyForChat()
    val week = ChatExport.week(state, date, today)

    fun fmt(v: Double) = String.format(Locale.getDefault(), "%.1f", v).removeSuffix(".0").removeSuffix(",0")
    // Text kept per date so typing "18" on the way to "182.4" isn't reformatted under the cursor.
    var weight by rememberSaveable(dateText) { mutableStateOf(log?.weightKg?.let { fmt(ChatExport.weightIn(it, units)) } ?: "") }
    var waist by rememberSaveable(dateText) {
        mutableStateOf(log?.waistCm?.let { String.format(Locale.US, "%.2f", ChatExport.waistIn(it, units)).trimEnd('0').trimEnd('.') } ?: "")
    }

    val title = when (date) {
        today -> "Today"; today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                "Check-in", "$title · ${date.format(DateTimeFormatter.ofPattern("MMMM d"))}",
                onPrev = { dateText = date.minusDays(1).toString() },
                onNext = if (date.isBefore(today)) { { dateText = date.plusDays(1).toString() } } else null,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SectionCard {
                MeasureField(
                    label = "Morning weight", unit = wl, text = weight, onText = { weight = it },
                    saved = log?.weightKg?.let { ChatExport.weightIn(it, units) },
                    hint = "After waking up and using the bathroom, before eating",
                    decimals = 1,
                ) { v -> vm.updateDayLog(date) { it.copy(weightKg = v?.let { x -> if (units == Units.US) Calculator.lbToKg(x) else x }) } }
                Spacer(Modifier.height(4.dp))
                ToggleRow("Gym session", log?.gym == true) { on -> vm.updateDayLog(date) { it.copy(gym = on) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow("Cardio", log?.cardio == true) { on -> vm.updateDayLog(date) { it.copy(cardio = on) } }
            }
        }

        item {
            SectionCard {
                Text("Waist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Once a week, same day and time. Measure around your belly button, relaxed, after breathing out.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                MeasureField(
                    label = "Waist", unit = waistLabel, text = waist, onText = { waist = it },
                    saved = log?.waistCm?.let { ChatExport.waistIn(it, units) },
                    hint = null,
                    decimals = 2,
                ) { v -> vm.updateDayLog(date) { it.copy(waistCm = v?.let { x -> if (units == Units.US) x * 2.54 else x }) } }
                if (log?.waistCm == null && week.waist != null) Text(
                    "Already measured this week: ${fmt(week.waist)} $waistLabel",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            SectionCard {
                val fmtDay = DateTimeFormatter.ofPattern("MMM d")
                Text("Week of ${week.start.format(fmtDay)} – ${week.start.plusDays(6).format(fmtDay)}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                WeekLine("Morning weight", when {
                    week.weights.isEmpty() -> "—"
                    week.weights.size == 1 -> "${fmt(week.weights[0].second)} $wl"
                    else -> "avg ${fmt(week.weights.map { it.second }.average())} $wl (${week.weights.size} days)"
                })
                WeekLine("Waist", week.waist?.let { "${fmt(it)} $waistLabel" } ?: "—")
                WeekLine("Calories", if (week.loggedDays == 0) "—" else "avg ${num(week.avgCalories)} / day")
                WeekLine("Protein", if (week.loggedDays == 0) "—" else "avg ${num(week.avgProtein)} g / day")
                WeekLine("Gym sessions", week.gym.toString())
                WeekLine("Cardio", week.cardio.toString())
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { copy(ChatExport.text(state, week.start, week.start, today)) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Copy this week for ChatGPT")
                }
                TextButton(onClick = {
                    val start = WeekPlanner.weekStart(date).minusWeeks(3)
                    copy(ChatExport.text(state, start, week.start, today))
                }) { Text("Copy the last 4 weeks") }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * A number field with an enter button: the ✓ icon or the keyboard's enter key saves it and closes the keyboard.
 * Leaving the field also saves, so a typed number is never lost. [saved] is what's stored (user units).
 */
@Composable
private fun MeasureField(
    label: String, unit: String, text: String, onText: (String) -> Unit, saved: Double?, hint: String?, decimals: Int,
    onSave: (Double?) -> Unit,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val unsaved = if (value == null || saved == null) value != saved else kotlin.math.abs(value - saved) > 0.004
    fun save() { if (unsaved) onSave(value) }
    fun submit() { save(); focus.clearFocus() }

    OutlinedTextField(
        value = text,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d{0,3}([.,]\d{0,$decimals})?"""))) onText(v) },
        label = { Text(label) },
        suffix = { Text(unit) },
        trailingIcon = if (focused || unsaved) { {
            IconButton(onClick = ::submit) { Icon(Icons.Filled.Check, "Save $label", tint = MaterialTheme.colorScheme.primary) }
        } } else null,
        supportingText = {
            when {
                unsaved && text.isNotEmpty() -> Text("Tap ✓ or enter to save")
                saved != null -> Text("✓ Saved", color = MaterialTheme.colorScheme.primary)
                hint != null -> Text(hint)
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
            if (focused && !f.isFocused) save()
            focused = f.isFocused
        },
    )
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun WeekLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
