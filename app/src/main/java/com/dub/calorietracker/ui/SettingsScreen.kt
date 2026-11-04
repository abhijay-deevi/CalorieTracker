package com.dub.calorietracker.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.data.AiProvider
import com.dub.calorietracker.data.MacroSplit
import com.dub.calorietracker.data.SavedFood

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ai = state.ai
    var showKey by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<SavedFood?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))

        // ----- AI provider -----
        SectionCard {
            Text("AI for food estimates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("A ChatGPT Plus subscription can't be used by other apps — API access is billed separately. " +
                "Gemini's free tier costs nothing; when its limit is hit the app tells you how long until you can use it again.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            AiProvider.entries.forEach { p ->
                Row(Modifier.fillMaxWidth().selectable(ai.provider == p) { vm.setProvider(p) },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(ai.provider == p, { vm.setProvider(p) })
                    Text(p.label)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Keyed on provider so the field resets when switching.
            val provider = ai.provider
            val clipboard = LocalClipboardManager.current
            var keyText by remember(provider) { mutableStateOf(ai.key(provider)) }
            var modelText by remember(provider) { mutableStateOf(ai.models[provider].orEmpty()) }
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it; vm.setApiKey(provider, it) },
                label = { Text("${provider.label.substringBefore(" (")} API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        // Paste button: Ctrl+V / long-press paste doesn't always work (e.g. on the emulator).
                        IconButton(onClick = {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                keyText = it; vm.setApiKey(provider, it)
                            }
                        }) { Icon(Icons.Filled.ContentPaste, "Paste key") }
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show key")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(provider.keyHint, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = modelText,
                onValueChange = { modelText = it; vm.setModel(provider, it) },
                label = { Text("Model (optional)") },
                placeholder = { Text(provider.defaultModel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Leave blank for ${provider.defaultModel}." +
                if (provider == AiProvider.CLAUDE) " Use claude-haiku-5-5 for a much cheaper option." else "",
                style = MaterialTheme.typography.bodySmall)
            if (ai.cooldownUntil > System.currentTimeMillis()) {
                TextButton(onClick = vm::clearCooldown) { Text("Clear AI cooldown (try again now)") }
            }
        }

        // ----- macros -----
        SectionCard {
            Text("Macro targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Percent of each day's calories. Must add up to 100.", style = MaterialTheme.typography.bodySmall)
            var p by remember { mutableStateOf(state.macros.proteinPct.toString()) }
            var c by remember { mutableStateOf(state.macros.carbsPct.toString()) }
            var f by remember { mutableStateOf(state.macros.fatPct.toString()) }
            val total = (p.toIntOrNull() ?: 0) + (c.toIntOrNull() ?: 0) + (f.toIntOrNull() ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PctField("Protein %", p, { p = it }, Modifier.weight(1f))
                PctField("Carbs %", c, { c = it }, Modifier.weight(1f))
                PctField("Fat %", f, { f = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total: $total%", color = if (total == 100) MaterialTheme.colorScheme.onSurface else OverColor)
                Spacer(Modifier.weight(1f))
                Button(enabled = total == 100, onClick = {
                    vm.setMacros(MacroSplit(p.toInt(), c.toInt(), f.toInt()))
                }) { Text("Save") }
            }
        }

        // ----- zigzag -----
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-detect high-calorie days", fontWeight = FontWeight.SemiBold)
                    Text("Zigzag plans: if you go over a regular day's target and a high day is still coming this week, " +
                        "today becomes the high day and the later one becomes regular. Weekly total stays the same.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.autoDetectHighDays, vm::setAutoDetect)
            }
        }

        // ----- saved foods -----
        SectionCard {
            Text("Saved foods (${state.savedFoods.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Type one of these names on the Today tab to log it instantly without the AI.",
                style = MaterialTheme.typography.bodySmall)
            if (state.savedFoods.isEmpty()) Text("Nothing saved yet.", Modifier.padding(top = 6.dp))
            state.savedFoods.sortedByDescending { it.timesUsed }.forEach { food ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, fontWeight = FontWeight.Medium)
                        Text("${num(food.calories)} cal · P ${num(food.protein)} · C ${num(food.carbs)} · F ${num(food.fat)}" +
                            " · used ${food.timesUsed}×", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editingFood = food }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { vm.deleteSavedFood(food.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }

    editingFood?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        var cal by remember(f) { mutableStateOf(trimNum(f.calories)) }
        var pro by remember(f) { mutableStateOf(trimNum(f.protein)) }
        var carb by remember(f) { mutableStateOf(trimNum(f.carbs)) }
        var fat by remember(f) { mutableStateOf(trimNum(f.fat)) }
        AlertDialog(
            onDismissRequest = { editingFood = null },
            title = { Text("Edit saved food") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PctField("Calories", cal, { cal = it }, Modifier.weight(1f))
                        PctField("Protein g", pro, { pro = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PctField("Carbs g", carb, { carb = it }, Modifier.weight(1f))
                        PctField("Fat g", fat, { fat = it }, Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateSavedFood(f.copy(name = name.trim().ifEmpty { f.name }, calories = cal.toDoubleOrNull() ?: f.calories,
                        protein = pro.toDoubleOrNull() ?: f.protein, carbs = carb.toDoubleOrNull() ?: f.carbs, fat = fat.toDoubleOrNull() ?: f.fat))
                    editingFood = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingFood = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PctField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d*\.?\d*"""))) onChange(v) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
