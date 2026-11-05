package com.dub.calorietracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.EntrySource
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.FoodMemory
import com.dub.calorietracker.data.PasteParser
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.dub.calorietracker.R
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(vm: AppViewModel, modifier: Modifier, onSetUpPlan: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    val parsed = PasteParser.parse(text)

    val plan = state.plan
    val targets = WeekPlanner.weekTargets(state, date)
    val dayCal = WeekPlanner.dayTarget(state, date)
    val goal = WeekPlanner.macroTargets(dayCal, state.macros)
    val eaten = WeekPlanner.eaten(state.entries, date)
    val dayEntries = state.entries.filter { it.date == date.toString() }.sortedBy { it.time }
    val zigzag = WeekPlanner.isZigzag(state)
    val isHigh = zigzag && WeekPlanner.isHighDay(targets, WeekPlanner.dayIndex(date))
    val laterHigh = if (zigzag && !date.isBefore(today)) WeekPlanner.bestLaterHighDay(state, date) else null
    val notice = state.shiftNotices[date.toString()]

    val title = when (date) {
        today -> "Today"; today.minusDays(1) -> "Yesterday"; today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                title, date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                onPrev = { vm.selectedDate.value = date.minusDays(1) },
                onNext = { vm.selectedDate.value = date.plusDays(1) },
            )
        }

        if (plan == null) {
            item {
                SectionCard {
                    Text("Set up your plan first", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Enter your age, height, weight and activity to get your daily calorie target.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSetUpPlan) { Text("Set up plan") }
                }
            }
            return@LazyColumn
        }

        // ----- summary: eaten · gauge · target, then macros -----
        item {
            SectionCard(padding = 24.dp) {
                if (zigzag) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Pill(if (isHigh) "High day" else "Regular day", highlight = isHigh)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stat(num(eaten.calories), "Eaten", Modifier.weight(1f))
                    CalorieGauge(eaten.calories, dayCal.toDouble())
                    Stat(num(dayCal), "Target", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MacroColumn("Carbs", eaten.carbs, goal.carbs, Modifier.weight(1f))
                    MacroColumn("Protein", eaten.protein, goal.protein, Modifier.weight(1f))
                    MacroColumn("Fat", eaten.fat, goal.fat, Modifier.weight(1f))
                }
            }
        }

        // ----- zigzag notices (only when there's something to say) -----
        if (notice != null || laterHigh != null) item {
            SectionCard(padding = 8.dp) {
                if (notice != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = Amber)
                    Spacer(Modifier.width(8.dp))
                    Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.dismissNotice(date) }) {
                        Icon(Icons.Filled.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (laterHigh != null) {
                    val laterDate = WeekPlanner.weekStart(date).plusDays(laterHigh.toLong())
                    TextButton(onClick = { vm.useHighDay(date) }) {
                        Text("Use ${laterDate.format(DateTimeFormatter.ofPattern("EEEE"))}'s higher calories " +
                            "(${num(targets[laterHigh])}) today")
                    }
                }
            }
        }

        // ----- add food: paste ChatGPT's numbers -----
        item {
            SectionCard {
                Text("Add food", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Paste the LOG line from ChatGPT, or tap the camera below for photos, typing and barcodes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("LOG: name | 450 cal | 40 carbs | 30 protein | 15 fat") },
                    trailingIcon = if (text.isNotEmpty()) { { IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, "Clear") } } } else null,
                    minLines = 1, maxLines = 4,
                )
                // Live preview of what was understood.
                if (text.isNotBlank()) {
                    Text(
                        parsed?.let { "${if (it.parts.size > 1) "${it.parts.size} foods: " else ""}${it.name} · ${num(it.calories)} cal · C ${num(it.carbs)}g · P ${num(it.protein)}g · F ${num(it.fat)}g" }
                            ?: "Couldn't find calories, carbs, protein and fat in that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (parsed != null) MaterialTheme.colorScheme.primary else OverColor,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    // Typed a food name instead? Offer saved foods.
                    val suggestions = if (parsed == null) FoodMemory.suggestions(text, state.savedFoods) else emptyList()
                    if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { f ->
                            SuggestionChip(onClick = { vm.useSaved(f, text); text = "" },
                                label = { Text("${f.name} · ${num(f.calories)} cal") })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // One tap: paste from clipboard and open the review if it reads.
                    FilledTonalButton(modifier = Modifier.weight(1f).height(48.dp), onClick = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        if (clip.isBlank()) vm.error.value = "The clipboard is empty — copy the LOG line in ChatGPT first."
                        else {
                            text = clip.trim()
                            PasteParser.parse(clip)?.let { vm.startFromPaste(it); text = "" }
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text("Paste")
                    }
                    Button(enabled = parsed != null, modifier = Modifier.weight(1f).height(48.dp), onClick = {
                        parsed?.let { vm.startFromPaste(it); text = "" }
                    }) { Text("Add") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(PasteParser.CHATGPT_INSTRUCTIONS))
                        Toast.makeText(ctx, "Copied — paste it into ChatGPT once (or its Custom Instructions)", Toast.LENGTH_LONG).show()
                    }) { Text("Copy ChatGPT instructions") }
                    TextButton(onClick = { vm.startManual() }) {
                        Text("Manual", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ----- logged foods -----
        item {
            SectionCard(padding = 0.dp) {
                Text("Logged" + if (dayEntries.isNotEmpty()) " · ${dayEntries.size}" else "",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))
                if (dayEntries.isEmpty()) {
                    Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp))
                }
                dayEntries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable { editing = e }.padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name.replaceFirstChar { it.uppercase() } + if (e.servings != 1.0) " ×${trimNum(e.servings)}" else "",
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${num(e.calories)} cal · P ${num(e.protein)}g · C ${num(e.carbs)}g · F ${num(e.fat)}g",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Several foods in one message → list them, scaled by servings.
                            if (e.items.size > 1) e.items.forEach { item ->
                                Text("· ${item.name}" + (if (item.quantity.isNotBlank()) " (${item.quantity})" else "") +
                                    " — ${num(item.calories * e.servings)} cal",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { editing = e }) {
                            Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteEntry(e.id) }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (dayEntries.isNotEmpty()) Spacer(Modifier.height(10.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    editing?.let { e ->
        EditEntryDialog(e,
            onDismiss = { editing = null },
            onSave = { vm.updateEntry(it); editing = null },
            onDelete = { vm.deleteEntry(e.id); editing = null })
    }
}

@Composable
private fun Pill(text: String, highlight: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlight) { Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp), tint = Amber); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun trimNum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d*\.?\d*"""))) onChange(v) },
        label = if (label.isEmpty()) null else { { Text(label) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Review / edit an estimate before logging it. */
@Composable
fun ReviewDialog(
    p: Pending,
    onCancel: () -> Unit,
    onConfirm: (Pending, Double, Boolean) -> Unit,
    busy: Boolean = false,
    /** Non-null for AI estimates: send a correction like "I meant 2 sandwiches". */
    onRefine: ((String) -> Unit)? = null,
) {
    var correction by remember(p) { mutableStateOf("") }
    var name by remember(p) { mutableStateOf(p.name) }
    var cal by remember(p) { mutableStateOf(numText(p.calories)) }
    var pro by remember(p) { mutableStateOf(numText(p.protein)) }
    var carb by remember(p) { mutableStateOf(numText(p.carbs)) }
    var fat by remember(p) { mutableStateOf(numText(p.fat)) }
    var servingsText by remember(p) { mutableStateOf("1") }
    val servings = servingsText.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    var rememberIt by remember(p) { mutableStateOf(true) }
    val c = cal.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(when (p.source) { EntrySource.MANUAL -> "Add food"; EntrySource.PASTE -> "From ChatGPT"; else -> "Review estimate" } +
                if (p.of > 1) " · ${p.step} of ${p.of}" else "")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ----- item breakdown, like a food-log table -----
                if (p.items.size > 1 || (p.items.size == 1 && p.source != EntrySource.BARCODE)) {
                    p.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name.replaceFirstChar { it.uppercase() } +
                                    if (item.quantity.isNotBlank()) " (${item.quantity})" else "",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("P ${num(item.protein)}g · C ${num(item.carbs)}g · F ${num(item.fat)}g",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${num(item.calories)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (p.notes.isNotBlank()) {
                    Text(p.notes + if (p.confidence.isNotBlank()) " (confidence: ${p.confidence})" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                // ----- correct it like a chat reply -----
                if (onRefine != null) {
                    OutlinedTextField(
                        value = correction,
                        onValueChange = { correction = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !busy,
                        placeholder = { Text("Something off? e.g. \"I meant 2 sandwiches\"") },
                        trailingIcon = {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(enabled = correction.isNotBlank(), onClick = { onRefine(correction) }) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Fix estimate")
                            }
                        },
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Calories", cal, { cal = it }, Modifier.weight(1f))
                    NumberField("Protein g", pro, { pro = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Carbs g", carb, { carb = it }, Modifier.weight(1f))
                    NumberField("Fat g", fat, { fat = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Servings", Modifier.weight(1f))
                    TextButton(onClick = { servingsText = trimNum((servings - 0.5).coerceAtLeast(0.5)) }) { Text("−") }
                    NumberField("", servingsText, { servingsText = it }, Modifier.width(80.dp))
                    TextButton(onClick = { servingsText = trimNum(servings + 0.5) }) { Text("+") }
                }
                if (p.servingLabel != null) {
                    Text(p.servingLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Total: ${num(c * servings)} cal", fontWeight = FontWeight.SemiBold)
                if (p.savedFoodId == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberIt, { rememberIt = it })
                        Text("Remember \"${name.ifBlank { "this" }}\" so I can log it again instantly",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy && name.isNotBlank() && servings > 0, onClick = {
                onConfirm(p.copy(name = name.trim(), calories = c, protein = pro.toDoubleOrNull() ?: 0.0,
                    carbs = carb.toDoubleOrNull() ?: 0.0, fat = fat.toDoubleOrNull() ?: 0.0), servings, rememberIt)
            }) { Text("Log it") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(if (p.of > 1 && p.step < p.of) "Skip" else "Cancel") } },
    )
}

private fun Double.roundTo1() = kotlin.math.round(this * 10) / 10

/** Blank instead of "0" so typing doesn't produce "0450". */
private fun numText(v: Double) = if (v == 0.0) "" else trimNum(v.roundTo1())

@Composable
fun EditEntryDialog(e: FoodEntry, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(e.name) }
    var cal by remember { mutableStateOf(trimNum(e.calories.roundTo1())) }
    var pro by remember { mutableStateOf(trimNum(e.protein.roundTo1())) }
    var carb by remember { mutableStateOf(trimNum(e.carbs.roundTo1())) }
    var fat by remember { mutableStateOf(trimNum(e.fat.roundTo1())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit entry") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Calories", cal, { cal = it }, Modifier.weight(1f))
                    NumberField("Protein g", pro, { pro = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Carbs g", carb, { carb = it }, Modifier.weight(1f))
                    NumberField("Fat g", fat, { fat = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(e.copy(name = name.trim().ifEmpty { e.name }, calories = cal.toDoubleOrNull() ?: 0.0,
                    protein = pro.toDoubleOrNull() ?: 0.0, carbs = carb.toDoubleOrNull() ?: 0.0,
                    fat = fat.toDoubleOrNull() ?: 0.0))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = OverColor) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
